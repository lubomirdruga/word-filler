package com.lubomirdruga.wordfiller

import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.slf4j.LoggerFactory

/**
 * Converts plain-text `http(s)://` URLs in a paragraph into clickable hyperlink
 * runs (blue, underlined), preserving the surrounding text and its formatting.
 *
 * Stateless document post-processor, independent of template substitution.
 */
internal object HyperlinkFormatter {
    private val logger = LoggerFactory.getLogger(HyperlinkFormatter::class.java)

    private val HYPERLINK_PATTERN = Regex("""http(s)?://\S+""")
    private const val HYPERLINK_COLOR = "0000FF"
    private const val TRAILING_PUNCTUATION = """.,;:!?"')]}"""

    fun process(paragraph: XWPFParagraph) {
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

            val startIdxOfLink = hyperLinkMatch.range.first
            val endIdxOfLink = trimTrailingPunctuation(wholeRunText, startIdxOfLink, hyperLinkMatch.range.last + 1)

            replaceRunWithContentAndLink(paragraph, i, wholeRunText, startIdxOfLink, endIdxOfLink)
            // resume at the run holding the text after the link, so further links in it are still found
            i +=
                if (startIdxOfLink > 0) {
                    2
                } else {
                    1
                }
        }
    }

    /**
     * Sentence punctuation directly after a URL belongs to the text, not the link:
     * "see https://x.com." should link "https://x.com" and keep the dot as plain text.
     */
    private fun trimTrailingPunctuation(
        text: String,
        start: Int,
        end: Int,
    ): Int {
        var trimmedEnd = end
        while (trimmedEnd > start && text[trimmedEnd - 1] in TRAILING_PUNCTUATION) {
            trimmedEnd--
        }
        return trimmedEnd
    }

    private fun replaceRunWithContentAndLink(
        paragraph: XWPFParagraph,
        runIndex: Int,
        originalText: String,
        startIdxOfLink: Int,
        endIdxOfLink: Int,
    ) {
        val originalRun = paragraph.runs[runIndex]
        var insertIndex = runIndex + 1

        if (startIdxOfLink > 0) {
            val runBeforeLink = paragraph.insertNewRun(insertIndex++)
            setTextRestoringLineBreaks(runBeforeLink, originalText.substring(0, startIdxOfLink))
            copyStylesToNewRun(originalRun, runBeforeLink)
        }

        createHyperlinkRun(paragraph, insertIndex++, originalRun, originalText.substring(startIdxOfLink, endIdxOfLink))

        if (endIdxOfLink < originalText.length) {
            val runAfterLink = paragraph.insertNewRun(insertIndex)
            setTextRestoringLineBreaks(runAfterLink, originalText.substring(endIdxOfLink))
            copyStylesToNewRun(originalRun, runAfterLink)
        }

        paragraph.removeRun(runIndex)
    }

    /**
     * [XWPFRun.text] flattens `<w:br/>` elements into `\n` characters, so text written
     * back must be split on them again - a raw newline inside a text node is not
     * rendered as a line break by Word.
     */
    private fun setTextRestoringLineBreaks(
        run: XWPFRun,
        text: String,
    ) {
        val lines = text.lines()
        run.setText(lines.first(), 0)
        for (line in lines.drop(1)) {
            run.addBreak()
            run.setText(line)
        }
    }

    private fun createHyperlinkRun(
        paragraph: XWPFParagraph,
        insertIndex: Int,
        originalRun: XWPFRun,
        link: String,
    ) {
        logger.debug("Converting plain-text URL to hyperlink: {}", link)
        val hyperlinkRun = paragraph.insertNewHyperlinkRun(insertIndex, link)
        hyperlinkRun.setText(link)

        copyStylesToNewRun(originalRun, hyperlinkRun)
        hyperlinkRun.underline = UnderlinePatterns.SINGLE
        hyperlinkRun.color = HYPERLINK_COLOR
    }

    private fun copyStylesToNewRun(
        originalRun: XWPFRun,
        newRun: XWPFRun,
    ) {
        newRun.fontFamily = originalRun.fontFamily
        if (originalRun.fontSizeAsDouble != null) {
            newRun.setFontSize(originalRun.fontSizeAsDouble)
        }

        newRun.isBold = originalRun.isBold
        newRun.isItalic = originalRun.isItalic
        newRun.underline = originalRun.underline

        newRun.color = originalRun.color
    }
}
