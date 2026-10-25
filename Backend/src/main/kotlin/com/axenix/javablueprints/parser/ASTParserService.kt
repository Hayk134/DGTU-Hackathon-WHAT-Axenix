package com.axenix.javablueprints.parser

import com.axenix.javablueprints.model.*
import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ParseResult
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.stmt.*
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import java.nio.charset.StandardCharsets

class ASTParserService {
    
    private val javaParser: JavaParser
    
    init {
        val typeSolver = CombinedTypeSolver().apply {
            add(ReflectionTypeSolver())
        }
        val symbolSolver = JavaSymbolSolver(typeSolver)
        javaParser = JavaParser().apply {
            parserConfiguration.setSymbolResolver(symbolSolver)
            parserConfiguration.setAttributeComments(false)
            parserConfiguration.languageLevel = ParserConfiguration.LanguageLevel.JAVA_21
        }
        StaticJavaParser.getParserConfiguration().languageLevel = ParserConfiguration.LanguageLevel.JAVA_21
    }
    
    fun parseJavaFile(filePath: String, content: String): CompilationUnit {
        val parseResult: ParseResult<CompilationUnit> = javaParser.parse(content)
        
        if (!parseResult.isSuccessful) {
            val problems = parseResult.problems.joinToString("\n") { it.message }
            throw IllegalArgumentException("Failed to parse Java file '$filePath':\n$problems")
        }
        
        return parseResult.result.orElseThrow {
            IllegalStateException("Parse result is empty for file '$filePath'")
        }
    }
    
    fun buildBlueprintGraph(cu: CompilationUnit, filePath: String): BlueprintGraph {
        val nodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<GraphEdge>()
        val nodeIdCounter = NodeIdCounter()
        
        // Add START node
        val startNode = GraphNode(
            id = "start",
            type = NodeType.START,
            label = "START",
            code = "",
            location = SourceLocation(filePath, 1, 1, 0, 0)
        )
        nodes.add(startNode)
        
        // Visit all constructors
        cu.findAll(ConstructorDeclaration::class.java).forEach { constructor ->
            val constructorStartId = "constructor_${constructor.nameAsString}_start"
            val constructorNode = GraphNode(
                id = constructorStartId,
                type = NodeType.METHOD,
                label = "Constructor: ${constructor.nameAsString}()",
                code = constructor.declarationAsString,
                location = extractLocation(constructor, filePath),
                complexity = calculateConstructorComplexity(constructor)
            )
            nodes.add(constructorNode)
            edges.add(GraphEdge(startNode.id, constructorStartId))
            
            // Parse constructor body
            val lastNodeId = processBlockStatements(
                constructor.body.statements,
                constructorStartId,
                filePath,
                constructor.nameAsString,
                nodes,
                edges,
                nodeIdCounter
            )
            
            // Add END node for this constructor
            val constructorEndId = "constructor_${constructor.nameAsString}_end"
            val endNode = GraphNode(
                id = constructorEndId,
                type = NodeType.END,
                label = "END ${constructor.nameAsString}",
                code = "",
                location = extractLocation(constructor, filePath)
            )
            nodes.add(endNode)
            edges.add(GraphEdge(lastNodeId, constructorEndId))
        }
        
        // Visit all methods
        cu.findAll(MethodDeclaration::class.java).forEach { method ->
            val methodStartId = "method_${method.nameAsString}_start"
            val methodNode = GraphNode(
                id = methodStartId,
                type = NodeType.METHOD,
                label = "Method: ${method.nameAsString}()",
                code = method.declarationAsString,
                location = extractLocation(method, filePath),
                complexity = calculateCyclomaticComplexity(method)
            )
            nodes.add(methodNode)
            edges.add(GraphEdge(startNode.id, methodStartId))
            
            // Parse method body
            method.body.ifPresent { body ->
                val lastNodeId = processBlockStatements(
                    body.statements,
                    methodStartId,
                    filePath,
                    method.nameAsString,
                    nodes,
                    edges,
                    nodeIdCounter
                )
                
                // Add END node for this method
                val methodEndId = "method_${method.nameAsString}_end"
                val endNode = GraphNode(
                    id = methodEndId,
                    type = NodeType.END,
                    label = "END ${method.nameAsString}",
                    code = "",
                    location = extractLocation(method, filePath)
                )
                nodes.add(endNode)
                edges.add(GraphEdge(lastNodeId, methodEndId))
            }
        }
        
        return BlueprintGraph(nodes, edges)
    }
    
    private fun processBlockStatements(
        statements: List<Statement>,
        parentNodeId: String,
        filePath: String,
        methodName: String,
        nodes: MutableList<GraphNode>,
        edges: MutableList<GraphEdge>,
        counter: NodeIdCounter
    ): String {
        var currentNodeId = parentNodeId
        
        statements.forEach { stmt ->
            val nodeId = generateNodeId(stmt, methodName, counter)
            
            when (stmt) {
                is IfStmt -> {
                    val ifNode = GraphNode(
                        id = nodeId,
                        type = NodeType.IF,
                        label = "if (${stmt.condition})",
                        code = stmt.toString().take(100),
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(ifNode)
                    edges.add(GraphEdge(currentNodeId, nodeId, label = "if"))
                    
                    // Then branch
                    val thenNodeId = processStatement(stmt.thenStmt, nodeId, filePath, methodName, nodes, edges, counter)
                    
                    // Else branch (if exists)
                    val elseNodeId = stmt.elseStmt.map { elseStmt ->
                        processStatement(elseStmt, nodeId, filePath, methodName, nodes, edges, counter)
                    }.orElse(nodeId)
                    
                    // Merge point
                    val mergeId = "${methodName}_merge_${counter.next()}"
                    val mergeNode = GraphNode(
                        id = mergeId,
                        type = NodeType.EXPRESSION,
                        label = "merge",
                        code = "",
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(mergeNode)
                    edges.add(GraphEdge(thenNodeId, mergeId))
                    edges.add(GraphEdge(elseNodeId, mergeId))
                    currentNodeId = mergeId
                }
                
                is ForStmt -> {
                    val forNode = GraphNode(
                        id = nodeId,
                        type = NodeType.FOR,
                        label = "for",
                        code = stmt.toString().take(100),
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(forNode)
                    edges.add(GraphEdge(currentNodeId, nodeId))
                    
                    // Loop body
                    val bodyNodeId = processStatement(stmt.body, nodeId, filePath, methodName, nodes, edges, counter)
                    edges.add(GraphEdge(bodyNodeId, nodeId, label = "loop back"))
                    
                    // Exit loop
                    val exitId = "${methodName}_exit_${counter.next()}"
                    val exitNode = GraphNode(
                        id = exitId,
                        type = NodeType.EXPRESSION,
                        label = "exit loop",
                        code = "",
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(exitNode)
                    edges.add(GraphEdge(nodeId, exitId, label = "exit"))
                    currentNodeId = exitId
                }
                
                is ForEachStmt -> {
                    val foreachNode = GraphNode(
                        id = nodeId,
                        type = NodeType.FOREACH,
                        label = "for (${stmt.variable} : ${stmt.iterable})",
                        code = stmt.toString().take(100),
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(foreachNode)
                    edges.add(GraphEdge(currentNodeId, nodeId))
                    
                    // Loop body
                    val bodyNodeId = processStatement(stmt.body, nodeId, filePath, methodName, nodes, edges, counter)
                    edges.add(GraphEdge(bodyNodeId, nodeId, label = "loop back"))
                    
                    // Exit loop
                    val exitId = "${methodName}_exit_${counter.next()}"
                    val exitNode = GraphNode(
                        id = exitId,
                        type = NodeType.EXPRESSION,
                        label = "exit loop",
                        code = "",
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(exitNode)
                    edges.add(GraphEdge(nodeId, exitId, label = "exit"))
                    currentNodeId = exitId
                }
                
                is WhileStmt -> {
                    val whileNode = GraphNode(
                        id = nodeId,
                        type = NodeType.WHILE,
                        label = "while (${stmt.condition})",
                        code = stmt.toString().take(100),
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(whileNode)
                    edges.add(GraphEdge(currentNodeId, nodeId))
                    
                    // Loop body
                    val bodyNodeId = processStatement(stmt.body, nodeId, filePath, methodName, nodes, edges, counter)
                    edges.add(GraphEdge(bodyNodeId, nodeId, label = "loop back"))
                    
                    // Exit loop
                    val exitId = "${methodName}_exit_${counter.next()}"
                    val exitNode = GraphNode(
                        id = exitId,
                        type = NodeType.EXPRESSION,
                        label = "exit loop",
                        code = "",
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(exitNode)
                    edges.add(GraphEdge(nodeId, exitId, label = "exit"))
                    currentNodeId = exitId
                }
                
                is SwitchStmt -> {
                    val switchNode = GraphNode(
                        id = nodeId,
                        type = NodeType.SWITCH,
                        label = "switch (${stmt.selector})",
                        code = stmt.toString().take(100),
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(switchNode)
                    edges.add(GraphEdge(currentNodeId, nodeId))
                    
                    val caseExitIds = mutableListOf<String>()
                    stmt.entries.forEach { entry ->
                        val caseId = "${methodName}_case_${counter.next()}"
                        val caseNode = GraphNode(
                            id = caseId,
                            type = NodeType.CASE,
                            label = if (entry.labels.isNotEmpty()) "case ${entry.labels.joinToString()}" else "default",
                            code = entry.toString().take(50),
                            location = extractLocation(entry, filePath)
                        )
                        nodes.add(caseNode)
                        edges.add(GraphEdge(nodeId, caseId))
                        
                        val caseEndId = processBlockStatements(entry.statements, caseId, filePath, methodName, nodes, edges, counter)
                        caseExitIds.add(caseEndId)
                    }
                    
                    // Merge all case exits
                    val mergeId = "${methodName}_switch_merge_${counter.next()}"
                    val mergeNode = GraphNode(
                        id = mergeId,
                        type = NodeType.EXPRESSION,
                        label = "merge",
                        code = "",
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(mergeNode)
                    caseExitIds.forEach { exitId ->
                        edges.add(GraphEdge(exitId, mergeId))
                    }
                    currentNodeId = mergeId
                }
                
                is TryStmt -> {
                    val tryNode = GraphNode(
                        id = nodeId,
                        type = NodeType.TRY,
                        label = "try",
                        code = stmt.toString().take(100),
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(tryNode)
                    edges.add(GraphEdge(currentNodeId, nodeId))
                    
                    // Try block
                    val tryEndId = processBlockStatements(stmt.tryBlock.statements, nodeId, filePath, methodName, nodes, edges, counter)
                    
                    // Catch clauses
                    val catchExitIds = mutableListOf(tryEndId)
                    stmt.catchClauses.forEach { catchClause ->
                        val catchId = "${methodName}_catch_${counter.next()}"
                        val catchNode = GraphNode(
                            id = catchId,
                            type = NodeType.CATCH,
                            label = "catch (${catchClause.parameter})",
                            code = catchClause.toString().take(50),
                            location = extractLocation(catchClause, filePath)
                        )
                        nodes.add(catchNode)
                        edges.add(GraphEdge(nodeId, catchId))
                        
                        val catchEndId = processBlockStatements(catchClause.body.statements, catchId, filePath, methodName, nodes, edges, counter)
                        catchExitIds.add(catchEndId)
                    }
                    
                    // Finally block
                    stmt.finallyBlock.ifPresent { finallyBlock ->
                        val finallyId = "${methodName}_finally_${counter.next()}"
                        val finallyNode = GraphNode(
                            id = finallyId,
                            type = NodeType.FINALLY,
                            label = "finally",
                            code = "",
                            location = extractLocation(finallyBlock, filePath)
                        )
                        nodes.add(finallyNode)
                        catchExitIds.forEach { exitId ->
                            edges.add(GraphEdge(exitId, finallyId))
                        }
                        catchExitIds.clear()
                        catchExitIds.add(processBlockStatements(finallyBlock.statements, finallyId, filePath, methodName, nodes, edges, counter))
                    }
                    
                    // Merge point
                    val mergeId = "${methodName}_try_merge_${counter.next()}"
                    val mergeNode = GraphNode(
                        id = mergeId,
                        type = NodeType.EXPRESSION,
                        label = "merge",
                        code = "",
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(mergeNode)
                    catchExitIds.forEach { exitId ->
                        edges.add(GraphEdge(exitId, mergeId))
                    }
                    currentNodeId = mergeId
                }
                
                is ReturnStmt -> {
                    val returnNode = GraphNode(
                        id = nodeId,
                        type = NodeType.RETURN,
                        label = "return ${stmt.expression.map { it.toString() }.orElse("")}",
                        code = stmt.toString(),
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(returnNode)
                    edges.add(GraphEdge(currentNodeId, nodeId))
                    currentNodeId = nodeId
                }
                
                is ThrowStmt -> {
                    val throwNode = GraphNode(
                        id = nodeId,
                        type = NodeType.THROW,
                        label = "throw",
                        code = stmt.toString(),
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(throwNode)
                    edges.add(GraphEdge(currentNodeId, nodeId))
                    currentNodeId = nodeId
                }
                
                is BreakStmt -> {
                    val breakNode = GraphNode(
                        id = nodeId,
                        type = NodeType.BREAK,
                        label = "break",
                        code = stmt.toString(),
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(breakNode)
                    edges.add(GraphEdge(currentNodeId, nodeId))
                    currentNodeId = nodeId
                }
                
                is ContinueStmt -> {
                    val continueNode = GraphNode(
                        id = nodeId,
                        type = NodeType.CONTINUE,
                        label = "continue",
                        code = stmt.toString(),
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(continueNode)
                    edges.add(GraphEdge(currentNodeId, nodeId))
                    currentNodeId = nodeId
                }
                
                is BlockStmt -> {
                    currentNodeId = processBlockStatements(
                        stmt.statements,
                        currentNodeId,
                        filePath,
                        methodName,
                        nodes,
                        edges,
                        counter
                    )
                }
                
                else -> {
                    // Handle expressions, assignments, method calls
                    val exprNode = GraphNode(
                        id = nodeId,
                        type = if (stmt.toString().contains("(")) NodeType.METHOD_CALL else NodeType.EXPRESSION,
                        label = stmt.toString().take(50),
                        code = stmt.toString(),
                        location = extractLocation(stmt, filePath)
                    )
                    nodes.add(exprNode)
                    edges.add(GraphEdge(currentNodeId, nodeId))
                    currentNodeId = nodeId
                }
            }
        }
        
        return currentNodeId
    }
    
    private fun processStatement(
        stmt: Statement,
        parentNodeId: String,
        filePath: String,
        methodName: String,
        nodes: MutableList<GraphNode>,
        edges: MutableList<GraphEdge>,
        counter: NodeIdCounter
    ): String {
        return if (stmt is BlockStmt) {
            processBlockStatements(stmt.statements, parentNodeId, filePath, methodName, nodes, edges, counter)
        } else {
            processBlockStatements(listOf(stmt), parentNodeId, filePath, methodName, nodes, edges, counter)
        }
    }
    
    private fun generateNodeId(stmt: Statement, methodName: String, counter: NodeIdCounter): String {
        val type = when (stmt) {
            is IfStmt -> "if"
            is ForStmt -> "for"
            is ForEachStmt -> "foreach"
            is WhileStmt -> "while"
            is SwitchStmt -> "switch"
            is TryStmt -> "try"
            is ReturnStmt -> "return"
            is ThrowStmt -> "throw"
            is BreakStmt -> "break"
            is ContinueStmt -> "continue"
            else -> "stmt"
        }
        val range = stmt.range.orElse(null)
        return if (range != null) {
            "${methodName}_${type}_${range.begin.line}_${range.begin.column}"
        } else {
            "${methodName}_${type}_${counter.next()}"
        }
    }
    
    private fun extractLocation(node: com.github.javaparser.ast.Node, filePath: String): SourceLocation {
        val range = node.range.orElse(null) ?: return SourceLocation(filePath, 0, 0, 0, 0)
        return SourceLocation(
            filePath = filePath,
            startLine = range.begin.line,
            endLine = range.end.line,
            startColumn = range.begin.column,
            endColumn = range.end.column
        )
    }
    
    private fun calculateCyclomaticComplexity(method: MethodDeclaration): Int {
        var complexity = 1 // Base complexity
        
        method.accept(object : VoidVisitorAdapter<Void>() {
            override fun visit(n: IfStmt, arg: Void?) {
                complexity++
                super.visit(n, arg)
            }
            
            override fun visit(n: ForStmt, arg: Void?) {
                complexity++
                super.visit(n, arg)
            }
            
            override fun visit(n: ForEachStmt, arg: Void?) {
                complexity++
                super.visit(n, arg)
            }
            
            override fun visit(n: WhileStmt, arg: Void?) {
                complexity++
                super.visit(n, arg)
            }
            
            override fun visit(n: DoStmt, arg: Void?) {
                complexity++
                super.visit(n, arg)
            }
            
            override fun visit(n: SwitchEntry, arg: Void?) {
                if (n.labels.isNotEmpty()) complexity++
                super.visit(n, arg)
            }
            
            override fun visit(n: CatchClause, arg: Void?) {
                complexity++
                super.visit(n, arg)
            }
        }, null)
        
        return complexity
    }
    
    private fun calculateConstructorComplexity(constructor: ConstructorDeclaration): Int {
        var complexity = 1 // Base complexity
        
        constructor.accept(object : VoidVisitorAdapter<Void>() {
            override fun visit(n: IfStmt, arg: Void?) {
                complexity++
                super.visit(n, arg)
            }
            
            override fun visit(n: ForStmt, arg: Void?) {
                complexity++
                super.visit(n, arg)
            }
            
            override fun visit(n: ForEachStmt, arg: Void?) {
                complexity++
                super.visit(n, arg)
            }
            
            override fun visit(n: WhileStmt, arg: Void?) {
                complexity++
                super.visit(n, arg)
            }
            
            override fun visit(n: DoStmt, arg: Void?) {
                complexity++
                super.visit(n, arg)
            }
            
            override fun visit(n: SwitchEntry, arg: Void?) {
                if (n.labels.isNotEmpty()) complexity++
                super.visit(n, arg)
            }
            
            override fun visit(n: CatchClause, arg: Void?) {
                complexity++
                super.visit(n, arg)
            }
        }, null)
        
        return complexity
    }
    
    private class NodeIdCounter {
        private var count = 0
        fun next() = count++
    }
}
