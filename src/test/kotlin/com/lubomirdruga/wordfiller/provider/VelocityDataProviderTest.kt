package com.lubomirdruga.wordfiller.provider

import com.lubomirdruga.wordfiller.TestPerson
import com.lubomirdruga.wordfiller.WordFillerConfig
import com.lubomirdruga.wordfiller.WordFillerException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VelocityDataProviderTest {
    private val provider = VelocityDataProvider(WordFillerConfig())

    @Test
    fun `evaluates simple property expression`() {
        val result = provider.evaluateExpression($$"$model.name", "test-doc", TestPerson("John"))

        assertEquals("John", result)
    }

    @Test
    fun `evaluates conditional expression`() {
        val expression = $$"#if($model.jobTitle)$model.jobTitle#{else}unknown#end"

        assertEquals("Engineer", provider.evaluateExpression(expression, "test-doc", TestPerson("John", "Engineer")))
        assertEquals("unknown", provider.evaluateExpression(expression, "test-doc", TestPerson("John")))
    }

    @Test
    fun `evaluates loop over map data`() {
        val data = mapOf("items" to listOf("a", "b", "c"))

        val result = provider.evaluateExpression($$"#foreach($i in $model.items)$i;#end", "test-doc", data)

        assertEquals("a;b;c;", result)
    }

    @Test
    fun `resolves nested template by name`() {
        val result = provider.evaluateExpression("|person|", "test-doc", TestPerson("John", "Engineer"))

        assertEquals("John (Engineer)", result)
    }

    @Test
    fun `trims whitespace around nested template name`() {
        val result = provider.evaluateExpression("| person |", "test-doc", TestPerson("John"))

        assertEquals("John", result)
    }

    @Test
    fun `throws for missing nested template`() {
        val exception =
            assertFailsWith<WordFillerException> {
                provider.evaluateExpression("|missing|", "test-doc", TestPerson("John"))
            }

        assertTrue(
            exception.message!!.contains("/word-filler/test-doc/missing.vm"),
            "message should contain the resolved path, was: ${exception.message}",
        )
    }

    @Test
    fun `throws for malformed velocity expression`() {
        assertFailsWith<WordFillerException> {
            provider.evaluateExpression($$"#if($model.name)unclosed", "test-doc", TestPerson("John"))
        }
    }

    @Test
    fun `blocks reflection from templates by default`() {
        // blocked references stay unresolved and render literally
        val result = provider.evaluateExpression($$"$model.class.classLoader", "test-doc", TestPerson("John"))

        assertEquals($$"$model.class.classLoader", result)
    }

    @Test
    fun `allows reflection when secure introspection is disabled`() {
        val permissiveProvider = VelocityDataProvider(WordFillerConfig(), secureIntrospection = false)

        val result = permissiveProvider.evaluateExpression($$"$model.class.simpleName", "test-doc", TestPerson("John"))

        assertEquals("TestPerson", result)
    }

    @Test
    fun `supports custom template base path via config`() {
        val customProvider = VelocityDataProvider(WordFillerConfig(templateBasePath = "custom-templates"))

        val result = customProvider.evaluateExpression("|person|", "test-doc", TestPerson("John"))

        assertEquals("John", result)
    }
}
