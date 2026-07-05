package com.lubomirdruga.wordfiller

import org.apache.poi.wp.usermodel.HeaderFooterType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentWalkerTest {
    @Test
    fun `visits paragraphs in headers, body, tables and footers`() {
        val built =
            XWPFDocument().use { doc ->
                doc
                    .createHeader(HeaderFooterType.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText("header")
                doc.createParagraph().createRun().setText("body")
                doc
                    .createTable(1, 1)
                    .getRow(0)
                    .getCell(0)
                    .text = "cell"
                doc
                    .createFooter(HeaderFooterType.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText("footer")
                ByteArrayOutputStream().also { doc.write(it) }.toByteArray()
            }

        // parse from bytes: POI only populates header/footer bodyElements when reading a document
        XWPFDocument(ByteArrayInputStream(built)).use { doc ->
            val visited = mutableListOf<String>()
            DocumentWalker { paragraph -> visited += paragraph.text }.walk(doc)

            assertEquals(listOf("header", "body", "cell", "footer"), visited.filter { it.isNotEmpty() })
        }
    }

    @Test
    fun `visits paragraphs in tables nested inside table cells`() {
        XWPFDocument().use { doc ->
            val outerCell = doc.createTable(1, 1).getRow(0).getCell(0)
            outerCell.text = "outer"
            val innerTable = outerCell.insertNewTbl(outerCell.paragraphs[0].ctp.newCursor())
            val innerRow = innerTable.createRow()
            innerRow.createCell().setText("inner")

            val visited = mutableListOf<String>()
            DocumentWalker { paragraph -> visited += paragraph.text }.walk(doc)

            assertEquals(listOf("inner", "outer"), visited.filter { it.isNotEmpty() }.sorted())
        }
    }
}
