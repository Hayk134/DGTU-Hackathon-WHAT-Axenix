package com.axenix.javablueprints.service

import com.axenix.javablueprints.model.*
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.stmt.*
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import kotlin.math.min

class StaticAnalyzerService {
    
    fun analyzeCode(cu: CompilationUnit, filePath: String): AnalysisReport {
        val defects = mutableListOf<Defect>()
        val methods = cu.findAll(MethodDeclaration::class.java)
        
        var totalLines = 0
        var totalComplexity = 0
        var maxComplexity = 0
        val duplicatedBlocks = mutableSetOf<String>()
        
        methods.forEach { method ->
            val complexity = calculateComplexity(method)
            totalComplexity += complexity
            maxComplexity = maxOf(maxComplexity, complexity)
            
            val methodRange = method.range.orElse(null)
            val methodLines = if (methodRange != null) {
                methodRange.end.line - methodRange.begin.line + 1
            } else 0
            totalLines += methodLines
            
            // Detect long methods
            if (methodLines > 30) {
                defects.add(Defect(
                    id = "long_method_${method.nameAsString}",
                    type = DefectType.LONG_METHOD,
                    severity = Severity.MEDIUM,
                    location = extractLocation(method, filePath),
                    title = "Длинный метод: ${method.nameAsString}",
                    description = "Метод содержит $methodLines строк. Рекомендуется разбить на более мелкие методы для улучшения читаемости.",
                    codeFragment = method.declarationAsString
                ))
            }
            
            // Detect high complexity
            if (complexity > 8) {
                defects.add(Defect(
                    id = "high_complexity_${method.nameAsString}",
                    type = DefectType.NESTED_COMPLEXITY,
                    severity = Severity.HIGH,
                    location = extractLocation(method, filePath),
                    title = "Высокая цикломатическая сложность: ${method.nameAsString}",
                    description = "Цикломатическая сложность метода: $complexity (порог: 8). Метод трудно тестировать и поддерживать.",
                    complexity = "O($complexity)",
                    codeFragment = method.declarationAsString
                ))
            }
            
            // Detect nested loops (O(n²) and higher)
            detectNestedLoops(method, filePath, defects)
            
            // Detect inefficient operations inside loops
            detectInefficientLoopOperations(method, filePath, defects)
        }
        
        // Detect code duplication
        detectDuplicatedLogic(methods, filePath, defects, duplicatedBlocks)
        
        val metrics = CodeMetrics(
            totalLines = totalLines,
            totalMethods = methods.size,
            averageComplexity = if (methods.isNotEmpty()) totalComplexity.toDouble() / methods.size else 0.0,
            maxComplexity = maxComplexity,
            duplicatedBlocks = duplicatedBlocks.size
        )
        
        val recommendations = generateRecommendations(defects)
        
        return AnalysisReport(defects, metrics, recommendations)
    }
    
    private fun detectNestedLoops(
        method: MethodDeclaration,
        filePath: String,
        defects: MutableList<Defect>
    ) {
        val nestedLoopVisitor = object : VoidVisitorAdapter<Int>() {
            override fun visit(n: ForStmt, depth: Int) {
                if (depth >= 1) {
                    defects.add(Defect(
                        id = "nested_loop_${method.nameAsString}_${n.hashCode()}",
                        type = DefectType.NESTED_COMPLEXITY,
                        severity = Severity.HIGH,
                        location = extractLocation(n, filePath),
                        title = "Вложенный цикл O(n²) в ${method.nameAsString}",
                        description = "Обнаружен вложенный цикл глубины ${depth + 1}. Сложность: O(n^${depth + 1}). Может привести к значительному замедлению на больших данных.",
                        complexity = "O(n^${depth + 1})",
                        codeFragment = n.toString().take(150)
                    ))
                }
                super.visit(n, depth + 1)
            }
            
            override fun visit(n: ForEachStmt, depth: Int) {
                if (depth >= 1) {
                    defects.add(Defect(
                        id = "nested_foreach_${method.nameAsString}_${n.hashCode()}",
                        type = DefectType.NESTED_COMPLEXITY,
                        severity = Severity.HIGH,
                        location = extractLocation(n, filePath),
                        title = "Вложенный foreach O(n²) в ${method.nameAsString}",
                        description = "Обнаружен вложенный foreach глубины ${depth + 1}. Сложность: O(n^${depth + 1}). Рассмотрите использование HashMap для оптимизации.",
                        complexity = "O(n^${depth + 1})",
                        codeFragment = n.toString().take(150)
                    ))
                }
                super.visit(n, depth + 1)
            }
            
            override fun visit(n: WhileStmt, depth: Int) {
                if (depth >= 1) {
                    defects.add(Defect(
                        id = "nested_while_${method.nameAsString}_${n.hashCode()}",
                        type = DefectType.NESTED_COMPLEXITY,
                        severity = Severity.HIGH,
                        location = extractLocation(n, filePath),
                        title = "Вложенный while O(n²) в ${method.nameAsString}",
                        description = "Обнаружен вложенный while глубины ${depth + 1}. Сложность: O(n^${depth + 1}).",
                        complexity = "O(n^${depth + 1})",
                        codeFragment = n.toString().take(150)
                    ))
                }
                super.visit(n, depth + 1)
            }
        }
        
        method.accept(nestedLoopVisitor, 0)
    }
    
    private fun detectInefficientLoopOperations(
        method: MethodDeclaration,
        filePath: String,
        defects: MutableList<Defect>
    ) {
        val loopVisitor = object : VoidVisitorAdapter<Void>() {
            private val inefficientOperations = listOf("contains", "indexOf", "remove")
            
            override fun visit(n: ForStmt, arg: Void?) {
                checkLoopBody(n.body, n)
                super.visit(n, arg)
            }
            
            override fun visit(n: ForEachStmt, arg: Void?) {
                checkLoopBody(n.body, n)
                super.visit(n, arg)
            }
            
            override fun visit(n: WhileStmt, arg: Void?) {
                checkLoopBody(n.body, n)
                super.visit(n, arg)
            }
            
            private fun checkLoopBody(body: Statement, loopStmt: Statement) {
                body.findAll(MethodCallExpr::class.java).forEach { call ->
                    if (call.nameAsString in inefficientOperations) {
                        defects.add(Defect(
                            id = "inefficient_op_${method.nameAsString}_${call.hashCode()}",
                            type = DefectType.INEFFICIENT_LOOP,
                            severity = Severity.MEDIUM,
                            location = extractLocation(call, filePath),
                            title = "Неэффективная операция ${call.nameAsString} в цикле",
                            description = "Операция ${call.nameAsString}() имеет сложность O(n) и вызывается внутри цикла. Итоговая сложность O(n²). Используйте HashSet для ускорения.",
                            complexity = "O(n²)",
                            codeFragment = call.toString()
                        ))
                    }
                }
                
                // Detect string concatenation in loops
                if (body.toString().contains(" += ") && body.toString().contains("\"")) {
                    defects.add(Defect(
                        id = "string_concat_${method.nameAsString}_${loopStmt.hashCode()}",
                        type = DefectType.INEFFICIENT_LOOP,
                        severity = Severity.MEDIUM,
                        location = extractLocation(loopStmt, filePath),
                        title = "Конкатенация строк в цикле",
                        description = "Обнаружена конкатенация строк с помощью оператора += в цикле. Используйте StringBuilder для оптимизации.",
                        codeFragment = loopStmt.toString().take(100)
                    ))
                }
            }
        }
        
        method.accept(loopVisitor, null)
    }
    
    private fun detectDuplicatedLogic(
        methods: List<MethodDeclaration>,
        filePath: String,
        defects: MutableList<Defect>,
        duplicatedBlocks: MutableSet<String>
    ) {
        val methodHashes = mutableMapOf<String, MutableList<MethodDeclaration>>()
        
        methods.forEach { method ->
            val normalized = normalizeMethod(method)
            if (normalized.isNotBlank()) {
                methodHashes.computeIfAbsent(normalized) { mutableListOf() }.add(method)
            }
        }
        
        methodHashes.forEach { (hash, duplicates) ->
            if (duplicates.size > 1) {
                duplicatedBlocks.add(hash)
                duplicates.forEach { method ->
                    defects.add(Defect(
                        id = "duplicated_${method.nameAsString}",
                        type = DefectType.DUPLICATED_LOGIC,
                        severity = Severity.LOW,
                        location = extractLocation(method, filePath),
                        title = "Дублированная логика: ${method.nameAsString}",
                        description = "Найдено ${duplicates.size} методов с похожей структурой. Рассмотрите извлечение общей логики в отдельный метод.",
                        codeFragment = method.declarationAsString
                    ))
                }
            }
        }
    }
    
    private fun normalizeMethod(method: MethodDeclaration): String {
        // Only consider non-trivial methods with at least 4 statements
        return method.body.map { body ->
            if (body.statements.size < 4) ""
            else body.statements.joinToString("|") { stmt ->
                stmt.javaClass.simpleName
            }
        }.orElse("")
    }
    
    private fun calculateComplexity(method: MethodDeclaration): Int {
        var complexity = 1
        
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
    
    private fun generateRecommendations(defects: List<Defect>): List<Recommendation> {
        return defects.map { defect ->
            Recommendation(
                defectId = defect.id,
                title = when (defect.type) {
                    DefectType.NESTED_COMPLEXITY -> "Оптимизация вложенных циклов"
                    DefectType.LONG_METHOD -> "Декомпозиция метода"
                    DefectType.DUPLICATED_LOGIC -> "Извлечение общей логики"
                    DefectType.INEFFICIENT_LOOP -> "Использование эффективных структур данных"
                    DefectType.HIDDEN_COMPLEXITY -> "Упрощение логики"
                },
                description = when (defect.type) {
                    DefectType.NESTED_COMPLEXITY -> "Замените вложенные циклы на HashMap для индексации элементов по ключу, снизив сложность с O(n²) до O(n)."
                    DefectType.LONG_METHOD -> "Разбейте метод на несколько небольших методов с четкой ответственностью."
                    DefectType.DUPLICATED_LOGIC -> "Создайте вспомогательный метод для общей логики и вызывайте его из обоих мест."
                    DefectType.INEFFICIENT_LOOP -> "Используйте HashSet вместо List для операций поиска, или StringBuilder для конкатенации строк."
                    DefectType.HIDDEN_COMPLEXITY -> "Упростите условия и извлеките сложные выражения в переменные с понятными именами."
                },
                expectedBenefit = when (defect.type) {
                    DefectType.NESTED_COMPLEXITY -> "Ускорение выполнения в 10-1000 раз на больших объемах данных"
                    DefectType.LONG_METHOD -> "Повышение читаемости и тестируемости кода"
                    DefectType.DUPLICATED_LOGIC -> "Снижение объема кода и упрощение сопровождения"
                    DefectType.INEFFICIENT_LOOP -> "Ускорение выполнения в 10-100 раз"
                    DefectType.HIDDEN_COMPLEXITY -> "Улучшение понимания и сопровождения кода"
                }
            )
        }
    }
}
