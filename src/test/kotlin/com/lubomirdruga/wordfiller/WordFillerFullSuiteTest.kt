package com.lubomirdruga.wordfiller

import com.lubomirdruga.wordfiller.provider.VelocityDataProvider
import org.apache.poi.wp.usermodel.HeaderFooterType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end suite: a real Word document (`src/test/resources/word-filler/full-suite.docx`)
 * containing every supported structure - placeholders in single and split runs, Velocity
 * conditionals and loops, a nested .vm sub-template, escape sequences, multi-line values,
 * URL-to-hyperlink conversion (including a multi-line value that contains a URL), tables
 * (including a nested table), header, and footer -
 * exported once with one data model and asserted structure by structure.
 *
 * The fixture is a normal .docx: open it in Word to inspect or extend it. After changing
 * it (or to rebuild it from code), run the ignored [regenerate the full-suite fixture]
 * test and review the assertions here.
 */
class WordFillerFullSuiteTest {
    @Test
    fun `evaluates simple and multiple placeholders in one paragraph`() {
        result().use { doc ->
            assertEquals("Employee: John (Engineer)", doc.paragraphs[0].text)
        }
    }

    @Test
    fun `joins a placeholder split across runs and renders a loop`() {
        result().use { doc ->
            assertEquals("Skills of John: [Kotlin][Java][SQL]", doc.paragraphs[1].text)
        }
    }

    @Test
    fun `evaluates a velocity conditional`() {
        result().use { doc ->
            assertEquals("Status: ACTIVE", doc.paragraphs[2].text)
        }
    }

    @Test
    fun `renders a nested velocity sub-template`() {
        result().use { doc ->
            assertEquals("Nested: John (Engineer)", doc.paragraphs[3].text)
        }
    }

    @Test
    fun `honours escape sequences`() {
        result().use { doc ->
            assertEquals("{braces} and \\ stay, but John is replaced", doc.paragraphs[4].text)
        }
    }

    @Test
    fun `renders a multi-line value as line breaks`() {
        result().use { doc ->
            assertEquals("42 Main Street\n811 01 Bratislava", doc.paragraphs[5].text)
        }
    }

    @Test
    fun `converts an evaluated URL into a hyperlink keeping punctuation out`() {
        result().use { doc ->
            val paragraph = doc.paragraphs[6]
            assertEquals("Docs at https://example.com/docs.", paragraph.text)

            val linkRun = paragraph.runs.filterIsInstance<XWPFHyperlinkRun>().single()
            assertEquals("https://example.com/docs", linkRun.text())
            assertEquals("https://example.com/docs", linkRun.getHyperlink(doc).url)
        }
    }

    @Test
    fun `leaves a plain paragraph untouched`() {
        result().use { doc ->
            assertEquals("No placeholders here.", doc.paragraphs[7].text)
        }
    }

    @Test
    fun `keeps line breaks when a multi-line value also contains a URL`() {
        result().use { doc ->
            val paragraph = doc.paragraphs[8]
            assertEquals("Kind regards,\nThe Team\nhttps://example.com/contact", paragraph.text)

            val linkRun = paragraph.runs.filterIsInstance<XWPFHyperlinkRun>().single()
            assertEquals("https://example.com/contact", linkRun.text())
            assertEquals("https://example.com/contact", linkRun.getHyperlink(doc).url)

            // linkification must keep the breaks as real <w:br/> elements, not raw '\n'
            // inside a text node - Word does not render those as line breaks
            assertEquals(2, paragraph.runs.sumOf { it.ctr.sizeOfBrArray() })
            assertTrue(paragraph.runs.flatMap { it.ctr.tList }.none { it.stringValue.contains("\n") })
        }
    }

    @Test
    fun `fills outer and nested table cells`() {
        result().use { doc ->
            val outer = doc.tables[0]
            assertEquals("Field", outer.getRow(0).getCell(0).text)
            assertEquals("Value", outer.getRow(0).getCell(1).text)
            assertEquals("Role: Engineer", outer.getRow(1).getCell(0).text)

            val hostCell = outer.getRow(1).getCell(1)
            assertEquals("John", hostCell.text)
            assertEquals(
                "Nested: John",
                hostCell.tables[0]
                    .getRow(0)
                    .getCell(0)
                    .text,
            )
        }
    }

    @Test
    fun `fills the header`() {
        result().use { doc ->
            val headerText = doc.headerList.joinToString("") { it.text }
            assertTrue(headerText.contains("Confidential - John"), "header should be filled, was: $headerText")
        }
    }

    @Test
    fun `fills the footer`() {
        result().use { doc ->
            val footerText = doc.footerList.joinToString("") { it.text }
            assertTrue(footerText.contains("Generated for Engineer"), "footer should be filled, was: $footerText")
        }
    }

    /**
     * Rewrites `src/test/resources/word-filler/full-suite.docx` from [buildFullTemplate].
     * Kept ignored: the checked-in fixture is the source of truth for the suite.
     */
    @Ignore
    @Test
    fun `regenerate the full-suite fixture`() {
        File("src/test/resources/word-filler/full-suite.docx").writeBytes(buildFullTemplate())
    }

    private fun result(): XWPFDocument = XWPFDocument(ByteArrayInputStream(exported))

    companion object {
        private val model =
            mapOf(
                "name" to "John",
                "jobTitle" to "Engineer",
                "active" to true,
                "skills" to listOf("Kotlin", "Java", "SQL"),
                "address" to "42 Main Street\n811 01 Bratislava",
                "website" to "https://example.com/docs",
                "signature" to "Kind regards,\nThe Team\nhttps://example.com/contact",
            )

        /** The fixture is exported once from the classpath; every test asserts on the same output. */
        private val exported: ByteArray by lazy {
            val config = WordFillerConfig()
            val filler = WordFiller(config, VelocityDataProvider(config))
            filler.export("full-suite", model)
        }

        private fun buildFullTemplate(): ByteArray {
            XWPFDocument().use { doc ->
                // body paragraphs, one structure each
                doc.addParagraph($$"Employee: {$model.name} ({$model.jobTitle})")
                doc.addParagraph($$"Skills of {$mo", "del.name}: ", $$"{#foreach($s in $model.skills)[$s]#end}")
                // the } of Velocity's #{else} must be escaped so it does not end the placeholder
                doc.addParagraph($$"Status: {#if($model.active)ACTIVE#{else\\}INACTIVE#end}")
                doc.addParagraph("Nested: {|person|}")
                doc.addParagraph($$"\\{braces\\} and \\\\ stay, but {$model.name} is replaced")
                doc.addParagraph($$"{$model.address}")
                doc.addParagraph($$"Docs at {$model.website}.")
                doc.addParagraph("No placeholders here.")
                doc.addParagraph($$"{$model.signature}")

                // table with a placeholder cell and a nested table
                val table = doc.createTable(2, 2)
                table.getRow(0).getCell(0).text = "Field"
                table.getRow(0).getCell(1).text = "Value"
                table.getRow(1).getCell(0).text = $$"Role: {$model.jobTitle}"
                val hostCell = table.getRow(1).getCell(1)
                hostCell.text = $$"{$model.name}"
                // insertNewTbl creates a table without rows; add one row with one cell
                val nested = hostCell.insertNewTbl(hostCell.paragraphs[0].ctp.newCursor())
                nested.createRow().addNewTableCell().text = $$"Nested: {$model.name}"

                // header and footer
                doc
                    .createHeader(HeaderFooterType.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText($$"Confidential - {$model.name}")
                doc
                    .createFooter(HeaderFooterType.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText($$"Generated for {$model.jobTitle}")

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
    }
}
