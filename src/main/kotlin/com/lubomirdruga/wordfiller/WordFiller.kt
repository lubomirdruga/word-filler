package com.lubomirdruga.wordfiller

import com.lubomirdruga.wordfiller.provider.TemplateDataProvider
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Service for exporting Word documents with Velocity template substitution.
 *
 * Loads a Word template, replaces `{...}` placeholders via [PlaceholderReplacer],
 * converts plain-text URLs into hyperlinks via [HyperlinkFormatter], and returns
 * the result as a byte array. Traversal of the document is handled by [DocumentWalker].
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
     * @throws WordFillerException if the template is missing or processing fails
     */
    fun export(
        template: String,
        obj: Any?,
    ): ByteArray {
        val templatePath = config.wordTemplatePath(template)
        logger.debug("Exporting template '{}' from classpath at {}", template, templatePath)
        val inputStream =
            javaClass.getResourceAsStream(templatePath)
                ?: throw WordFillerException("Word template not found on classpath at: $templatePath")
        inputStream.use {
            XWPFDocument(it).use { doc ->
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
     * @throws WordFillerException if the template file is missing or processing fails
     */
    fun exportFromFile(
        templateFile: File,
        templateName: String,
        obj: Any?,
    ): ByteArray {
        if (!templateFile.exists()) {
            throw WordFillerException("Word template file does not exist: ${templateFile.absolutePath}")
        }
        logger.debug("Exporting template '{}' from file {}", templateName, templateFile.absolutePath)
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
    fun exportFromStream(
        inputStream: InputStream,
        templateName: String,
        obj: Any?,
    ): ByteArray {
        XWPFDocument(inputStream).use { doc ->
            processDocument(doc, templateName, obj)
            return transformToByteArray(doc)
        }
    }

    private fun processDocument(
        doc: XWPFDocument,
        template: String,
        obj: Any?,
    ) {
        val replacer = PlaceholderReplacer(dataProvider, template, obj)
        val walker =
            DocumentWalker { paragraph ->
                replacer.process(paragraph)
                HyperlinkFormatter.process(paragraph)
            }
        walker.walk(doc)
    }

    private fun transformToByteArray(doc: XWPFDocument): ByteArray {
        ByteArrayOutputStream().use { out ->
            doc.write(out)
            return out.toByteArray()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WordFiller::class.java)
    }
}
