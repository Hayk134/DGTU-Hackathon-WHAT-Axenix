package com.axenix.javablueprints.model

import kotlinx.serialization.Serializable

// Core Domain Models

@Serializable
data class ProjectWorkspaceResponse(
    val project: ProjectState,
    val graph: BlueprintGraph,
    val analysis: AnalysisReport
)

@Serializable
data class ProjectState(
    val files: Map<String, String>, // filePath -> content
    val mainFile: String
)

@Serializable
data class BlueprintGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

@Serializable
data class GraphNode(
    val id: String, // AST path ID
    val type: NodeType,
    val label: String,
    val code: String,
    val location: SourceLocation,
    val complexity: Int? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class NodeType {
    START, END, METHOD, IF, ELSE, FOR, FOREACH, WHILE, SWITCH, CASE, 
    TRY, CATCH, FINALLY, THROW, RETURN, BREAK, CONTINUE, 
    ASSIGNMENT, METHOD_CALL, EXPRESSION, BLOCK
}

@Serializable
data class GraphEdge(
    val from: String,
    val to: String,
    val label: String? = null,
    val condition: String? = null
)

@Serializable
data class SourceLocation(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val startColumn: Int,
    val endColumn: Int
)

@Serializable
data class AnalysisReport(
    val defects: List<Defect>,
    val metrics: CodeMetrics,
    val recommendations: List<Recommendation>
)

@Serializable
data class Defect(
    val id: String,
    val type: DefectType,
    val severity: Severity,
    val location: SourceLocation,
    val title: String,
    val description: String,
    val complexity: String? = null,
    val codeFragment: String
)

@Serializable
enum class DefectType {
    NESTED_COMPLEXITY, LONG_METHOD, DUPLICATED_LOGIC, 
    INEFFICIENT_LOOP, HIDDEN_COMPLEXITY
}

@Serializable
enum class Severity {
    HIGH, MEDIUM, LOW
}

@Serializable
data class CodeMetrics(
    val totalLines: Int,
    val totalMethods: Int,
    val averageComplexity: Double,
    val maxComplexity: Int,
    val duplicatedBlocks: Int
)

@Serializable
data class Recommendation(
    val defectId: String,
    val title: String,
    val description: String,
    val expectedBenefit: String
)

// API Request/Response Models

@Serializable
data class LoadProjectRequest(
    val files: Map<String, String>? = null // filePath -> content
)

@Serializable
data class ParseRequest(
    val filePath: String,
    val content: String
)

@Serializable
data class ApplyOptimizationRequest(
    val files: Map<String, String>,
    val mainFile: String? = null
)

@Serializable
data class GraphMutationRequest(
    val nodeId: String,
    val operation: MutationOperation,
    val newCode: String? = null,
    val newNodeType: NodeType? = null,
    val filePath: String? = null
)

@Serializable
enum class MutationOperation {
    UPDATE, DELETE, ADD
}

@Serializable
data class OptimizeRequest(
    val applyOptimizations: Boolean = true,
    val decomposeMonolith: Boolean = false
)

@Serializable
data class RunRequest(
    val input: String = ""
)

@Serializable
data class VerifyRequest(
    val inputs: List<String>? = null
)

@Serializable
data class RunResult(
    val compilationSuccess: Boolean,
    val output: String,
    val errors: String,
    val exitCode: Int,
    val executionTime: Long,
    val mainClass: String
)

@Serializable
data class OptimizationResult(
    val before: ProjectWorkspaceResponse,
    val after: ProjectWorkspaceResponse,
    val changes: List<OptimizationChange>,
    val verification: VerificationResult
)

@Serializable
data class OptimizationChange(
    val type: String,
    val description: String,
    val location: SourceLocation,
    val complexityBefore: String,
    val complexityAfter: String
)

@Serializable
data class VerificationResult(
    val compilationSuccess: Boolean,
    val testResults: List<TestResult>,
    val originalExecutionTime: Long, // milliseconds
    val optimizedExecutionTime: Long,
    val speedupFactor: Double,
    val behaviorPreserved: Boolean,
    val originalOutput: String,
    val optimizedOutput: String,
    val originalErrors: String,
    val optimizedErrors: String
)

@Serializable
data class TestResult(
    val testName: String,
    val passed: Boolean,
    val originalTime: Long,
    val optimizedTime: Long,
    val message: String
)

@Serializable
data class ErrorResponse(
    val error: String,
    val details: String
)
