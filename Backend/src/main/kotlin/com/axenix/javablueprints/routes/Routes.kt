package com.axenix.javablueprints.routes

import com.axenix.javablueprints.model.*
import com.axenix.javablueprints.parser.ASTParserService
import com.axenix.javablueprints.repository.ProjectRepository
import com.axenix.javablueprints.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun Application.configureRoutes() {
    val astParser = ASTParserService()
    val analyzer = StaticAnalyzerService()
    val optimizer = OptimizerAndDecomposerService()
    val mutator = GraphMutatorService()
    val verifier = VerificationService()
    val geminiService = GeminiService(astParser, analyzer, verifier)
    
    routing {
        get("/api/health") {
            call.respond(mapOf("status" to "ok", "service" to "Java Blueprints Backend"))
        }
        
        post("/api/project/load") {
            try {
                val request = call.receive<LoadProjectRequest>()
                
                val files = request.files
                    ?: throw IllegalArgumentException("files must be provided")
                
                val mainFile = files.entries.firstOrNull { (_, source) ->
                    Regex("""\bstatic\s+void\s+main\s*\(\s*String\s*(\[\s*\]|\.\.\.)""").containsMatchIn(source)
                }?.key ?: files.keys.first()
                
                ProjectRepository.createProject("default", files, mainFile)
                
                // Parse main file
                val mainContent = files[mainFile]!!
                val cu = astParser.parseJavaFile(mainFile, mainContent)
                ProjectRepository.updateCompilationUnit("default", mainFile, cu)
                
                val graph = astParser.buildBlueprintGraph(cu, mainFile)
                val analysis = analyzer.analyzeCode(cu, mainFile)
                
                val response = ProjectWorkspaceResponse(
                    project = ProjectState(files, mainFile),
                    graph = graph,
                    analysis = analysis
                )
                
                call.respond(response)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Failed to load project", e.message ?: "Unknown error")
                )
            }
        }

        post("/api/project/load-demo") {
            try {
                val content = object {}.javaClass.getResourceAsStream("/demo.java")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: java.io.File("../adskiy-dro__--java--ilnya.java").takeIf { it.exists() }?.readText(Charsets.UTF_8)
                    ?: java.io.File("adskiy-dro__--java--ilnya.java").takeIf { it.exists() }?.readText(Charsets.UTF_8)
                    ?: java.io.File("c:/Users/gg/Desktop/adskiy-dro__--java--ilnya.java").takeIf { it.exists() }?.readText(Charsets.UTF_8)
                    ?: """
                    package com.enterprise.demo;
                    public class EnterpriseUniversalCoreSystemEngineFinalManager {
                        public static void main(String[] args) {
                            System.out.println("Enterprise Core Demo Loaded");
                        }
                    }
                    """.trimIndent()
                val mainFile = "EnterpriseUniversalCoreSystemEngineFinalManager.java"
                val files = mapOf(mainFile to content)
                ProjectRepository.createProject("default", files, mainFile)
                val cu = astParser.parseJavaFile(mainFile, content)
                ProjectRepository.updateCompilationUnit("default", mainFile, cu)
                val graph = astParser.buildBlueprintGraph(cu, mainFile)
                val analysis = analyzer.analyzeCode(cu, mainFile)
                call.respond(ProjectWorkspaceResponse(
                    project = ProjectState(files, mainFile),
                    graph = graph,
                    analysis = analysis
                ))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to load demo project", e.message ?: "Unknown error")
                )
            }
        }
        
        post("/api/project/parse") {
            try {
                val request = call.receive<ParseRequest>()
                
                val cu = astParser.parseJavaFile(request.filePath, request.content)
                ProjectRepository.updateCompilationUnit("default", request.filePath, cu)
                ProjectRepository.updateFiles("default", mapOf(request.filePath to request.content))
                
                val graph = astParser.buildBlueprintGraph(cu, request.filePath)
                val analysis = analyzer.analyzeCode(cu, request.filePath)
                
                val currentFiles = ProjectRepository.getCurrentFiles()
                val project = ProjectRepository.getProject()
                
                val response = ProjectWorkspaceResponse(
                    project = ProjectState(currentFiles, project?.mainFile ?: request.filePath),
                    graph = graph,
                    analysis = analysis
                )
                
                call.respond(response)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Failed to parse code", e.message ?: "Unknown error")
                )
            }
        }
        
        post("/api/graph/mutate") {
            try {
                val request = call.receive<GraphMutationRequest>()
                
                val project = ProjectRepository.getProject()
                    ?: throw IllegalStateException("No project loaded")
                
                val filePath = request.filePath ?: project.mainFile
                val cu = ProjectRepository.getCompilationUnit("default", filePath)
                    ?: throw IllegalStateException("No compilation unit found for $filePath")

                val updatedCode = mutator.mutateGraph(cu, filePath, request)
                ProjectRepository.updateFiles("default", mapOf(filePath to updatedCode))

                val newCu = astParser.parseJavaFile(filePath, updatedCode)
                ProjectRepository.updateCompilationUnit("default", filePath, newCu)

                val graph = astParser.buildBlueprintGraph(newCu, filePath)
                val analysis = analyzer.analyzeCode(newCu, filePath)
                
                val response = ProjectWorkspaceResponse(
                    project = ProjectState(ProjectRepository.getCurrentFiles(), project.mainFile),
                    graph = graph,
                    analysis = analysis
                )
                
                call.respond(response)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Failed to mutate graph", e.message ?: "Unknown error")
                )
            }
        }
        
        post("/api/project/optimize") {
            try {
                val request = call.receive<OptimizeRequest>()
                
                val project = ProjectRepository.getProject()
                    ?: throw IllegalStateException("No project loaded")
                
                val originalFiles = ProjectRepository.getOriginalFiles()
                
                val cu = ProjectRepository.getCompilationUnit("default", project.mainFile)
                    ?: throw IllegalStateException("No compilation unit found")
                
                // Capture before state
                val beforeGraph = astParser.buildBlueprintGraph(cu, project.mainFile)
                val beforeAnalysis = analyzer.analyzeCode(cu, project.mainFile)
                val beforeResponse = ProjectWorkspaceResponse(
                    project = ProjectState(ProjectRepository.getCurrentFiles(), project.mainFile),
                    graph = beforeGraph,
                    analysis = beforeAnalysis
                )
                
                // Apply optimizations
                val (optimizedFiles, changes) = optimizer.optimizeAndDecompose(
                    cu,
                    project.mainFile,
                    request.applyOptimizations,
                    request.decomposeMonolith
                )
                
                ProjectRepository.updateFiles("default", optimizedFiles)
                
                // Parse optimized main file
                val optimizedMainFile = if (optimizedFiles.containsKey(project.mainFile)) {
                    project.mainFile
                } else {
                    optimizedFiles.entries.firstOrNull { (_, source) ->
                        Regex("""\bstatic\s+void\s+main\s*\(\s*String\s*(\[\s*\]|\.\.\.)""").containsMatchIn(source)
                    }?.key ?: optimizedFiles.keys.first()
                }
                
                val optimizedContent = optimizedFiles[optimizedMainFile]!!
                val optimizedCu = astParser.parseJavaFile(optimizedMainFile, optimizedContent)
                optimizedFiles.forEach { (path, content) ->
                    if (path != optimizedMainFile) {
                        ProjectRepository.updateCompilationUnit("default", path, astParser.parseJavaFile(path, content))
                    }
                }
                ProjectRepository.updateCompilationUnit("default", optimizedMainFile, optimizedCu)

                val afterGraph = astParser.buildBlueprintGraph(optimizedCu, optimizedMainFile)
                val fileAnalyses = optimizedFiles.map { (path, content) ->
                    analyzer.analyzeCode(astParser.parseJavaFile(path, content), path)
                }
                val afterAnalysis = AnalysisReport(
                    defects = fileAnalyses.flatMap { it.defects },
                    metrics = CodeMetrics(
                        totalLines = fileAnalyses.sumOf { it.metrics.totalLines },
                        totalMethods = fileAnalyses.sumOf { it.metrics.totalMethods },
                        averageComplexity = fileAnalyses.map { it.metrics.averageComplexity * it.metrics.totalMethods }.sum() /
                            fileAnalyses.sumOf { it.metrics.totalMethods }.coerceAtLeast(1),
                        maxComplexity = fileAnalyses.maxOfOrNull { it.metrics.maxComplexity } ?: 0,
                        duplicatedBlocks = fileAnalyses.sumOf { it.metrics.duplicatedBlocks }
                    ),
                    recommendations = fileAnalyses.flatMap { it.recommendations }
                )
                val afterResponse = ProjectWorkspaceResponse(
                    project = ProjectState(optimizedFiles, optimizedMainFile),
                    graph = afterGraph,
                    analysis = afterAnalysis
                )
                
                // Run verification
                val verification = verifier.runVerification(
                    originalFiles,
                    optimizedFiles,
                    emptyList()
                )
                
                val result = OptimizationResult(
                    before = beforeResponse,
                    after = afterResponse,
                    changes = changes,
                    verification = verification
                )
                
                call.respond(result)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to optimize project", e.message ?: "Unknown error")
                )
            }
        }

        post("/api/project/optimize-ai") {
            try {
                val result = geminiService.optimizeProjectWithAi()
                call.respond(result)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to optimize project with AI", e.message ?: "Unknown error")
                )
            }
        }

        post("/api/project/apply-optimization") {
            try {
                val request = call.receive<ApplyOptimizationRequest>()
                val files = request.files
                val mainFile = request.mainFile ?: ProjectRepository.getProject()?.mainFile ?: files.keys.first()

                ProjectRepository.updateFiles("default", files)
                val cu = astParser.parseJavaFile(mainFile, files[mainFile] ?: files.values.first())
                ProjectRepository.updateCompilationUnit("default", mainFile, cu)
                files.forEach { (path, content) ->
                    if (path != mainFile) {
                        try {
                            ProjectRepository.updateCompilationUnit("default", path, astParser.parseJavaFile(path, content))
                        } catch (_: Exception) { }
                    }
                }

                val graph = astParser.buildBlueprintGraph(cu, mainFile)
                val fileAnalyses = files.mapNotNull { (path, content) ->
                    try {
                        analyzer.analyzeCode(astParser.parseJavaFile(path, content), path)
                    } catch (_: Exception) { null }
                }

                val analysis = AnalysisReport(
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

                call.respond(ProjectWorkspaceResponse(
                    project = ProjectState(files, mainFile),
                    graph = graph,
                    analysis = analysis
                ))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to apply optimization", e.message ?: "Unknown error")
                )
            }
        }

        get("/api/project/export") {
            try {
                val files = ProjectRepository.getCurrentFiles()
                if (files.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("No project loaded", "There are no project files to export"))
                    return@get
                }

                val archive = ByteArrayOutputStream()
                ZipOutputStream(archive).use { zip ->
                    files.toSortedMap().forEach { (path, content) ->
                        val safePath = path.replace('\\', '/').trimStart('/')
                        if (safePath.isBlank() || safePath.contains("../") || safePath.startsWith("..")) {
                            throw IllegalArgumentException("Unsafe project file path: $path")
                        }
                        zip.putNextEntry(ZipEntry(safePath))
                        zip.write(content.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "java-blueprints-refactored.zip").toString()
                )
                call.respondBytes(archive.toByteArray(), ContentType.Application.Zip)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to export project", e.message ?: "Unknown error")
                )
            }
        }

        post("/api/project/run") {
            try {
                val request = call.receive<RunRequest>()
                val project = ProjectRepository.getProject()
                    ?: throw IllegalStateException("No project loaded")
                val result = verifier.runProject(
                    ProjectRepository.getCurrentFiles(),
                    project.mainFile,
                    request.input
                )
                call.respond(result)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to run project", e.message ?: "Unknown error")
                )
            }
        }
        
        post("/api/project/verify") {
            try {
                val originalFiles = ProjectRepository.getOriginalFiles()
                val currentFiles = ProjectRepository.getCurrentFiles()
                val request = try { call.receiveNullable<VerifyRequest>() } catch (_: Exception) { null }
                val customInputs = request?.inputs ?: emptyList()
                
                val verification = verifier.runVerification(
                    originalFiles,
                    currentFiles,
                    customInputs
                )
                
                call.respond(verification)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to verify project", e.message ?: "Unknown error")
                )
            }
        }
    }
}
