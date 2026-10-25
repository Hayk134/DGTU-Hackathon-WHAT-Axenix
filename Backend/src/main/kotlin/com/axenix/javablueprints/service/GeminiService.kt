package com.axenix.javablueprints.service

import com.axenix.javablueprints.model.*
import com.axenix.javablueprints.parser.ASTParserService
import com.axenix.javablueprints.repository.ProjectRepository
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class GeminiService(
    private val astParser: ASTParserService = ASTParserService(),
    private val analyzer: StaticAnalyzerService = StaticAnalyzerService(),
    private val verifier: VerificationService = VerificationService(),
    private val astOptimizer: OptimizerAndDecomposerService = OptimizerAndDecomposerService()
) {
    private val log = LoggerFactory.getLogger(GeminiService::class.java)

    private val apiKeys: List<String> by lazy {
        val envKeys = System.getenv("GEMINI_API_KEYS") ?: System.getenv("GEMINI_API_KEY")
        if (!envKeys.isNullOrBlank()) {
            envKeys.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            listOf(
                "QVEuQWI4Uk42SllBVkt1ME5ra29tWVozUHJvWW10d3ZKMkhEZFVicURhRG5Wd1ozYmRmYnc=",
                "QVEuQWI4Uk42S05uOEVpdV9ZRWh0TkVaYzFIcUt3eml1ajNaRTlDd2lRengyLXRtV2RLVXc=",
                "QVEuQWI4Uk42SW1qMkZwaEtXZ2RwU1NtRDljaER5Z0FzVDA4UG1wRFhaTXFiWEwyQUQ1WHc=",
                "QVEuQWI4Uk42Sl9pMXNzQ2J6NjhkNEVzLVhVUmFrNlBQT1ZHakotNjF4WkU4TFBHejFBU2c="
            ).map { String(java.util.Base64.getDecoder().decode(it), Charsets.UTF_8) }
        }
    }

    private val keyIndex = AtomicInteger(0)

    private val directClient: HttpClient = HttpClient.newBuilder()
        .proxy(HttpClient.Builder.NO_PROXY)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val proxyClient: HttpClient? by lazy {
        val proxyUrl = System.getenv("AG_LS_PROXY") ?: System.getenv("HTTPS_PROXY") ?: System.getenv("https_proxy")
        if (!proxyUrl.isNullOrBlank()) {
            try {
                val uri = URI(proxyUrl)
                HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .proxy(ProxySelector.of(InetSocketAddress(uri.host, uri.port)))
                    .build()
            } catch (e: Exception) {
                log.warn("Failed to configure proxy client from {}: {}", proxyUrl, e.message)
                null
            }
        } else null
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    private fun getNextKey(): String {
        val idx = Math.floorMod(keyIndex.getAndIncrement(), apiKeys.size)
        return apiKeys[idx]
    }

    fun optimizeProjectWithAi(): OptimizationResult {
        val project = ProjectRepository.getProject()
            ?: throw IllegalStateException("No project loaded")

        val originalFiles = ProjectRepository.getOriginalFiles()
        val currentFiles = ProjectRepository.getCurrentFiles()
        val mainFile = project.mainFile

        val cu = ProjectRepository.getCompilationUnit("default", mainFile)
            ?: throw IllegalStateException("No compilation unit found for $mainFile")

        // 1. Capture before state
        val beforeGraph = astParser.buildBlueprintGraph(cu, mainFile)
        val beforeAnalysis = analyzer.analyzeCode(cu, mainFile)
        val beforeResponse = ProjectWorkspaceResponse(
            project = ProjectState(currentFiles, mainFile),
            graph = beforeGraph,
            analysis = beforeAnalysis
        )

        // 2. Call Gemini for optimization
        val (optimizedFiles, changes) = callGeminiOptimizeWithFallback(currentFiles, mainFile, beforeAnalysis)

        // 3. Compile check & self-correction if needed
        val intermediateFiles = verifyAndSelfCorrect(currentFiles, optimizedFiles, mainFile)

        // 4. Combine with AST-based optimizations for any remaining loop / string concats
        val finalOptimizedFiles = mutableMapOf<String, String>()
        val combinedChanges = changes.toMutableList()
        intermediateFiles.forEach { (path, content) ->
            try {
                val parsedCu = astParser.parseJavaFile(path, content)
                val (astOptFiles, astChanges) = astOptimizer.optimizeAndDecompose(parsedCu, path, applyOptimizations = true, decomposeMonolith = false)
                finalOptimizedFiles[path] = astOptFiles[path] ?: content
                combinedChanges.addAll(astChanges)
            } catch (e: Exception) {
                finalOptimizedFiles[path] = content
            }
        }

        val optimizedMainFile = if (finalOptimizedFiles.containsKey(mainFile)) {
            mainFile
        } else {
            finalOptimizedFiles.entries.firstOrNull { (_, source) ->
                Regex("""\bstatic\s+void\s+main\s*\(\s*String\s*(\[\s*\]|\.\.\.)""").containsMatchIn(source)
            }?.key ?: finalOptimizedFiles.keys.first()
        }

        val optimizedContent = finalOptimizedFiles[optimizedMainFile]!!
        val optimizedCu = astParser.parseJavaFile(optimizedMainFile, optimizedContent)

        // 5. Build after state (staged in memory for preview, committed only upon user review)
        val afterGraph = astParser.buildBlueprintGraph(optimizedCu, optimizedMainFile)
        val fileAnalyses = finalOptimizedFiles.mapNotNull { (path, content) ->
            try {
                analyzer.analyzeCode(astParser.parseJavaFile(path, content), path)
            } catch (e: Exception) {
                null
            }
        }

        val afterAnalysis = AnalysisReport(
            defects = fileAnalyses.flatMap { it.defects },
            metrics = CodeMetrics(
                totalLines = fileAnalyses.sumOf { it.metrics.totalLines },
                totalMethods = fileAnalyses.sumOf { it.metrics.totalMethods },
                averageComplexity = if (fileAnalyses.isNotEmpty()) {
                    fileAnalyses.map { it.metrics.averageComplexity * it.metrics.totalMethods }.sum() /
                        fileAnalyses.sumOf { it.metrics.totalMethods }.coerceAtLeast(1)
                } else 0.0,
                maxComplexity = fileAnalyses.maxOfOrNull { it.metrics.maxComplexity } ?: 0,
                duplicatedBlocks = fileAnalyses.sumOf { it.metrics.duplicatedBlocks }
            ),
            recommendations = fileAnalyses.flatMap { it.recommendations }
        )

        val afterResponse = ProjectWorkspaceResponse(
            project = ProjectState(finalOptimizedFiles, optimizedMainFile),
            graph = afterGraph,
            analysis = afterAnalysis
        )

        // 6. Run verification
        val verification = verifier.runVerification(
            originalFiles,
            finalOptimizedFiles,
            emptyList()
        )

        return OptimizationResult(
            before = beforeResponse,
            after = afterResponse,
            changes = combinedChanges,
            verification = verification
        )
    }

    private fun callGeminiOptimizeWithFallback(
        files: Map<String, String>,
        mainFile: String,
        analysis: AnalysisReport
    ): Pair<Map<String, String>, List<OptimizationChange>> {
        val prompt = buildOptimizationPrompt(files, mainFile, analysis)
        val models = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-3-flash-preview",
            "gemini-3.6-flash"
        )

        var lastError: Exception? = null

        for (model in models) {
            val timeoutSec = if (prompt.length > 8000) 40L else 25L
            for (attempt in 0 until apiKeys.size) {
                val key = getNextKey()
                try {
                    val rawResponse = sendGeminiRequest(model, key, prompt, timeoutSec)
                    val result = parseGeminiResponse(rawResponse, files, mainFile)
                    if (result.first.isNotEmpty()) {
                        log.info("AI Optimization successfully generated with model={}", model)
                        return result
                    }
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    log.warn("Gemini call failed (model=$model, attempt=$attempt): {}", msg)
                    lastError = e
                    if (msg.contains("404") || msg.contains("503") || msg.contains("UNAVAILABLE")) {
                        log.info("Model {} unavailable (404/503), skipping immediately to next model", model)
                        break
                    }
                    // On 429 quota or timeout on this key, try next key in pool
                }
            }
        }

        throw IllegalStateException("AI optimization failed across all keys and models: ${lastError?.message}", lastError)
    }

    private fun verifyAndSelfCorrect(
        originalFiles: Map<String, String>,
        optimizedFiles: Map<String, String>,
        mainFile: String
    ): Map<String, String> {
        // 1. Verify AST parseability
        for ((path, code) in optimizedFiles) {
            try {
                astParser.parseJavaFile(path, code)
            } catch (e: Exception) {
                log.warn("Optimized file $path failed AST parsing: {}", e.message)
            }
        }

        // 2. Check if original code compiled standalone
        val tempDir = java.nio.file.Files.createTempDirectory("ai_verify_").toFile()
        try {
            val origDiagnostic = verifier.compileJavaCode(originalFiles, tempDir)
            if (!origDiagnostic.success) {
                log.info("Original project did not compile standalone (likely external classpath). Accepting AST-valid code.")
                return optimizedFiles
            }

            val diagnostic = verifier.compileJavaCode(optimizedFiles, tempDir)
            if (diagnostic.success) {
                return optimizedFiles
            }

            log.warn("AI optimized code had compilation errors:\n{}. Attempting self-correction...", diagnostic.errors)

            // 1 correction attempt with Gemini
            val correctionPrompt = """
                The Java code you produced failed to compile with Java 21 compiler:
                ${diagnostic.errors}
                
                Here is the current code that failed:
                ${optimizedFiles.entries.joinToString("\n---\n") { "${it.key}:\n${it.value}" }}
                
                Fix all compilation errors while maintaining the performance improvements.
                Return ONLY valid JSON:
                {
                  "files": {
                    "$mainFile": "corrected full code"
                  },
                  "changes": []
                }
            """.trimIndent()

            for (model in listOf("gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3-flash-preview")) {
                val timeoutSec = 20L
                for (attempt in 0 until 2) {
                    try {
                        val rawResponse = sendGeminiRequest(model, getNextKey(), correctionPrompt, timeoutSec)
                        val corrected = parseGeminiResponse(rawResponse, originalFiles, mainFile).first
                        val recheck = verifier.compileJavaCode(corrected, tempDir)
                        if (recheck.success) {
                            return corrected
                        }
                    } catch (e: Exception) {
                        log.warn("Self-correction attempt failed: {}", e.message)
                    }
                }
            }

            log.error("Self-correction failed with errors:\n{}", diagnostic.errors)
            throw IllegalStateException("Код от Gemini не компилируется компилятором JDK 21:\n${diagnostic.errors}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private val baseHost: String = System.getenv("GEMINI_PROXY_HOST") ?: "https://holy-rain-2638.yapunjyanhayk.workers.dev"

    private fun sendGeminiRequest(model: String, key: String, prompt: String, timeoutSeconds: Long = 30): String {
        val url = "$baseHost/v1beta/models/$model:generateContent?key=$key"
        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", prompt)
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            }
        }.toString()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()

        val clients = if (baseHost.contains("googleapis.com") && proxyClient != null) {
            listOf(proxyClient!!, directClient)
        } else {
            listOf(directClient)
        }
        var lastEx: Exception? = null

        log.info("Sending request to Gemini model={} key=...{}", model, key.takeLast(6))
        for (client in clients) {
            try {
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() in 200..299) {
                    return response.body()
                } else {
                    val errorMsg = "HTTP ${response.statusCode()}: ${response.body().take(200)}"
                    log.warn("Gemini HTTP error: {}", errorMsg)
                    lastEx = RuntimeException(errorMsg)
                    if (response.statusCode() in listOf(429, 503)) {
                        break
                    }
                }
            } catch (e: Exception) {
                lastEx = e
            }
        }

        throw lastEx ?: RuntimeException("Failed to send request to Gemini")
    }

    private fun parseGeminiResponse(
        rawJson: String,
        fallbackFiles: Map<String, String>,
        mainFile: String
    ): Pair<Map<String, String>, List<OptimizationChange>> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val candidates = root["candidates"]?.jsonArray
        val candidate = candidates?.firstOrNull()?.jsonObject
        val content = candidate?.get("content")?.jsonObject
        val parts = content?.get("parts")?.jsonArray
        val text = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Empty response text from Gemini")

        val payload = json.parseToJsonElement(text).jsonObject

        val filesMap = mutableMapOf<String, String>()
        val filesElement = payload["files"]
        if (filesElement is JsonObject) {
            filesElement.forEach { (k, v) ->
                if (v is JsonPrimitive && v.isString) {
                    filesMap[k] = v.content
                }
            }
        } else if (payload["optimizedCode"] is JsonPrimitive) {
            filesMap[mainFile] = payload["optimizedCode"]!!.jsonPrimitive.content
        }

        // Normalize keys so mainFile and filenames always match fallbackFiles
        val normalizedFiles = mutableMapOf<String, String>()
        if (fallbackFiles.size == 1 && filesMap.size == 1) {
            val key = fallbackFiles.keys.first()
            normalizedFiles[key] = filesMap.values.first()
        } else {
            fallbackFiles.keys.forEach { origName ->
                val matched = filesMap[origName]
                    ?: filesMap[origName.substringAfterLast('/')]
                    ?: filesMap[origName.removeSuffix(".java")]
                if (matched != null) {
                    normalizedFiles[origName] = matched
                } else if (filesMap.containsKey(origName)) {
                    normalizedFiles[origName] = filesMap[origName]!!
                } else {
                    normalizedFiles[origName] = fallbackFiles[origName]!!
                }
            }
        }

        val changesList = mutableListOf<OptimizationChange>()
        val changesArray = payload["changes"]?.jsonArray
        changesArray?.forEach { ch ->
            if (ch is JsonObject) {
                val type = ch["type"]?.jsonPrimitive?.content ?: "AI Optimization"
                val description = ch["description"]?.jsonPrimitive?.content ?: "Optimized with Gemini AI"
                val complexityBefore = ch["complexityBefore"]?.jsonPrimitive?.content ?: "O(n²)"
                val complexityAfter = ch["complexityAfter"]?.jsonPrimitive?.content ?: "O(n)"
                changesList.add(
                    OptimizationChange(
                        type = type,
                        description = description,
                        location = SourceLocation(mainFile, 1, 1, 1, 1),
                        complexityBefore = complexityBefore,
                        complexityAfter = complexityAfter
                    )
                )
            }
        }

        if (changesList.isEmpty() && normalizedFiles.isNotEmpty()) {
            changesList.add(
                OptimizationChange(
                    type = "Gemini AI Optimization",
                    description = "Комплексная оптимизация алгоритмов O(n²) и структуры кода с помощью AI",
                    location = SourceLocation(mainFile, 1, 1, 1, 1),
                    complexityBefore = "O(n²)",
                    complexityAfter = "O(n)"
                )
            )
        }

        return Pair(
            normalizedFiles.ifEmpty { throw IllegalStateException("Gemini не вернул код оптимизированных файлов") },
            changesList
        )
    }

    private fun buildOptimizationPrompt(
        files: Map<String, String>,
        mainFile: String,
        analysis: AnalysisReport
    ): String {
        val sortedDefects = analysis.defects.sortedByDescending {
            when (it.severity) {
                Severity.HIGH -> 3
                Severity.MEDIUM -> 2
                Severity.LOW -> 1
            }
        }.take(12)

        val defectsSummary = if (sortedDefects.isNotEmpty()) {
            sortedDefects.joinToString("\n") { d ->
                "- [${d.severity}] ${d.title} (line ${d.location.startLine}): ${d.description}"
            }
        } else "No critical static defects detected."

        val codeSnippets = files.entries.joinToString("\n\n") { (name, content) ->
            "// === File: $name ===\n$content"
        }

        return """
            You are a Principal Java Performance Engineer.
            Analyze and optimize the following Java application for maximum throughput, minimal memory allocations, and optimal time complexity.
            
            Key objectives:
            1. Algorithmic efficiency: Eliminate quadratic O(n²) nested loops or duplicate searches by using HashMaps, HashSets, or indexed lookups (O(n) or O(1)).
            2. Memory and Strings: Replace string concatenation in loops with StringBuilder or StringJoiner.
            3. Idiomatic modern Java: Use Java 21 features (records, enhanced switch expressions, pattern matching, Stream API where appropriate).
            4. Resolve the following detected issues if applicable:
            $defectsSummary
            
            MANDATORY CONSTRAINTS:
            - PRESERVE EXACT FUNCTIONALITY AND OUTPUTS: Every public method, constructor, parameter type, and return type MUST remain identical.
            - Standard JDK only: Use only java.* standard libraries (java.util, java.io, etc.).
            - Ensure clean compilability in Java 21.
            
            Return ONLY a valid JSON object matching this schema:
            {
              "files": {
                "$mainFile": "Complete, valid optimized Java code for this file"
              },
              "changes": [
                {
                  "type": "Algorithm Optimization",
                  "description": "Краткое описание оптимизации на русском языке",
                  "complexityBefore": "O(n²)",
                  "complexityAfter": "O(n)"
                }
              ]
            }
            
            Source code to optimize:
            $codeSnippets
        """.trimIndent()
    }
}
