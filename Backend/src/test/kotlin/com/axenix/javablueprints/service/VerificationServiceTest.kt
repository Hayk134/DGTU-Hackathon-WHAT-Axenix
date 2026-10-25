package com.axenix.javablueprints.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerificationServiceTest {

    private val verifier = VerificationService()

    @Test
    fun testDetectMainClass() {
        val files = mapOf(
            "Test.java" to """
                package com.axenix.demo;
                public class Test {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
            """.trimIndent()
        )
        val mainClass = verifier.detectMainClass(files)
        assertEquals("com.axenix.demo.Test", mainClass)
    }

    @Test
    fun testResolveSourcePath() {
        val content = """
            package com.enterprise.hell.monolith;
            public class MonolithManager {
            }
        """.trimIndent()
        val path = verifier.resolveSourcePath("any_temp_name.java", content)
        assertEquals("com/enterprise/hell/monolith/MonolithManager.java", path)
    }

    @Test
    fun testRunVerificationBehaviorPreserved() {
        val original = mapOf(
            "Main.java" to """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Result: 42");
                    }
                }
            """.trimIndent()
        )
        val optimized = mapOf(
            "Main.java" to """
                public class Main {
                    public static void main(String[] args) {
                        int x = 40 + 2;
                        System.out.println("Result: " + x);
                    }
                }
            """.trimIndent()
        )

        val result = verifier.runVerification(original, optimized, emptyList())
        assertTrue(result.compilationSuccess, "Compilation should succeed")
        assertTrue(result.behaviorPreserved, "Behavior must be preserved")
        assertTrue(result.testResults.isNotEmpty(), "Test cases must be generated")
        assertTrue(result.testResults.all { it.passed }, "All tests must pass")
    }
}
