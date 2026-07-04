package com.lubomirdruga.wordfiller

import com.lubomirdruga.wordfiller.config.WordFillerConfig
import com.lubomirdruga.wordfiller.provider.TemplateDataProvider
import org.apache.poi.xwpf.usermodel.BodyElementType
import org.apache.poi.xwpf.usermodel.IBodyElement
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Service for exporting Word documents with Velocity template substitution.
 *
 * @property config Configuration for the word filler
 * @property dataProvider The data provider for template evaluation
 */
class WordFiller(
    private val config: WordFillerConfig,
    private val dataProvider: TemplateDataProvider,
) {

    init {
        val providerConfig = dataProvider.config
        require(providerConfig == null || providerConfig == config) {
            "TemplateDataProvider was created with a different WordFillerConfig " +
                "(templateBasePath=${providerConfig?.templateBasePath}) than WordFiller " +
                "(templateBasePath=${config.templateBasePath}); pass the same config to both."
        }
    }

    /**
     * Export a document using a template name from the classpath.
     *
     * @param template The template name (without extension)
     * @param obj The data object to be used in template evaluation
     * @return ByteArray containing the processed Word document
     */
    fun export(template: String, obj: Any?): ByteArray {
        val templatePath = config.wordTemplatePath(template)
        javaClass.getResourceAsStream(templatePath).use { inputStream ->
            requireNotNull(inputStream) { "Template not found at: $templatePath" }
            XWPFDocument(inputStream).use { doc ->
                processDocument(doc, template, obj)
                return transformToByteArray(doc)
            }
        }
    }

    /**
     * Export a document using a template file from the filesystem.
     *
     * @param templateFile The Word template file
     * @param templateName The name identifier for the template (used for finding Velocity templates)
     * @param obj The data object to be used in template evaluation
     * @return ByteArray containing the processed Word document
     */
    fun exportFromFile(templateFile: File, templateName: String, obj: Any?): ByteArray {
        require(templateFile.exists()) { "Template file does not exist: ${templateFile.absolutePath}" }
        FileInputStream(templateFile).use { inputStream ->
            return exportFromStream(inputStream, templateName, obj)
        }
    }

    /**
     * Export a document using a template from an InputStream.
     *
     * @param inputStream The input stream containing the Word template
     * @param templateName The name identifier for the template (used for finding Velocity templates)
     * @param obj The data object to be used in template evaluation
     * @return ByteArray containing the processed Word document
     */
    fun exportFromStream(inputStream: InputStream, templateName: String, obj: Any?): ByteArray {
        XWPFDocument(inputStream).use { doc ->
            processDocument(doc, templateName, obj)
            return transformToByteArray(doc)
        }
    }

    private fun transformToByteArray(doc: XWPFDocument): ByteArray {
        ByteArrayOutputStream().use { out ->
            doc.write(out)
            return out.toByteArray()
        }
    }

    private fun processDocument(doc: XWPFDocument, template: String, obj: Any?) {
        for (header in doc.headerList) {
            processBodyElements(header.bodyElements, template, obj)
        }
        processBodyElements(doc.bodyElements, template, obj)
        for (footer in doc.footerList) {
            processBodyElements(footer.bodyElements, template, obj)
        }
    }

    private fun processBodyElements(bodyElements: List<IBodyElement>, template: String, data: Any?) {
        for (bodyElement in bodyElements) {
            processBodyElement(bodyElement, template, data)
        }
    }

    private fun processBodyElement(bodyElement: IBodyElement, template: String, data: Any?) {
        when (bodyElement.elementType) {
            BodyElementType.PARAGRAPH -> processParagraph(bodyElement as XWPFParagraph, template, data)
            BodyElementType.TABLE -> processTable(bodyElement as XWPFTable, template, data)
            else -> {} // Ignore other element types
        }
    }

    private fun processTable(table: XWPFTable, template: String, data: Any?) {
        for (row in table.rows) {
            for (cell in row.tableCells) {
                for (bodyElement in cell.bodyElements) {
                    processBodyElement(bodyElement, template, data)
                }
            }
        }
    }

    private fun processParagraph(paragraph: XWPFParagraph, template: String, data: Any?) {
        var expressionText: StringBuilder? = null // expression may span multiple runs in paragraph
        for (run in paragraph.runs) {
            val text = run.getText(0)
            if (text != null) {
                expressionText = processRun(run, expressionText, text, template, data)
            }
        }
        postProcessParagraph(paragraph)
    }

    private fun processRun(
        run: XWPFRun,
        initialExpressionText: StringBuilder?,
        runText: String,
        template: String,
        data: Any?,
    ): StringBuilder? {
        val contentText = StringBuilder()
        var startIndex = 0
        var expressionText = initialExpressionText

        outer@ while (true) {
            if (expressionText == null) { // outside expression
                val index = runText.indexOf(TOKEN_PLACEHOLDER_START, startIndex)
                if (index >= 0) { // expression starts in run
                    expressionText = StringBuilder()
                    contentText.append(runText.substring(startIndex, index))
                    startIndex = index + TOKEN_PLACEHOLDER_START.length
                } else {  // no expression till the end of run
                    contentText.append(runText.substring(startIndex))
                    break@outer
                }
            }

            if (expressionText != null) { // inside expression
                val index = runText.indexOf(TOKEN_PLACEHOLDER_END, startIndex)
                if (index >= 0) { // expression ends
                    expressionText.append(runText.substring(startIndex, index))
                    contentText.append(evaluateExpression(expressionText.toString(), template, data))
                    expressionText = null
                    startIndex = index + TOKEN_PLACEHOLDER_END.length
                } else { // expression continues till the end of run
                    expressionText.append(runText.substring(startIndex))
                    break@outer
                }
            }
        }

        postProcessRun(run, runText, contentText.toString())
        return expressionText
    }

    private fun evaluateExpression(expression: String, template: String, data: Any?): String {
        return dataProvider.evaluateExpression(expression, template, data)
    }

    private fun postProcessRun(run: XWPFRun, oldRunText: String, newRunText: String) {
        if (oldRunText != newRunText) {
            val lines = newRunText.lines()

            if (lines.size == 1) {
                run.setText(newRunText, 0)
            } else {
                run.setText(lines.first(), 0)
                for (line in lines.drop(1)) {
                    run.addBreak()
                    run.setText(line)
                }
            }
        }
    }

    private fun postProcessParagraph(paragraph: XWPFParagraph) {
        var i = 0
        while (i < paragraph.runs.size) {
            val run = paragraph.runs[i]
            if (run is XWPFHyperlinkRun) { // skip already created URLs
                i++
                continue
            }

            val wholeRunText = run.text()
            val hyperLinkMatch = wholeRunText?.let { HYPERLINK_PATTERN.find(it) }
            if (hyperLinkMatch == null) {
                i++
                continue
            }

            replaceRunWithContentAndLink(paragraph, i, wholeRunText, hyperLinkMatch.range.first, hyperLinkMatch.range.last + 1)
            // resume at the run holding the text after the link, so further links in it are still found
            i += if (hyperLinkMatch.range.first > 0) 2 else 1
        }
    }

    private fun replaceRunWithContentAndLink(
        paragraph: XWPFParagraph,
        runIndex: Int,
        originalText: String,
        startIdxOfLink: Int,
        endIdxOfLink: Int
    ) {
        val originalRun = paragraph.runs[runIndex]
        var insertIndex = runIndex + 1

        if (startIdxOfLink > 0) {
            val runBeforeLink = paragraph.insertNewRun(insertIndex++)
            runBeforeLink.setText(originalText.substring(0, startIdxOfLink))
            copyStylesToNewRun(originalRun, runBeforeLink)
        }

        createHyperlinkRun(paragraph, insertIndex++, originalRun, originalText.substring(startIdxOfLink, endIdxOfLink))

        if (endIdxOfLink < originalText.length) {
            val runAfterLink = paragraph.insertNewRun(insertIndex)
            runAfterLink.setText(originalText.substring(endIdxOfLink))
            copyStylesToNewRun(originalRun, runAfterLink)
        }

        paragraph.removeRun(runIndex)
    }

    private fun copyStylesToNewRun(originalRun: XWPFRun, newRun: XWPFRun) {
        newRun.fontFamily = originalRun.fontFamily
        if (originalRun.fontSizeAsDouble != null) {
            newRun.setFontSize(originalRun.fontSizeAsDouble)
        }

        newRun.isBold = originalRun.isBold
        newRun.isItalic = originalRun.isItalic
        newRun.underline = originalRun.underline

        newRun.color = originalRun.color
    }

    private fun createHyperlinkRun(paragraph: XWPFParagraph, insertIndex: Int, originalRun: XWPFRun, link: String) {
        val hyperlinkRun = paragraph.insertNewHyperlinkRun(insertIndex, link)
        hyperlinkRun.setText(link)

        copyStylesToNewRun(originalRun, hyperlinkRun)
        hyperlinkRun.underline = UnderlinePatterns.SINGLE
        hyperlinkRun.color = HYPERLINK_COLOR
    }

    companion object {
        private const val TOKEN_PLACEHOLDER_START = "{"
        private const val TOKEN_PLACEHOLDER_END = "}"
        private const val HYPERLINK_COLOR = "0000FF"
        private val HYPERLINK_PATTERN = Regex("""http(s)?://\S+""")
    }
}
