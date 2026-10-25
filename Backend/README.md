# Java Blueprints Backend

Kotlin (Ktor) backend для хакатона "Java Blueprints" от Axenix.

## Возможности

- **AST парсинг**: Полный анализ Java-кода с помощью JavaParser
- **Граф управления**: Построение блок-схемы из AST
- **Статический анализ**: Обнаружение O(n²) сложности, длинных методов, дублирования
- **Оптимизация**: Автоматические рефакторинги и декомпозиция монолита
- **Синхронизация Java ↔ Graph**: Двусторонняя синхронизация кода и графа
- **Верификация поведения**: Компиляция и запуск тестов для проверки сохранения поведения
- **Поддержка кириллицы**: UTF-8 в консольном I/O

## Запуск

### Требования

- JDK 17+
- Gradle 7.6+

### Сборка и запуск

```bash
# Сборка
./gradlew build

# Запуск сервера
./gradlew run
```

Сервер запустится на http://localhost:8080

## API Endpoints

### GET /api/health
Проверка состояния сервера

### POST /api/project/load
Загрузить проект из файлов

**Request:**
```json
{
  "files": {
    "Main.java": "public class Main { ... }"
  }
}
```

**Response:** ProjectWorkspaceResponse (файлы, граф, анализ)

### POST /api/project/parse
Перепарсить код после редактирования в Monaco Editor

**Request:**
```json
{
  "filePath": "Main.java",
  "content": "..."
}
```

### POST /api/graph/mutate
Изменить код через редактирование узла графа

**Request:**
```json
{
  "nodeId": "method_findDuplicateAndMergeItems_for_0",
  "operation": "UPDATE",
  "newCode": "// optimized code"
}
```

### POST /api/project/optimize
Применить оптимизации и/или декомпозицию монолита

**Request:**
```json
{
  "applyOptimizations": true,
  "decomposeMonolith": true
}
```

**Response:** OptimizationResult (до/после, изменения, результаты тестов)

### POST /api/project/verify
Запустить верификацию поведения (компиляция + тесты)

## Архитектура

```
src/main/kotlin/com/axenix/javablueprints/
├── Application.kt              # Ktor server setup
├── model/
│   └── Models.kt               # Data models
├── parser/
│   └── ASTParserService.kt     # JavaParser integration
├── service/
│   ├── StaticAnalyzerService.kt
│   ├── OptimizerAndDecomposerService.kt
│   ├── GraphMutatorService.kt
│   └── VerificationService.kt
├── repository/
│   └── ProjectRepository.kt
└── routes/
    └── Routes.kt
```

## Технологии

- **Kotlin 1.9.22**
- **Ktor 2.3.7** (Netty)
- **JavaParser 3.26.2** (Symbol Solver)
- **Kotlinx Serialization**
- **JDK Compiler API** для in-memory компиляции

## Лицензия

MIT
