package com.axenix.javablueprints.service

import com.axenix.javablueprints.model.*
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.CallableDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter

class GraphMutatorService {

    fun mutateGraph(
        cu: CompilationUnit,
        @Suppress("UNUSED_PARAMETER") filePath: String,
        mutation: GraphMutationRequest
    ): String {
        LexicalPreservingPrinter.setup(cu)

        val targetNode = findNodeById(cu, mutation.nodeId)
        val targetMethod = if (targetNode == null) findMethodByNodeId(cu, mutation.nodeId) else null

        when (mutation.operation) {
            MutationOperation.UPDATE -> {
                val newCode = mutation.newCode
                    ?: throw IllegalArgumentException("newCode is required for UPDATE")
                if (targetNode != null) {
                    val replacement = parseSingleStatement(newCode)
                    if (!targetNode.replace(replacement)) {
                        throw IllegalArgumentException("AST node '${mutation.nodeId}' cannot be replaced")
                    }
                } else if (targetMethod != null) {
                    try {
                        val newMethod = StaticJavaParser.parseMethodDeclaration(newCode)
                        targetMethod.replace(newMethod)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Invalid method syntax: ${e.message}")
                    }
                } else {
                    throw IllegalArgumentException("AST node '${mutation.nodeId}' was not found")
                }
            }

            MutationOperation.DELETE -> {
                if (targetNode != null) {
                    if (!targetNode.remove()) {
                        throw IllegalArgumentException("AST node '${mutation.nodeId}' cannot be deleted")
                    }
                } else if (targetMethod != null) {
                    if (!targetMethod.remove()) {
                        throw IllegalArgumentException("Method '${targetMethod.nameAsString}' cannot be deleted")
                    }
                } else {
                    throw IllegalArgumentException("AST node '${mutation.nodeId}' was not found")
                }
            }

            MutationOperation.ADD -> {
                val newCode = mutation.newCode
                    ?: throw IllegalArgumentException("newCode is required for ADD")
                val statement = parseSingleStatement(newCode)
                addStatement(cu, mutation.nodeId, targetNode, statement)
            }
        }

        return LexicalPreservingPrinter.print(cu)
    }

    private fun findNodeById(cu: CompilationUnit, nodeId: String): Statement? {
        val parts = nodeId.split('_')
        if (parts.size < 4) return null
        val line = parts[parts.size - 2].toIntOrNull() ?: return null
        val column = parts.last().toIntOrNull() ?: return null
        val type = parts[parts.size - 3]
        val callableName = parts.dropLast(3).joinToString("_")

        return cu.findAll(Statement::class.java).firstOrNull { statement ->
            if (statement is BlockStmt) return@firstOrNull false
            val range = statement.range.orElse(null) ?: return@firstOrNull false
            val callable = statement.findAncestor(CallableDeclaration::class.java).orElse(null)
                ?: return@firstOrNull false
            callable.nameAsString == callableName &&
                range.begin.line == line &&
                range.begin.column == column &&
                statementType(statement) == type
        }
    }

    private fun findMethodByNodeId(cu: CompilationUnit, nodeId: String): MethodDeclaration? {
        val methodName = Regex("^method_(.+)_start$").matchEntire(nodeId)?.groupValues?.get(1)
            ?: Regex("^method_(.+)_end$").matchEntire(nodeId)?.groupValues?.get(1)
            ?: nodeId.removePrefix("method_").removeSuffix("_start").removeSuffix("_end")
        return cu.findAll(MethodDeclaration::class.java).firstOrNull { it.nameAsString == methodName }
    }

    private fun addStatement(
        cu: CompilationUnit,
        nodeId: String,
        targetNode: Statement?,
        statement: Statement
    ) {
        val targetBlock = targetNode?.parentNode
            ?.filter { it is BlockStmt }
            ?.map { it as BlockStmt }
            ?.orElse(null)

        if (targetBlock != null) {
            val index = targetBlock.statements.indexOf(targetNode)
            targetBlock.addStatement(if (index >= 0) index + 1 else targetBlock.statements.size, statement)
            return
        }

        val methodName = Regex("^method_(.+)_start$").matchEntire(nodeId)?.groupValues?.get(1)
        val method = if (methodName != null) {
            cu.findAll(MethodDeclaration::class.java)
                .firstOrNull { it.nameAsString == methodName && it.body.isPresent }
        } else {
            null
        }

        val body = method?.body?.orElse(null)
            ?: throw IllegalArgumentException("ADD target '$nodeId' must be a statement or method node")
        body.addStatement(statement)
    }

    private fun parseSingleStatement(code: String): Statement {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("newCode must not be blank")
        return try {
            StaticJavaParser.parseStatement(trimmed)
        } catch (statementError: Exception) {
            // A block is a valid construct for palette operations and is parsed
            // separately because parseStatement expects a complete statement.
            try {
                StaticJavaParser.parseBlock(trimmed)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid Java statement: ${statementError.message}")
            }
        }
    }

    private fun statementType(statement: Statement): String = when (statement) {
        is com.github.javaparser.ast.stmt.IfStmt -> "if"
        is com.github.javaparser.ast.stmt.ForStmt -> "for"
        is com.github.javaparser.ast.stmt.ForEachStmt -> "foreach"
        is com.github.javaparser.ast.stmt.WhileStmt -> "while"
        is com.github.javaparser.ast.stmt.SwitchStmt -> "switch"
        is com.github.javaparser.ast.stmt.TryStmt -> "try"
        is com.github.javaparser.ast.stmt.ReturnStmt -> "return"
        is com.github.javaparser.ast.stmt.ThrowStmt -> "throw"
        is com.github.javaparser.ast.stmt.BreakStmt -> "break"
        is com.github.javaparser.ast.stmt.ContinueStmt -> "continue"
        else -> "stmt"
    }
}
