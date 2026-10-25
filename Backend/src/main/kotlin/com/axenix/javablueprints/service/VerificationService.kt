package com.axenix.javablueprints.service

import com.axenix.javablueprints.model.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.tools.*
import kotlin.math.roundToLong

class VerificationService {

    private val javaCompiler: JavaCompiler = ToolProvider.getSystemJavaCompiler()
    private val verificationPool = java.util.concurrent.Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "verification-worker").apply { isDaemon = true }
    }

    data class CompilationDiagnostic(
        val success: Boolean,
        val errors: String = ""
    )

    private data class ExecutionResult(
        val compilationSuccess: Boolean,
        val output: String,
        val errorOutput: String,
        val exitCode: Int,
        val executionTime: Long
    )

    fun resolveSourcePath(rawName: String, content: String): String {
        val packageName = Regex("""\bpackage\s+([A-Za-z_][\w.]*)\s*;""")
            .find(content)?.groupValues?.get(1)
        val className = Regex("""\b(public\s+)?(final\s+|abstract\s+)?(class|interface|enum|record)\s+([A-Za-z_]\w*)""")
            .find(content)?.groupValues?.get(4)
            ?: rawName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')

        val fileName = "$className.java"
        return if (packageName.isNullOrBlank()) {
            fileName
        } else {
            val packagePath = packageName.replace('.', '/')
            "$packagePath/$fileName"
        }
    }

    fun detectMainClass(files: Map<String, String>, mainFile: String? = null): String {
        val executableEntry = files.entries.firstOrNull { (_, source) ->
            Regex("""\bstatic\s+void\s+main\s*\(\s*String\s*(\[\s*\]|\.\.\.)""").containsMatchIn(source)
        } ?: if (!mainFile.isNullOrBlank() && files.containsKey(mainFile)) {
            files.entries.first { it.key == mainFile }
        } else {
            files.entries.firstOrNull()
        }

        if (executableEntry == null) return ""

        val content = executableEntry.value
        val packageName = Regex("""\bpackage\s+([A-Za-z_][\w.]*)\s*;""")
            .find(content)?.groupValues?.get(1)
        val className = Regex("""\b(public\s+)?(final\s+|abstract\s+)?(class|interface|enum|record)\s+([A-Za-z_]\w*)""")
            .find(content)?.groupValues?.get(4)
            ?: executableEntry.key.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')

        return if (packageName.isNullOrBlank()) className else "$packageName.$className"
    }

    fun compileJavaCode(files: Map<String, String>, targetDir: File): CompilationDiagnostic {
        try {
            val javaFiles = mutableListOf<File>()
            files.forEach { (name, content) ->
                val relPath = resolveSourcePath(name, content)
                val file = File(targetDir, relPath)
                file.parentFile?.mkdirs()
                file.writeText(content, StandardCharsets.UTF_8)
                javaFiles.add(file)
            }

            val diagnostics = DiagnosticCollector<JavaFileObject>()
            val fileManager = javaCompiler.getStandardFileManager(diagnostics, Locale.getDefault(), StandardCharsets.UTF_8)
            val compilationUnits = fileManager.getJavaFileObjectsFromFiles(javaFiles)

            val options = listOf("-encoding", "UTF-8", "-d", targetDir.absolutePath)
            val task = javaCompiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits)
            val success = task.call() ?: false
            fileManager.close()

            val errors = if (!success) {
                diagnostics.diagnostics.joinToString("\n") { d ->
                    "Строка ${d.lineNumber}: ${d.getMessage(Locale.getDefault())}"
                }
            } else ""

            return CompilationDiagnostic(success, errors)
        } catch (e: Exception) {
            return CompilationDiagnostic(false, e.message ?: "Unknown compilation error")
        }
    }

    private fun executeJavaCode(
        files: Map<String, String>,
        mainClass: String,
        input: String,
        timeoutMs: Long
    ): ExecutionResult {
        val tempDir = createTempDir("java_exec_")
        try {
            val compileResult = compileJavaCode(files, tempDir)
            if (!compileResult.success) {
                return ExecutionResult(false, "", compileResult.errors, -1, 0)
            }

            val startTime = System.nanoTime()

            val processBuilder = ProcessBuilder(
                "java",
                "-Xms16m",
                "-Xmx64m",
                "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
                "-cp", tempDir.absolutePath,
                mainClass
            )

            processBuilder.redirectErrorStream(false)
            val process = processBuilder.start()

            val writer = OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)
            try {
                writer.write(input)
                writer.flush()
            } catch (_: IOException) {
                // A console program may exit before consuming all stdin. The process result remains authoritative.
            } finally {
                try { writer.close() } catch (_: IOException) { }
            }

            val outputFuture = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
            }

            val errorFuture = CompletableFuture.supplyAsync {
                process.errorStream.bufferedReader(StandardCharsets.UTF_8).readText()
            }

            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            val endTime = System.nanoTime()
            val executionTime = (endTime - startTime) / 1_000_000

            return if (completed) {
                val output = try { outputFuture.get(1, TimeUnit.SECONDS) } catch (e: Exception) { "" }
                val errorOutput = try { errorFuture.get(1, TimeUnit.SECONDS) } catch (e: Exception) { "" }

                ExecutionResult(
                    compilationSuccess = true,
                    output = output,
                    errorOutput = errorOutput,
                    exitCode = process.exitValue(),
                    executionTime = executionTime
                )
            } else {
                process.destroyForcibly()
                ExecutionResult(true, "", "Таймаут выполнения (${timeoutMs} мс)", -2, timeoutMs)
            }
        } catch (e: Exception) {
            return ExecutionResult(false, "", e.message ?: "Ошибка выполнения", -1, 0)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun runProject(files: Map<String, String>, mainFile: String, input: String): RunResult {
        val mainClass = detectMainClass(files, mainFile)
        val local = executeJavaCode(files, mainClass, input, 7000)
        return RunResult(
            compilationSuccess = local.compilationSuccess,
            output = local.output,
            errors = local.errorOutput,
            exitCode = local.exitCode,
            executionTime = local.executionTime,
            mainClass = mainClass
        )
    }

    fun runVerification(
        originalFiles: Map<String, String>,
        optimizedFiles: Map<String, String>,
        testInputs: List<String>
    ): VerificationResult {
        if (originalFiles.isEmpty() || optimizedFiles.isEmpty()) {
            return VerificationResult(
                compilationSuccess = false,
                testResults = emptyList(),
                originalExecutionTime = 0,
                optimizedExecutionTime = 0,
                speedupFactor = 0.0,
                behaviorPreserved = false,
                originalOutput = "",
                optimizedOutput = "",
                originalErrors = "Файлы для верификации не предоставлены",
                optimizedErrors = ""
            )
        }

        val originalCompileDir = createTempDir("java_orig_test_")
        val originalCompile = try {
            compileJavaCode(originalFiles, originalCompileDir)
        } finally {
            originalCompileDir.deleteRecursively()
        }
        if (!originalCompile.success) {
            return VerificationResult(
                compilationSuccess = false,
                testResults = emptyList(),
                originalExecutionTime = 0,
                optimizedExecutionTime = 0,
                speedupFactor = 0.0,
                behaviorPreserved = false,
                originalOutput = "",
                optimizedOutput = "",
                originalErrors = "Ошибка компиляции оригинала:\n${originalCompile.errors}",
                optimizedErrors = ""
            )
        }

        val optimizedCompileDir = createTempDir("java_opt_test_")
        val optimizedCompile = try {
            compileJavaCode(optimizedFiles, optimizedCompileDir)
        } finally {
            optimizedCompileDir.deleteRecursively()
        }
        if (!optimizedCompile.success) {
            return VerificationResult(
                compilationSuccess = false,
                testResults = emptyList(),
                originalExecutionTime = 0,
                optimizedExecutionTime = 0,
                speedupFactor = 0.0,
                behaviorPreserved = false,
                originalOutput = "",
                optimizedOutput = "",
                originalErrors = "",
                optimizedErrors = "Ошибка компиляции оптимизированного кода:\n${optimizedCompile.errors}"
            )
        }

        val originalMainClass = detectMainClass(originalFiles)
        val optimizedMainClass = detectMainClass(optimizedFiles)
        if (originalMainClass.isBlank() || optimizedMainClass.isBlank()) {
            return VerificationResult(
                compilationSuccess = true,
                testResults = emptyList(),
                originalExecutionTime = 0,
                optimizedExecutionTime = 0,
                speedupFactor = 1.0,
                behaviorPreserved = false,
                originalOutput = "",
                optimizedOutput = "",
                originalErrors = "Не найден executable Java-класс с методом main",
                optimizedErrors = ""
            )
        }

        val defaultSuites = listOf(
            "empty_stdin" to "",
            "bare_newline" to "\n",
            "numeric_zero" to "0\n",
            "large_negative" to "-999999999\n",
            "integer_max" to "2147483647\n",
            "integer_min" to "-2147483648\n",
            "floating_point" to "3.141592653589793\n",
            "utf8_cyrillic" to "Привет, мир!\n",
            "punctuation_escapes" to "!@#\\$%^&*()\\t\\\"\\\\\n",
            "excessive_whitespace" to "   1   \\t  2  \\n\\n",
            "mixed_multiline_tokens" to "1 two\\n-3 4.5\\nсемь\\n",
            "delimiter_variations" to "1\\t2;3,4\\n",
            "long_alphanumeric" to ("A1b2".repeat(2048) + "\\n"),
            "large_numeric_array" to ((1..256).joinToString(" ") + "\\n"),
            "interactive_prompt_session" to "first\\nsecond\\n0\\n"
        )
        val testSuites = defaultSuites + testInputs.mapIndexed { index, input -> "custom_${index + 1}" to input }

        val futures = testSuites.map { (name, input) ->
            CompletableFuture.supplyAsync({
                val original = executeJavaCode(originalFiles, originalMainClass, input, 5000)
                val optimized = executeJavaCode(optimizedFiles, optimizedMainClass, input, 5000)
                val outputMatches = original.output == optimized.output
                val stderrSilent = original.errorOutput.isEmpty() && optimized.errorOutput.isEmpty()
                val cleanExit = original.exitCode == 0 && optimized.exitCode == 0
                val passed = original.compilationSuccess && optimized.compilationSuccess &&
                    outputMatches && stderrSilent && cleanExit
                val message = when {
                    !original.compilationSuccess -> "Ошибка оригинала: ${original.errorOutput}"
                    !optimized.compilationSuccess -> "Ошибка оптимизированного: ${optimized.errorOutput}"
                    !outputMatches -> "stdout отличается побайтно"
                    !stderrSilent -> "stderr не пуст"
                    !cleanExit -> "Некорректный exit code: ${original.exitCode} / ${optimized.exitCode}"
                    else -> "stdout/stderr/exit code идентичны ✓"
                }
                name to TestResult(
                    testName = name,
                    passed = passed,
                    originalTime = original.executionTime,
                    optimizedTime = optimized.executionTime,
                    message = message
                )
            }, verificationPool)
        }
        val testResults = futures.map { it.join().second }
        val totalOriginalTime = testResults.sumOf { it.originalTime }
        val totalOptimizedTime = testResults.sumOf { it.optimizedTime }
        val speedupFactor = if (totalOptimizedTime > 0 && totalOriginalTime > 0) {
            totalOriginalTime.toDouble() / totalOptimizedTime.toDouble()
        } else 1.0

        return VerificationResult(
            compilationSuccess = true,
            testResults = testResults,
            originalExecutionTime = totalOriginalTime,
            optimizedExecutionTime = totalOptimizedTime,
            speedupFactor = (speedupFactor * 100).roundToLong() / 100.0,
            behaviorPreserved = testResults.isNotEmpty() && testResults.all { it.passed },
            originalOutput = "",
            optimizedOutput = "",
            originalErrors = "",
            optimizedErrors = ""
        )
    }
}
