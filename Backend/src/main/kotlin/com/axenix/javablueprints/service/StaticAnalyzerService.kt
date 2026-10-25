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
                    type = DefectType.HIDDEN_COMPLEXITY,
                    severity = Severity.HIGH,
                    location = extractLocation(method, filePath),
                    title = "Высокая цикломатическая сложность: ${method.nameAsString}",
                    description = "Цикломатическая сложность метода: $complexity (порог: 8). Рекомендуется декомпозировать условия и применить Guard Clauses.",
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
                    val code = n.toString()
                    val isSorting = code.contains("swap") || code.contains("tmp") || code.contains("temp") ||
                            code.contains("compareTo") || Regex("""\w+\[.*?\]\s*[><]\s*\w+\[.*?\]""").containsMatchIn(code)
                    val isDuplicateCheck = (code.contains("==") || code.contains(".equals(")) &&
                            (code.contains("return true") || code.contains("return false") || code.contains("break"))

                    when {
                        isSorting -> defects.add(Defect(
                            id = "quadratic_sort_${method.nameAsString}_${n.hashCode()}",
                            type = DefectType.NESTED_COMPLEXITY,
                            severity = Severity.HIGH,
                            location = extractLocation(n, filePath),
                            title = "Квадратичная сортировка O(n²) в ${method.nameAsString}",
                            description = "Обнаружена квадратичная сортировка. Рекомендуется использовать Arrays.sort() или Collections.sort() с гарантией O(n log n).",
                            complexity = "O(n²)",
                            codeFragment = n.toString().take(150)
                        ))
                        isDuplicateCheck -> defects.add(Defect(
                            id = "duplicate_search_${method.nameAsString}_${n.hashCode()}",
                            type = DefectType.NESTED_COMPLEXITY,
                            severity = Severity.HIGH,
                            location = extractLocation(n, filePath),
                            title = "Квадратичный поиск дубликатов в ${method.nameAsString}",
                            description = "Вложенный цикл проверки совпадений. Рекомендуется однопроходный поиск через HashSet.add() за O(n).",
                            complexity = "O(n²)",
                            codeFragment = n.toString().take(150)
                        ))
                        else -> defects.add(Defect(
                            id = "nested_loop_${method.nameAsString}_${n.hashCode()}",
                            type = DefectType.NESTED_COMPLEXITY,
                            severity = Severity.HIGH,
                            location = extractLocation(n, filePath),
                            title = "Вложенный цикл O(n²) в ${method.nameAsString}",
                            description = "Обнаружен вложенный цикл глубины ${depth + 1}. Рекомендуется использовать HashMap/HashSet для индексации за O(1).",
                            complexity = "O(n^${depth + 1})",
                            codeFragment = n.toString().take(150)
                        ))
                    }
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
                        val scopeStr = call.scope.map { it.toString().trim() }.orElse("")
                        
                        // Check if the target is a String or Set/Map to avoid false positives
                        val isStringTarget = method.findAll(com.github.javaparser.ast.body.VariableDeclarator::class.java).any { v ->
                            v.nameAsString == scopeStr && v.typeAsString.contains("String")
                        } || method.parameters.any { p ->
                            p.nameAsString == scopeStr && p.typeAsString.contains("String")
                        } || (call.nameAsString == "contains" && call.arguments.any { it is com.github.javaparser.ast.expr.StringLiteralExpr })
                        
                        val isSetOrMapTarget = method.findAll(com.github.javaparser.ast.body.VariableDeclarator::class.java).any { v ->
                            v.nameAsString == scopeStr && (v.typeAsString.contains("Set") || v.typeAsString.contains("Map"))
                        } || method.parameters.any { p ->
                            p.nameAsString == scopeStr && (p.typeAsString.contains("Set") || p.typeAsString.contains("Map"))
                        }

                        if (!isStringTarget && !isSetOrMapTarget) {
                            defects.add(Defect(
                                id = "inefficient_op_${method.nameAsString}_${call.hashCode()}",
                                type = DefectType.INEFFICIENT_LOOP,
                                severity = Severity.MEDIUM,
                                location = extractLocation(call, filePath),
                                title = "Неэффективная операция ${call.nameAsString} в цикле",
                                description = "Операция ${call.nameAsString}() имеет сложность O(n) для списков и вызывается внутри цикла. Итоговая сложность O(n²). Используйте HashSet для ускорения.",
                                complexity = "O(n²)",
                                codeFragment = call.toString()
                            ))
                        }
                    }
                }
                
                // Detect string concatenation in loops (+= or var = var + ...)
                val bodyStr = body.toString()
                val hasConcatPlus = bodyStr.contains(" += ") || Regex("""\b(\w+)\s*=\s*\1\s*\+""").containsMatchIn(bodyStr)
                if (hasConcatPlus) {
                    defects.add(Defect(
                        id = "string_concat_${method.nameAsString}_${loopStmt.hashCode()}",
                        type = DefectType.INEFFICIENT_LOOP,
                        severity = Severity.MEDIUM,
                        location = extractLocation(loopStmt, filePath),
                        title = "Конкатенация строк в цикле: ${method.nameAsString}",
                        description = "Обнаружено накопление строк оператором '+' внутри цикла. Рекомендуется использовать StringBuilder для предотвращения избыточных аллокаций памяти.",
                        complexity = "O(n²)",
                        codeFragment = loopStmt.toString().take(100)
                    ))
                }

                // Detect manual array clear/fill in loop
                if (Regex("""\b(\w+)\[.*?\]\s*=\s*(?:0(?:\.0)?|false|null)\s*;""").containsMatchIn(bodyStr)) {
                    defects.add(Defect(
                        id = "array_fill_${method.nameAsString}_${loopStmt.hashCode()}",
                        type = DefectType.INEFFICIENT_LOOP,
                        severity = Severity.LOW,
                        location = extractLocation(loopStmt, filePath),
                        title = "Поэлементная очистка массива в ${method.nameAsString}",
                        description = "Ручной цикл обнуления/очистки массива. Рекомендуется использовать JVM-интринсик Arrays.fill().",
                        complexity = "O(n)",
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
        val result = mutableListOf<Recommendation>()
        val seenSignatures = mutableSetOf<String>()

        for (defect in defects) {
            val (title, description, benefit) = when {
                defect.id.startsWith("quadratic_sort_") -> Triple(
                    "Замена квадратичной сортировки на O(n log n)",
                    "Замените ручную сортировку на стандартный алгоритм Arrays.sort() или Collections.sort() (Dual-Pivot Quicksort / TimSort).",
                    "O(n²) → O(n log n), ускорение до 1000x"
                )
                defect.id.startsWith("duplicate_search_") -> Triple(
                    "Однопроходный поиск дубликатов через HashSet",
                    "Замените квадратичный перебор пар элементов на однопроходную проверку с использованием Set.add().",
                    "O(n²) → O(n), устранение вложенного цикла"
                )
                defect.id.startsWith("string_concat_") -> Triple(
                    "Оптимизация конкатенации строк через StringBuilder",
                    "Замените оператор '+' в цикле на StringBuilder для исключения создания промежуточных String-объектов.",
                    "Экономия памяти до 95%, снижение нагрузки на GC"
                )
                defect.id.startsWith("array_fill_") -> Triple(
                    "Векторное заполнение массива через Arrays.fill",
                    "Замените поэлементную очистку массива на векторный интринсик JVM Arrays.fill (аппаратный memset/SIMD).",
                    "O(n) → O(1) аппаратная инструкция"
                )
                defect.id.startsWith("inefficient_op_") -> Triple(
                    "Замена линейного поиска List.contains() на Set",
                    "Вызов contains()/indexOf() на списке внутри цикла дает сложность O(n×m). Использование HashSet обеспечивает поиск за O(1).",
                    "O(n) → O(1) на каждую операцию поиска"
                )
                defect.id.startsWith("nested_foreach_") -> Triple(
                    "Группировка связанных коллекций через HashMap",
                    "Замените двойной перебор ForEach на группировку элементов по ключу через Map<K, List<V>>.",
                    "O(n²) → O(n), ускорение на больших объемах данных"
                )
                defect.id.startsWith("nested_loop_") || defect.id.startsWith("nested_while_") -> Triple(
                    "Индексация вложенного поиска через HashSet / HashMap",
                    "Замените квадратичный перебор на предварительно построенный индекс HashMap/HashSet для сопоставления за O(1).",
                    "Снижение алгоритмической сложности с O(n²) до O(n)"
                )
                defect.type == DefectType.LONG_METHOD -> Triple(
                    "Декомпозиция метода по Single Responsibility",
                    "Разбейте объемный метод на короткие сфокусированные функции с единой ответственностью.",
                    "Повышение читаемости и тестируемости кода"
                )
                defect.type == DefectType.HIDDEN_COMPLEXITY -> Triple(
                    "Снижение цикломатической сложности через Guard Clauses",
                    "Упростите структуру условий метода, применив ранние возвраты (Guard Clauses) и разбив сложные предикаты.",
                    "Снижение сложности до < 8, устранение скрытых дефектов"
                )
                defect.type == DefectType.DUPLICATED_LOGIC -> Triple(
                    "Устранение дублирования кода (DRY)",
                    "Вынесите повторяющиеся фрагменты логики в общий переиспользуемый метод.",
                    "Сокращение дублирования и единая точка поддержки"
                )
                else -> Triple(
                    "Оптимизация алгоритма и структур данных",
                    defect.description,
                    "Повышение производительности и масштабируемости"
                )
            }

            val methodNameMatch = Regex("""^(?:nested_loop|quadratic_sort|duplicate_search|string_concat|array_fill|inefficient_op|nested_foreach|nested_while|high_complexity|long_method|duplicated)_([a-zA-Z0-9]+?)(?:_(-?\d+))?$""").find(defect.id)
            val rawName = methodNameMatch?.groupValues?.get(1)?.trimEnd('_')
            val methodName = if (!rawName.isNullOrBlank()) rawName else null

            val fullTitle = if (methodName != null) "$title в $methodName" else title
            val dedupKey = "$title:$methodName"

            if (seenSignatures.add(dedupKey)) {
                result.add(
                    Recommendation(
                        defectId = defect.id,
                        title = fullTitle,
                        description = description,
                        expectedBenefit = benefit
                    )
                )
            }
        }

        return result
    }
}
