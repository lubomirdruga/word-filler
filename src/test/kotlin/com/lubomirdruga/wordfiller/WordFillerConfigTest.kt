package com.lubomirdruga.wordfiller

import kotlin.test.Test
import kotlin.test.assertEquals

class WordFillerConfigTest {
    @Test
    fun `derives word template path from default base path`() {
        val config = WordFillerConfig()

        assertEquals("/word-filler/invoice.docx", config.wordTemplatePath("invoice"))
    }

    @Test
    fun `derives paths from custom base path`() {
        val config = WordFillerConfig(templateBasePath = "my/templates")

        assertEquals("/my/templates/invoice.docx", config.wordTemplatePath("invoice"))
        assertEquals("/my/templates/invoice/address.vm", config.resolve("invoice/address.vm"))
    }

    @Test
    fun `normalizes leading and trailing slashes in base path and relative path`() {
        val config = WordFillerConfig(templateBasePath = "/my/templates/")

        assertEquals("/my/templates/invoice.docx", config.wordTemplatePath("invoice"))
        assertEquals("/my/templates/invoice/address.vm", config.resolve("/invoice/address.vm"))
    }
}
