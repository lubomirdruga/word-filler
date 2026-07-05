package com.lubomirdruga.wordfiller

import org.apache.poi.xwpf.usermodel.BodyElementType
import org.apache.poi.xwpf.usermodel.IBodyElement
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable

/**
 * Walks the structure of a Word document - headers, body, footers, and tables
 * (including tables nested in cells) - and emits every paragraph to [onParagraph].
 *
 * Pure traversal: knows nothing about placeholders or formatting.
 */
internal class DocumentWalker(
    private val onParagraph: (XWPFParagraph) -> Unit,
) {
    fun walk(doc: XWPFDocument) {
        for (header in doc.headerList) {
            walk(header.bodyElements)
        }
        walk(doc.bodyElements)
        for (footer in doc.footerList) {
            walk(footer.bodyElements)
        }
    }

    private fun walk(bodyElements: List<IBodyElement>) {
        for (bodyElement in bodyElements) {
            when (bodyElement.elementType) {
                BodyElementType.PARAGRAPH -> {
                    onParagraph(bodyElement as XWPFParagraph)
                }

                BodyElementType.TABLE -> {
                    walk(bodyElement as XWPFTable)
                }

                else -> {} // Ignore other element types
            }
        }
    }

    private fun walk(table: XWPFTable) {
        for (row in table.rows) {
            for (cell in row.tableCells) {
                walk(cell.bodyElements)
            }
        }
    }
}
