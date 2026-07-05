package com.lubomirdruga.wordfiller

import com.lubomirdruga.wordfiller.provider.TemplateDataProvider
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun

/**
 * Replaces `{...}` placeholders in paragraphs with values evaluated by a
 * [TemplateDataProvider]. Handles placeholders that Word splits across multiple
 * runs (and across multiple text nodes within a run) and renders multi-line
 * values as line breaks.
 *
 * Literal braces and backslashes can be produced with escape sequences:
 * `\{` → `{`, `\}` → `}`, `\\` → `\`. Escapes work inside and outside
 * placeholders, even when split across run boundaries.
 *
 * One instance is bound to a single export: the template name and data object
 * are fixed at construction.
 */
internal class PlaceholderReplacer(
    private val dataProvider: TemplateDataProvider,
    private val template: String,
    private val data: Any?,
) {
    /** Tokenizer state carried across the runs of one paragraph. */
    private class State {
        var expression: StringBuilder? = null // non-null while inside a placeholder
        var pendingEscape = false // last consumed char was an unresolved backslash
        var lastRewrittenRun: XWPFRun? = null
    }

    fun process(paragraph: XWPFParagraph) {
        val state = State()
        for (run in paragraph.runs) {
            val text = runText(run)
            if (text != null) {
                processRun(run, state, text)
            }
        }

        state.expression?.let { dangling ->
            throw WordFillerException(
                "Unterminated placeholder '{$dangling' in template '$template'" +
                    " (paragraph: \"${paragraph.text.take(80)}\")",
            )
        }
        if (state.pendingEscape) {
            // paragraph ended with a lone backslash: keep it as literal text
            state.lastRewrittenRun?.setText(ESCAPE_CHAR.toString())
        }
    }

    private fun processRun(
        run: XWPFRun,
        state: State,
        runText: String,
    ) {
        val contentText = StringBuilder()

        for (char in runText) {
            if (state.pendingEscape) {
                state.pendingEscape = false
                val target = state.expression ?: contentText
                when (char) {
                    TOKEN_PLACEHOLDER_START, TOKEN_PLACEHOLDER_END, ESCAPE_CHAR -> target.append(char)
                    else -> { // not an escape sequence: keep the backslash
                        target.append(ESCAPE_CHAR)
                        target.append(char)
                    }
                }
                continue
            }

            when (char) {
                ESCAPE_CHAR -> state.pendingEscape = true

                TOKEN_PLACEHOLDER_START ->
                    if (state.expression == null) {
                        state.expression = StringBuilder()
                    } else { // nested '{' inside a placeholder is literal
                        state.expression!!.append(char)
                    }

                TOKEN_PLACEHOLDER_END -> {
                    val expression = state.expression
                    if (expression != null) {
                        contentText.append(dataProvider.evaluateExpression(expression.toString(), template, data))
                        state.expression = null
                    } else { // stray '}' outside a placeholder is literal
                        contentText.append(char)
                    }
                }

                else -> (state.expression ?: contentText).append(char)
            }
        }

        if (rewriteRun(run, runText, contentText.toString())) {
            state.lastRewrittenRun = run
        }
    }

    /**
     * Full text of a run across all its text nodes, or null if the run has none.
     */
    private fun runText(run: XWPFRun): String? {
        val nodeCount = run.ctr.sizeOfTArray()
        return when (nodeCount) {
            0 -> null
            1 -> run.getText(0)
            else ->
                buildString {
                    for (i in 0 until nodeCount) {
                        append(run.getText(i) ?: "")
                    }
                }
        }
    }

    private fun rewriteRun(
        run: XWPFRun,
        oldRunText: String,
        newRunText: String,
    ): Boolean {
        if (oldRunText == newRunText) {
            return false
        }

        // collapse extra text nodes so the rewritten text lands in a single node
        for (i in run.ctr.sizeOfTArray() - 1 downTo 1) {
            run.ctr.removeT(i)
        }

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
        return true
    }

    companion object {
        private const val TOKEN_PLACEHOLDER_START = '{'
        private const val TOKEN_PLACEHOLDER_END = '}'
        private const val ESCAPE_CHAR = '\\'
    }
}
