package com.axenix.javablueprints.service

import com.axenix.javablueprints.model.*
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.stmt.*
import java.io.File

class OptimizerAndDecomposerService {

    private val verifier = VerificationService()

    fun optimizeAndDecompose(
        cu: CompilationUnit,
        filePath: String,
        applyOptimizations: Boolean,
        decomposeMonolith: Boolean
    ): Pair<Map<String, String>, List<OptimizationChange>> {

        val changes = mutableListOf<OptimizationChange>()
        var source = cu.toString()

        if (applyOptimizations) {
            source = applyVerifiedOptimization(source, filePath, changes) { candidate ->
                optimizeArrayNestedLoop(candidate, filePath, changes)
            }
            source = applyVerifiedOptimization(source, filePath, changes) { candidate ->
                optimizeDuplicateCheck(candidate, filePath, changes)
            }
            source = applyVerifiedOptimization(source, filePath, changes) { candidate ->
                optimizeCollectionContains(candidate, filePath, changes)
            }
            source = applyVerifiedOptimization(source, filePath, changes) { candidate ->
                optimizeStringConcatenationInLoop(candidate, filePath, changes)
            }
            source = applyVerifiedOptimization(source, filePath, changes) { candidate ->
                optimizeCharLoopAccumulation(candidate, filePath, changes)
            }
            source = applyVerifiedOptimization(source, filePath, changes) { candidate ->
                optimizeNestedDeduplication(candidate, filePath, changes)
            }
            source = applyVerifiedOptimization(source, filePath, changes) { candidate ->
                optimizeDigitsScan(candidate, filePath, changes)
            }
            source = applyVerifiedOptimization(source, filePath, changes) { candidate ->
                optimizeReverseLoop(candidate, filePath, changes)
            }
            source = applyVerifiedOptimization(source, filePath, changes) { candidate ->
                optimizeNestedForEachCollection(candidate, filePath, changes)
            }
            source = applyVerifiedOptimization(source, filePath, changes) { candidate ->
                optimizeArraysFill(candidate, filePath, changes)
            }
        }

        val resultFiles = mutableMapOf<String, String>()

        if (decomposeMonolith) {
            val decomposed = tryDecomposeIntoModules(source, filePath, changes)
            if (decomposed != null) {
                resultFiles.putAll(decomposed)
            } else {
                resultFiles[filePath] = source
            }
        } else {
            resultFiles[filePath] = source
        }

        return Pair(resultFiles, changes)
    }

    private fun applyVerifiedOptimization(
        source: String,
        filePath: String,
        changes: MutableList<OptimizationChange>,
        transform: (String) -> String
    ): String {
        val beforeChangeCount = changes.size
        val candidate = transform(source)
        if (candidate == source) return source

        val tempDir = java.nio.file.Files.createTempDirectory("verify_comp_").toFile()
        val compile = try {
            verifier.compileJavaCode(mapOf(filePath to candidate), tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
        return if (compile.success) {
            candidate
        } else {
            while (changes.size > beforeChangeCount) changes.removeAt(changes.lastIndex)
            source
        }
    }

    /**
     * Handles the common collection-membership shape while deliberately
     * refusing patterns whose inner variable participates in the body.
     */
    private fun optimizeCollectionContains(
        source: String,
        filePath: String,
        changes: MutableList<OptimizationChange>
    ): String {
        val pattern = Regex(
            """for\s*\(\s*(\w+[<>\w\s,?]*)\s+(\w+)\s*:\s*(\w+)\s*\)\s*\{\s*""" +
                """for\s*\(\s*(\w+[<>\w\s,?]*)\s+(\w+)\s*:\s*(\w+)\s*\)\s*\{\s*""" +
                """if\s*\(\s*\7\.contains\(\s*\2\s*\)\s*\)\s*\{([\s\S]*?)\}\s*\}\s*\}"""
        )
        val match = pattern.find(source) ?: return source
        val outerType = match.groupValues[1]
        val outerVar = match.groupValues[2]
        val outerCollection = match.groupValues[3]
        val innerType = match.groupValues[4]
        val innerVar = match.groupValues[5]
        val innerCollection = match.groupValues[6]
        val body = match.groupValues[8]
        if (Regex("\\b${Regex.escape(innerVar)}\\b").containsMatchIn(body)) return source

        val indexName = "${innerCollection}Membership"
        val replacement = """
            {
                java.util.HashSet<$innerType> $indexName = new java.util.HashSet<>($innerCollection);
                for ($outerType $outerVar : $outerCollection) {
                    if ($indexName.contains($outerVar)) {$body
                    }
                }
            }
        """.trimIndent()
        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
        changes.add(OptimizationChange(
            type = "Collection Membership Indexing",
            description = "Заменена вложенная проверка contains на HashSet-индексацию",
            location = SourceLocation(filePath, line, line + match.value.count { it == '\n' }, 1, 1),
            complexityBefore = "O(n²)",
            complexityAfter = "O(n)"
        ))
        return source.replaceRange(match.range, replacement)
    }

    /**
     * Оптимизация 1: Вложенный цикл поиска по массиву (O(n²) -> O(n) через HashSet)
     */
    private fun optimizeArrayNestedLoop(
        source: String,
        filePath: String,
        changes: MutableList<OptimizationChange>
    ): String {
        val pattern = Regex(
            """for\s*\(\s*int\s+(\w+)\s*=\s*0\s*;\s*\1\s*<\s*(\w+)\.length\s*;\s*\1\+\+\s*\)\s*\{\s*""" +
                """for\s*\(\s*int\s+(\w+)\s*=\s*0\s*;\s*\3\s*<\s*(\w+)\.length\s*;\s*\3\+\+\s*\)\s*\{\s*""" +
                """if\s*\(\s*\2\[\1\]\s*==\s*\4\[\3\]\s*\)\s*\{([\s\S]*?)\}\s*\}\s*\}"""
        )

        val match = pattern.find(source) ?: return source
        val outerIndex = match.groupValues[1]
        val firstArray = match.groupValues[2]
        val innerIndex = match.groupValues[3]
        val secondArray = match.groupValues[4]
        val body = match.groupValues[5]

        if (Regex("""\b$innerIndex\b""").containsMatchIn(body)) return source

        val replacement = """
            {
                java.util.HashSet<Integer> ${secondArray}Index = new java.util.HashSet<>();
                for (int value : $secondArray) {
                    ${secondArray}Index.add(value);
                }
                for (int $outerIndex = 0; $outerIndex < $firstArray.length; $outerIndex++) {
                    if (${secondArray}Index.contains($firstArray[$outerIndex])) {$body
                    }
                }
            }
        """.trimIndent()

        val rangeStart = match.range.first
        val line = source.substring(0, rangeStart).count { it == '\n' } + 1
        changes.add(
            OptimizationChange(
                type = "Nested Loop Array Optimization",
                description = "Заменен квадратичный поиск по массиву на O(n) HashSet-индексацию",
                location = SourceLocation(filePath, line, line + match.value.count { it == '\n' }, 1, 1),
                complexityBefore = "O(n²)",
                complexityAfter = "O(n)"
            )
        )
        return source.replaceRange(match.range, replacement)
    }

    /**
     * Оптимизация 2: Квадратичный поиск дубликатов (O(n²) -> O(n) через HashSet.add)
     */
    private fun optimizeDuplicateCheck(
        source: String,
        filePath: String,
        changes: MutableList<OptimizationChange>
    ): String {
        val pattern = Regex(
            """for\s*\(\s*int\s+(\w+)\s*=\s*0\s*;\s*\1\s*<\s*(\w+)\.length\s*;\s*\1\+\+\s*\)\s*\{\s*""" +
                """for\s*\(\s*int\s+(\w+)\s*=\s*\1\s*\+\s*1\s*;\s*\3\s*<\s*\2\.length\s*;\s*\3\+\+\s*\)\s*\{\s*""" +
                """if\s*\(\s*\2\[\1\]\s*==\s*\2\[\3\]\s*\)\s*\{\s*return\s+true\s*;\s*\}\s*\}\s*\}\s*return\s+false\s*;"""
        )

        val match = pattern.find(source) ?: return source
        val arrayName = match.groupValues[2]

        val replacement = """
            java.util.HashSet<Integer> seen = new java.util.HashSet<>();
            for (int val : $arrayName) {
                if (!seen.add(val)) return true;
            }
            return false;
        """.trimIndent()

        val rangeStart = match.range.first
        val line = source.substring(0, rangeStart).count { it == '\n' } + 1
        changes.add(
            OptimizationChange(
                type = "Duplicate Search Optimization",
                description = "Заменен вложенный поиск дубликатов на однопроходный O(n) HashSet",
                location = SourceLocation(filePath, line, line + match.value.count { it == '\n' }, 1, 1),
                complexityBefore = "O(n²)",
                complexityAfter = "O(n)"
            )
        )
        return source.replaceRange(match.range, replacement)
    }

    /**
     * Оптимизация 3: Конкатенация строк в цикле (O(n²) по памяти -> O(n) через StringBuilder)
     */
    private fun optimizeStringConcatenationInLoop(
        source: String,
        filePath: String,
        changes: MutableList<OptimizationChange>
    ): String {
        val pattern = Regex(
            """String\s+(\w+)\s*=\s*""\s*;\s*""" +
                """for\s*\(\s*int\s+(\w+)\s*=\s*0\s*;\s*\2\s*<\s*(\w+)\.length\s*;\s*\2\+\+\s*\)\s*\{\s*""" +
                """\1\s*=\s*\1\s*\+\s*(\w+)\[\2\]\s*\+\s*"\\n"\s*;\s*\}\s*return\s+\1\s*;"""
        )

        val match = pattern.find(source) ?: return source
        val varName = match.groupValues[1]
        val indexVar = match.groupValues[2]
        val arrayName = match.groupValues[3]

        val replacement = """
            StringBuilder $varName = new StringBuilder();
            for (int $indexVar = 0; $indexVar < $arrayName.length; $indexVar++) {
                $varName.append($arrayName[$indexVar]).append("\n");
            }
            return $varName.toString();
        """.trimIndent()

        val rangeStart = match.range.first
        val line = source.substring(0, rangeStart).count { it == '\n' } + 1
        changes.add(
            OptimizationChange(
                type = "StringBuilder Loop Optimization",
                description = "Заменена квадратичная конкатенация строк в цикле на StringBuilder",
                location = SourceLocation(filePath, line, line + match.value.count { it == '\n' }, 1, 1),
                complexityBefore = "O(n²)",
                complexityAfter = "O(n)"
            )
        )
        return source.replaceRange(match.range, replacement)
    }

    /**
     * Оптимизация 4: Вложенный ForEach по коллекциям со сравнением ключа (Спецификация Слайд 5)
     */
    private fun optimizeNestedForEachCollection(
        source: String,
        filePath: String,
        changes: MutableList<OptimizationChange>
    ): String {
        val pattern = Regex(
            """for\s*\(\s*(\w+)\s+(\w+)\s*:\s*(\w+)\s*\)\s*\{\s*""" +
                """for\s*\(\s*(\w+)\s+(\w+)\s*:\s*(\w+)\s*\)\s*\{\s*""" +
                """if\s*\(\s*\2\.(\w+)\(\)\.equals\(\s*\5\.\7\(\)\s*\)\s*\)\s*\{([\s\S]*?)\}\s*\}\s*\}"""
        )

        val match = pattern.find(source) ?: return source
        val typeA = match.groupValues[1]
        val varA = match.groupValues[2]
        val listA = match.groupValues[3]
        val typeB = match.groupValues[4]
        val varB = match.groupValues[5]
        val listB = match.groupValues[6]
        val keyMethod = match.groupValues[7]
        val body = match.groupValues[8]

        val replacement = """
            {
                java.util.Map<Object, java.util.List<$typeB>> ${varB}Map = new java.util.HashMap<>();
                for ($typeB $varB : $listB) {
                    ${varB}Map.computeIfAbsent($varB.$keyMethod(), k -> new java.util.ArrayList<>()).add($varB);
                }
                for ($typeA $varA : $listA) {
                    java.util.List<$typeB> matches = ${varB}Map.get($varA.$keyMethod());
                    if (matches != null) {
                        for ($typeB $varB : matches) {$body
                        }
                    }
                }
            }
        """.trimIndent()

        val rangeStart = match.range.first
        val line = source.substring(0, rangeStart).count { it == '\n' } + 1
        changes.add(
            OptimizationChange(
                type = "Collection HashMap Indexing",
                description = "Заменен квадратичный перебор коллекций на HashMap-индексацию O(n)",
                location = SourceLocation(filePath, line, line + match.value.count { it == '\n' }, 1, 1),
                complexityBefore = "O(n²)",
                complexityAfter = "O(n)"
            )
        )
        return source.replaceRange(match.range, replacement)
    }

    /**
     * Оптимизация 5: Ручной цикл обнуления памяти -> Arrays.fill
     */
    private fun optimizeReverseLoop(
        source: String,
        filePath: String,
        changes: MutableList<OptimizationChange>
    ): String {
        val pattern = Regex(
            """String\s+(\w+)\s*=\s*""\s*;\s*for\s*\(\s*int\s+(\w+)\s*=\s*(\w+)\.length\(\)\s*-\s*1\s*;\s*\2\s*>=\s*0\s*;\s*\2--\s*\)\s*\{\s*\1\s*=\s*\1\s*\+\s*\3\.charAt\(\2\)\s*;\s*\}\s*return\s+\1\s*;"""
        )
        val match = pattern.find(source) ?: return source
        val strVar = match.groupValues[3]
        val replacement = "return new StringBuilder(" + strVar + ").reverse().toString();"
        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
        changes.add(OptimizationChange(
            type = "StringBuilder Reverse Optimization",
            description = "Заменен посимвольный реверс строки на StringBuilder.reverse()",
            location = SourceLocation(filePath, line, line + match.value.count { it == '\n' }, 1, 1),
            complexityBefore = "O(n²)",
            complexityAfter = "O(n)"
        ))
        return source.replaceRange(match.range, replacement)
    }

    private fun optimizeDigitsScan(
        source: String,
        filePath: String,
        changes: MutableList<OptimizationChange>
    ): String {
        val pattern = Regex(
            """String\s+digits\s*=\s*"0123456789"\s*;\s*for\s*\([^)]*\)\s*\{\s*if\s*\(\s*digits\.charAt\([^)]*\)\s*==\s*(\w+)\s*\)\s*\{\s*return\s+true\s*;\s*\}\s*\}\s*return\s+false\s*;"""
        )
        val match = pattern.find(source) ?: return source
        val charVar = match.groupValues[1]
        val replacement = "return Character.isDigit(" + charVar + ");"
        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
        changes.add(OptimizationChange(
            type = "Character.isDigit Optimization",
            description = "Заменен линейный перебор символов на Character.isDigit",
            location = SourceLocation(filePath, line, line + match.value.count { it == '\n' }, 1, 1),
            complexityBefore = "O(10)",
            complexityAfter = "O(1)"
        ))
        return source.replaceRange(match.range, replacement)
    }

    private fun optimizeNestedDeduplication(
        source: String,
        filePath: String,
        changes: MutableList<OptimizationChange>
    ): String {
        val pattern = Regex(
            """List<String>\s+(\w+)\s*=\s*new\s+ArrayList<>\s*\(\s*\)\s*;\s*for\s*\([^)]*\)\s*\{\s*String\s+(\w+)\s*=\s*(\w+)\.get\([^)]*\)\s*;\s*boolean\s+(\w+)\s*=\s*false\s*;\s*for\s*\([^)]*\)\s*\{[\s\S]*?\1\.get\([^)]*\)\.equals\(\2\)[\s\S]*?\}\s*if\s*\(!\4\)\s*\{\s*\1\.add\(\2\)\s*;\s*\}\s*\}\s*return\s+\1\s*;"""
        )
        val match = pattern.find(source) ?: return source
        val paramList = match.groupValues[3]
        val replacement = "return new java.util.ArrayList<>(new java.util.LinkedHashSet<>(" + paramList + "));"
        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
        changes.add(OptimizationChange(
            type = "LinkedHashSet Deduplication",
            description = "Заменен квадратичный поиск дубликатов в списке на LinkedHashSet O(n)",
            location = SourceLocation(filePath, line, line + match.value.count { it == '\n' }, 1, 1),
            complexityBefore = "O(n²)",
            complexityAfter = "O(n)"
        ))
        return source.replaceRange(match.range, replacement)
    }

    private fun optimizeCharLoopAccumulation(
        source: String,
        filePath: String,
        changes: MutableList<OptimizationChange>
    ): String {
        val pattern = Regex(
            """String\s+(\w+)\s*=\s*""\s*;\s*for\s*\(\s*int\s+(\w+)\s*=\s*0\s*;\s*\2\s*<\s*(\w+)\.length\(\)\s*;\s*\2\+\+\s*\)\s*\{\s*char\s+(\w+)\s*=\s*\3\.charAt\(\2\)\s*;([\s\S]*?)\1\s*=\s*\1\s*\+\s*\4\s*;\s*\}\s*return\s+\1\s*;"""
        )
        val match = pattern.find(source) ?: return source
        val varName = match.groupValues[1]
        val indexVar = match.groupValues[2]
        val strVar = match.groupValues[3]
        val charVar = match.groupValues[4]
        val innerLogic = match.groupValues[5]

        val replacement = """
            StringBuilder $varName = new StringBuilder($strVar.length());
            for (int $indexVar = 0; $indexVar < $strVar.length(); $indexVar++) {
                char $charVar = $strVar.charAt($indexVar);$innerLogic
                $varName.append($charVar);
            }
            return $varName.toString();
        """.trimIndent()

        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
        changes.add(OptimizationChange(
            type = "StringBuilder Character Accumulation",
            description = "Заменена квадратичная конкатенация символов в цикле на StringBuilder",
            location = SourceLocation(filePath, line, line + match.value.count { it == '\n' }, 1, 1),
            complexityBefore = "O(n²)",
            complexityAfter = "O(n)"
        ))
        return source.replaceRange(match.range, replacement)
    }

    private fun optimizeArraysFill(
        source: String,
        filePath: String,
        changes: MutableList<OptimizationChange>
    ): String {
        val pattern = Regex(
            """for\s*\(\s*int\s+(\w+)\s*=\s*(\d+)\s*;\s*\1\s*<\s*(\w+)\.length\s*;\s*\1\+\+\s*\)\s*\{\s*""" +
                """\3\[\1\]\s*=\s*(0(?:\.0)?)\s*;\s*(\w+)\+\+\s*;\s*\}"""
        )

        val match = pattern.find(source) ?: return source
        val startVal = match.groupValues[2]
        val arrayName = match.groupValues[3]
        val zeroVal = match.groupValues[4]
        val countVar = match.groupValues[5]

        val replacement = """
            java.util.Arrays.fill($arrayName, $startVal, $arrayName.length, $zeroVal);
            $countVar += ($arrayName.length - $startVal);
        """.trimIndent()

        val rangeStart = match.range.first
        val line = source.substring(0, rangeStart).count { it == '\n' } + 1
        changes.add(
            OptimizationChange(
                type = "Arrays.fill Memory Optimization",
                description = "Заменен поэлементный цикл очистки памяти на векторный Arrays.fill",
                location = SourceLocation(filePath, line, line + match.value.count { it == '\n' }, 1, 1),
                complexityBefore = "O(n)",
                complexityAfter = "O(1) JVM Intrinsic"
            )
        )
        return source.replaceRange(match.range, replacement)
    }

    /**
     * Recursive algorithm rewrites are intentionally not inferred from method names.
     * Only locally provable AST transformations are retained by the pipeline.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun optimizeRecursiveFibonacci(
        source: String,
        filePath: String,
        changes: MutableList<OptimizationChange>
    ): String = source

    /**
     * Deterministic decomposition based on the static method dependency graph.
     * Methods that depend on mutable class fields or non-static class methods
     * remain in the facade, because moving them would change object ownership.
     */
    private fun tryDecomposeIntoModules(
        source: String,
        originalPath: String,
        changes: MutableList<OptimizationChange>
    ): Map<String, String>? {
        val cu = try {
            StaticJavaParser.parse(source)
        } catch (_: Exception) {
            return null
        }

        val primaryClass = cu.findFirst(ClassOrInterfaceDeclaration::class.java).orElse(null)
            ?: return null
        val originalClassName = primaryClass.nameAsString
        val packageName = cu.packageDeclaration.map { it.nameAsString }.orElse("")
        val methods = primaryClass.methods
        val methodByName = methods.groupBy { it.nameAsString }
        val fieldNames = primaryClass.fields.flatMap { field ->
            field.variables.map { it.nameAsString }
        }.toSet()

        fun calls(method: MethodDeclaration): Set<String> = method
            .findAll(com.github.javaparser.ast.expr.MethodCallExpr::class.java)
            .map { it.nameAsString }
            .filter { methodByName.containsKey(it) }
            .toSet()

        fun readsClassField(method: MethodDeclaration): Boolean {
            val body = method.body.map { it.toString() }.orElse("")
            return fieldNames.any { field -> Regex("\\b${Regex.escape(field)}\\b").containsMatchIn(body) }
        }

        val candidates = methods.filter { method ->
            method.nameAsString != "main" &&
                method.body.isPresent &&
                !readsClassField(method)
        }.toMutableSet()

        // Remove methods whose call dependencies cannot travel with them. This
        // fixed point makes every generated module independently compilable.
        var changed: Boolean
        do {
            changed = false
            val unsupported = candidates.filter { method ->
                calls(method).any { calledName ->
                    methodByName[calledName].orEmpty().none { it in candidates }
                }
            }
            if (unsupported.isNotEmpty()) {
                candidates.removeAll(unsupported.toSet())
                changed = true
            }
        } while (changed)

        if (candidates.isEmpty()) return null

        val candidateByName = candidates.associateBy { it.nameAsString }
        val adjacency = candidates.associateWith { method ->
            calls(method).mapNotNull { candidateByName[it] }.toSet()
        }
        val components = mutableListOf<Set<MethodDeclaration>>()
        val remaining = candidates.toMutableSet()
        while (remaining.isNotEmpty()) {
            val seed = remaining.minBy { it.nameAsString }
            val component = linkedSetOf<MethodDeclaration>()
            val queue = ArrayDeque<MethodDeclaration>()
            queue.add(seed)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (!component.add(current)) continue
                val neighbours = adjacency[current].orEmpty() +
                    adjacency.filterValues { current in it }.keys
                neighbours.forEach { if (it in remaining) queue.add(it) }
            }
            remaining.removeAll(component)
            components.add(component)
        }

        val groups = components
            .sortedBy { group -> group.minOf { it.nameAsString } }
            .take(4)
        val moduleSuffixes = listOf("Core", "Processor", "Helper", "Support")
        val files = linkedMapOf<String, String>()
        val extracted = groups.flatMapIndexed { index, group ->
            val moduleName = originalClassName + moduleSuffixes[index]
            files[moduleFilePath(originalPath, moduleName)] = generateModuleSource(
                cu,
                packageName,
                moduleName,
                group.toList().sortedBy { it.nameAsString }
            )
            group.map { it to moduleName }
        }.toMap()

        val mainCu = StaticJavaParser.parse(source)
        val mainClass = mainCu.findFirst(ClassOrInterfaceDeclaration::class.java).orElse(null)
            ?: return null
        mainClass.methods.forEach { method ->
            val moduleName = extracted.entries.firstOrNull { it.key.nameAsString == method.nameAsString }?.value
            if (moduleName != null) {
                val args = method.parameters.joinToString(", ") { it.nameAsString }
                val call = "$moduleName.${method.nameAsString}($args)"
                method.setBody(StaticJavaParser.parseBlock(
                    if (method.type.isVoidType) "{ $call; }" else "{ return $call; }"
                ))
            }
        }
        files[originalPath] = mainCu.toString()

        val tempDir = createTempDir("decomp_verify_")
        val compileCheck = try {
            verifier.compileJavaCode(files, tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
        if (!compileCheck.success) return null

        val modulesCount = files.size
        changes.add(
            OptimizationChange(
                type = "Architecture Decomposition",
                description = "Методы распределены по графу вызовов в $modulesCount файл(а), фасады сохранены: ${files.keys.joinToString()}",
                location = SourceLocation(originalPath, 1, 1, 1, 1),
                complexityBefore = "Монолит (${methods.size} методов)",
                complexityAfter = "$modulesCount модулей с детерминированными границами"
            )
        )
        return files
    }

    private fun moduleFilePath(originalPath: String, moduleName: String): String {
        val normalized = originalPath.replace('\\', '/')
        val directory = normalized.substringBeforeLast('/', "")
        return if (directory.isBlank()) "$moduleName.java" else "$directory/$moduleName.java"
    }

    private fun generateModuleSource(
        cu: CompilationUnit,
        packageName: String,
        className: String,
        methods: List<MethodDeclaration>
    ): String {
        val sb = StringBuilder()
        if (packageName.isNotBlank()) {
            sb.appendLine("package $packageName;")
            sb.appendLine()
        }
        cu.imports.forEach { sb.appendLine(it.toString()) }
        if (cu.imports.isNotEmpty()) sb.appendLine()
        sb.appendLine("public final class $className {")
        sb.appendLine()
        methods.forEach { original ->
            val method = original.clone()
            method.isPrivate = false
            method.isProtected = false
            method.isPublic = true
            method.isStatic = true
            sb.appendLine("    ${method.toString().replace("\\n", "\\n    ")}")
            sb.appendLine()
        }
        sb.appendLine("}")
        return sb.toString()
    }
}
