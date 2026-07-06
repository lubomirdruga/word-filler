package com.lubomirdruga.wordfiller

import com.lubomirdruga.wordfiller.provider.TemplateDataProvider
import com.lubomirdruga.wordfiller.provider.VelocityDataProvider
import org.apache.poi.wp.usermodel.HeaderFooterType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WordFillerTest {
    private val config = WordFillerConfig()
    private val filler = WordFiller(config, VelocityDataProvider(config))

    // --- placeholder substitution ---/

    @Test
    fun `replaces placeholder in a single run`() {
        val template = buildDocument { addParagraph($$"Hello {$model.name}!") }

        process(template, TestPerson("John")).use { doc ->
            assertEquals("Hello John!", doc.paragraphs[0].text)
        }
    }

    @Test
    fun `replaces multiple placeholders in a single run`() {
        val template = buildDocument { addParagraph($$"{$model.name} - {$model.jobTitle}") }

        process(template, TestPerson("John", "Engineer")).use { doc ->
            assertEquals("John - Engineer", doc.paragraphs[0].text)
        }
    }

    @Test
    fun `replaces placeholder split across multiple runs`() {
        val template = buildDocument { addParagraph($$"Hello {$model.name}!") }

        process(template, TestPerson("John")).use { doc ->
            assertEquals("Hello John!", doc.paragraphs[0].text)
        }
    }

    @Test
    fun `leaves text without placeholders unchanged`() {
        val template = buildDocument { addParagraph("Nothing to replace here.") }

        process(template, TestPerson("John")).use { doc ->
            assertEquals("Nothing to replace here.", doc.paragraphs[0].text)
        }
    }

    @Test
    fun `replaces placeholders in table cells`() {
        val template =
            buildDocument {
                val table = createTable(1, 2)
                table.getRow(0).getCell(0).text = $$"Name: {$model.name}"
                table.getRow(0).getCell(1).text = $$"Job: {$model.jobTitle}"
            }

        process(template, TestPerson("John", "Engineer")).use { doc ->
            val row = doc.tables[0].getRow(0)
            assertEquals("Name: John", row.getCell(0).text)
            assertEquals("Job: Engineer", row.getCell(1).text)
        }
    }

    @Test
    fun `replaces placeholders in headers`() {
        val template =
            buildDocument {
                val header = createHeader(HeaderFooterType.DEFAULT)
                header.createParagraph().createRun().setText($$"Author: {$model.name}")
            }

        process(template, TestPerson("John")).use { doc ->
            val headerText = doc.headerList.joinToString("") { it.text }
            assertTrue(headerText.contains("Author: John"), "header should contain replaced text, was: $headerText")
        }
    }

    @Test
    fun `replaces placeholders in footers`() {
        val template =
            buildDocument {
                val footer = createFooter(HeaderFooterType.DEFAULT)
                footer.createParagraph().createRun().setText($$"Page by {$model.name}")
            }

        process(template, TestPerson("John")).use { doc ->
            val footerText = doc.footerList.joinToString("") { it.text }
            assertTrue(footerText.contains("Page by John"), "footer should contain replaced text, was: $footerText")
        }
    }

    @Test
    fun `renders multi-line values as line breaks without trailing break`() {
        val multiLineProvider = TemplateDataProvider { _, _, _ -> "Line1\nLine2\nLine3" }
        val filler = WordFiller(WordFillerConfig(), multiLineProvider)
        val template = buildDocument { addParagraph("{anything}") }

        process(template, data = null, filler = filler).use { doc ->
            assertEquals("Line1\nLine2\nLine3", doc.paragraphs[0].text)
        }
    }

    @Test
    fun `passes expression, template name and data to the provider`() {
        val seen = mutableListOf<String>()
        val recordingProvider =
            TemplateDataProvider { expression, template, value ->
                seen += "$expression|$template|$value"
                "x"
            }
        val filler = WordFiller(WordFillerConfig(), recordingProvider)
        val template = buildDocument { addParagraph("{a}{b}") }

        process(template, data = "data", filler = filler).use {
            assertEquals(listOf("a|test-doc|data", "b|test-doc|data"), seen)
        }
    }

    // --- hyperlink detection ---

    @Test
    fun `converts URL to hyperlink run preserving text order`() {
        val template = buildDocument { addParagraph($$"Visit {$model.url} for details") }

        process(template, TestPerson("John", url = "https://example.com")).use { doc ->
            val paragraph = doc.paragraphs[0]
            assertEquals("Visit https://example.com for details", paragraph.text)

            val linkRun = paragraph.runs.filterIsInstance<XWPFHyperlinkRun>().single()
            assertEquals("https://example.com", linkRun.text())
            assertEquals("https://example.com", linkRun.getHyperlink(doc).url)
        }
    }

    @Test
    fun `converts URL at the start of a run`() {
        val template = buildDocument { addParagraph("http://example.com is the site") }

        process(template, data = null).use { doc ->
            val paragraph = doc.paragraphs[0]
            assertEquals("http://example.com is the site", paragraph.text)
            assertIs<XWPFHyperlinkRun>(paragraph.runs[0])
        }
    }

    @Test
    fun `converts multiple URLs in the same run`() {
        val template = buildDocument { addParagraph("See http://a.example and http://b.example please") }

        process(template, data = null).use { doc ->
            val paragraph = doc.paragraphs[0]
            assertEquals("See http://a.example and http://b.example please", paragraph.text)

            val linkRuns = paragraph.runs.filterIsInstance<XWPFHyperlinkRun>()
            assertEquals(listOf("http://a.example", "http://b.example"), linkRuns.map { it.text() })
        }
    }

    @Test
    fun `keeps trailing sentence punctuation out of the link`() {
        val template = buildDocument { addParagraph("see https://example.com. Next sentence") }

        process(template, data = null).use { doc ->
            val paragraph = doc.paragraphs[0]
            assertEquals("see https://example.com. Next sentence", paragraph.text)

            val linkRun = paragraph.runs.filterIsInstance<XWPFHyperlinkRun>().single()
            assertEquals("https://example.com", linkRun.text())
        }
    }

    @Test
    fun `keeps closing bracket out of the link`() {
        val template = buildDocument { addParagraph("docs (https://example.com) here") }

        process(template, data = null).use { doc ->
            val paragraph = doc.paragraphs[0]
            assertEquals("docs (https://example.com) here", paragraph.text)

            val linkRun = paragraph.runs.filterIsInstance<XWPFHyperlinkRun>().single()
            assertEquals("https://example.com", linkRun.text())
        }
    }

    @Test
    fun `restores line breaks when a multi-line value also contains a URL`() {
        val multiLineProvider = TemplateDataProvider { _, _, _ -> "Kind regards,\nThe team\nhttps://example.com" }
        val filler = WordFiller(WordFillerConfig(), multiLineProvider)
        val template = buildDocument { addParagraph("{anything}") }

        process(template, data = null, filler = filler).use { doc ->
            val paragraph = doc.paragraphs[0]
            assertEquals("Kind regards,\nThe team\nhttps://example.com", paragraph.text)

            val linkRun = paragraph.runs.filterIsInstance<XWPFHyperlinkRun>().single()
            assertEquals("https://example.com", linkRun.text())

            // the newlines must survive linkification as real <w:br/> elements, not as
            // raw '\n' inside a text node - Word does not render those as line breaks
            assertEquals(2, paragraph.runs.sumOf { it.ctr.sizeOfBrArray() })
            assertTrue(paragraph.runs.flatMap { it.ctr.tList }.none { it.stringValue.contains("\n") })
        }
    }

    @Test
    fun `does not create hyperlink for plain text`() {
        val template = buildDocument { addParagraph("Just some plain text") }

        process(template, data = null).use { doc ->
            assertTrue(
                doc.paragraphs[0]
                    .runs
                    .filterIsInstance<XWPFHyperlinkRun>()
                    .isEmpty(),
            )
        }
    }

    // --- template loading ---

    @Test
    fun `exports template from classpath`() {
        val result = filler.export("simple", TestPerson("John"))

        XWPFDocument(ByteArrayInputStream(result)).use { doc ->
            assertEquals("Hello John!", doc.paragraphs[0].text)
        }
    }

    @Test
    fun `export fails for unknown classpath template`() {
        assertFailsWith<WordFillerException> {
            filler.export("does-not-exist", TestPerson("John"))
        }
    }

    @Test
    fun `exports template from file`() {
        val templateFile = File.createTempFile("word-filler-test", ".docx").apply { deleteOnExit() }
        templateFile.writeBytes(buildDocument { addParagraph($$"Hi {$model.name}") })

        val result = filler.exportFromFile(templateFile, "test-doc", TestPerson("John"))

        XWPFDocument(ByteArrayInputStream(result)).use { doc ->
            assertEquals("Hi John", doc.paragraphs[0].text)
        }
    }

    @Test
    fun `exportFromFile fails for missing file`() {
        assertFailsWith<WordFillerException> {
            filler.exportFromFile(File("/nonexistent/template.docx"), "test-doc", null)
        }
    }

    @Test
    fun `throws for unterminated placeholder`() {
        val template = buildDocument { addParagraph($$"Hello {$model.name") }

        val exception =
            assertFailsWith<WordFillerException> {
                process(template, TestPerson("John")).close()
            }
        assertTrue(
            exception.message!!.contains("Unterminated placeholder"),
            "unexpected message: ${exception.message}",
        )
    }

    // --- escape sequences ---

    @Test
    fun `escaped braces produce literal braces`() {
        val template = buildDocument { addParagraph($$"\\{not a placeholder\\} but {$model.name} is") }

        process(template, TestPerson("John")).use { doc ->
            assertEquals("{not a placeholder} but John is", doc.paragraphs[0].text)
        }
    }

    @Test
    fun `escaped backslash produces literal backslash`() {
        val template = buildDocument { addParagraph("a \\\\ b") }

        process(template, TestPerson("John")).use { doc ->
            assertEquals("a \\ b", doc.paragraphs[0].text)
        }
    }

    @Test
    fun `escaped closing brace inside placeholder is part of the expression`() {
        val seen = mutableListOf<String>()
        val recordingProvider =
            TemplateDataProvider { expression, _, _ ->
                seen += expression
                "x"
            }
        val filler = WordFiller(config, recordingProvider)
        val template = buildDocument { addParagraph("{a\\}b}") }

        process(template, data = null, filler = filler).use {
            assertEquals(listOf("a}b"), seen)
        }
    }

    @Test
    fun `escape sequence split across runs works`() {
        val template = buildDocument { addParagraph("start \\", "{literal") }

        process(template, TestPerson("John")).use { doc ->
            assertEquals("start {literal", doc.paragraphs[0].text)
        }
    }

    @Test
    fun `trailing lone backslash is kept as literal text`() {
        val template = buildDocument { addParagraph("ends with \\") }

        process(template, TestPerson("John")).use { doc ->
            assertEquals("ends with \\", doc.paragraphs[0].text)
        }
    }

    // --- multi-text-node runs ---

    @Test
    fun `replaces placeholder spanning multiple text nodes within one run`() {
        val template =
            buildDocument {
                val run = createParagraph().createRun()
                run.setText($$"Hello {$model.name}", 0)
                run.ctr.addNewT().stringValue = "!"
            }

        process(template, TestPerson("John")).use { doc ->
            assertEquals("Hello John!", doc.paragraphs[0].text)
        }
    }

    // --- config consistency ---

    @Test
    fun `rejects provider created with a different config`() {
        assertFailsWith<IllegalArgumentException> {
            WordFiller(WordFillerConfig("word-filler"), VelocityDataProvider(WordFillerConfig("other-root")))
        }
    }

    @Test
    fun `accepts provider created with an equal config instance`() {
        // different instances, same base path - no split root, so this is fine
        WordFiller(WordFillerConfig("word-filler"), VelocityDataProvider(WordFillerConfig("word-filler")))
    }

    @Test
    fun `accepts config-less providers such as lambdas`() {
        WordFiller(config) { _, _, _ -> "value" }
    }

    // --- nested Velocity templates ---

    @Test
    fun `resolves nested velocity template from classpath`() {
        val template = buildDocument { addParagraph("Person: {|person|}") }

        process(template, TestPerson("John", "Engineer")).use { doc ->
            assertEquals("Person: John (Engineer)", doc.paragraphs[0].text)
        }
    }

    // --- helpers ---

    private fun buildDocument(build: XWPFDocument.() -> Unit): ByteArray {
        XWPFDocument().use { doc ->
            doc.build()
            ByteArrayOutputStream().use { out ->
                doc.write(out)
                return out.toByteArray()
            }
        }
    }

    private fun XWPFDocument.addParagraph(vararg runTexts: String) {
        val paragraph = createParagraph()
        runTexts.forEach { text -> paragraph.createRun().setText(text) }
    }

    private fun process(
        templateBytes: ByteArray,
        data: Any?,
        filler: WordFiller = this.filler,
    ): XWPFDocument {
        val result = filler.exportFromStream(ByteArrayInputStream(templateBytes), "test-doc", data)
        return XWPFDocument(ByteArrayInputStream(result))
    }
}
