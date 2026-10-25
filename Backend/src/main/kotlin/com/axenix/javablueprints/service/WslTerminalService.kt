package com.axenix.javablueprints.service

import com.axenix.javablueprints.model.TerminalExecResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

class WslTerminalService {
    private val logger = LoggerFactory.getLogger(WslTerminalService::class.java)

    companion object {
        private const val DEFAULT_WSL_DIR = "/mnt/host/c/Users/gg/Desktop"
        private const val DEFAULT_WIN_DIR = "C:\\Users\\gg\\Desktop"
        private const val CWD_START = "__CWD_START__"
        private const val CWD_END = "__CWD_END__"
    }

    private val wslAvailable: Boolean by lazy {
        try {
            val process = ProcessBuilder("wsl.exe", "-e", "true")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            finished && process.exitValue() == 0
        } catch (e: Exception) {
            logger.warn("WSL check failed: ${e.message}")
            false
        }
    }

    fun execute(rawCommand: String, cwd: String?): TerminalExecResponse {
        val trimmedCommand = rawCommand.trim()
        if (trimmedCommand.isEmpty()) {
            return TerminalExecResponse(
                output = "",
                cwd = cwd ?: if (wslAvailable) DEFAULT_WSL_DIR else DEFAULT_WIN_DIR,
                exitCode = 0,
                environment = if (wslAvailable) "WSL2 (Linux)" else "PowerShell (Windows)"
            )
        }

        return if (wslAvailable) {
            executeWsl(trimmedCommand, cwd)
        } else {
            executePowerShell(trimmedCommand, cwd)
        }
    }

    private fun executeWsl(command: String, cwd: String?): TerminalExecResponse {
        val startDir = cwd?.takeIf { it.isNotBlank() } ?: DEFAULT_WSL_DIR
        val script = """
            cd "$startDir" 2>/dev/null || cd "$DEFAULT_WSL_DIR" 2>/dev/null || true
            $command
            _EC=${'$'}?
            echo "$CWD_START"
            pwd
            echo "$CWD_END"
            exit ${'$'}_EC
        """.trimIndent()

        return try {
            val process = ProcessBuilder("wsl.exe", "-e", "sh", "-c", script)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return TerminalExecResponse(
                    output = output + "\n[Process timed out after 30 seconds]",
                    cwd = startDir,
                    exitCode = 124,
                    environment = "WSL2 (Linux)"
                )
            }

            val (cleanOutput, parsedCwd) = parseWslOutput(output, startDir)
            TerminalExecResponse(
                output = cleanOutput,
                cwd = parsedCwd,
                exitCode = process.exitValue(),
                environment = "WSL2 (Linux)"
            )
        } catch (e: Exception) {
            TerminalExecResponse(
                output = "Error executing command in WSL: ${e.message}",
                cwd = startDir,
                exitCode = 1,
                environment = "WSL2 (Linux)"
            )
        }
    }

    private fun parseWslOutput(rawOutput: String, fallbackCwd: String): Pair<String, String> {
        val startIndex = rawOutput.indexOf(CWD_START)
        val endIndex = rawOutput.indexOf(CWD_END)

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            val cleanOutput = rawOutput.substring(0, startIndex).trimEnd()
            val parsedCwd = rawOutput.substring(startIndex + CWD_START.length, endIndex).trim()
            return Pair(cleanOutput, if (parsedCwd.isNotBlank()) parsedCwd else fallbackCwd)
        }

        return Pair(rawOutput.trimEnd(), fallbackCwd)
    }

    private fun executePowerShell(command: String, cwd: String?): TerminalExecResponse {
        val startDir = cwd?.takeIf { it.isNotBlank() } ?: DEFAULT_WIN_DIR
        return try {
            val process = ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "Set-Location '$startDir'; $command; (Get-Location).Path")
                .directory(File(startDir).takeIf { it.exists() } ?: File(DEFAULT_WIN_DIR))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return TerminalExecResponse(
                    output = output + "\n[Process timed out after 30 seconds]",
                    cwd = startDir,
                    exitCode = 124,
                    environment = "PowerShell (Windows fallback)"
                )
            }

            val lines = output.trim().lines()
            val lastLine = lines.lastOrNull()?.trim() ?: startDir
            val remainingOutput = lines.dropLast(1).joinToString("\n")

            TerminalExecResponse(
                output = remainingOutput,
                cwd = if (File(lastLine).exists()) lastLine else startDir,
                exitCode = process.exitValue(),
                environment = "PowerShell (Windows fallback)"
            )
        } catch (e: Exception) {
            TerminalExecResponse(
                output = "Error executing command in PowerShell: ${e.message}",
                cwd = startDir,
                exitCode = 1,
                environment = "PowerShell (Windows fallback)"
            )
        }
    }
}
