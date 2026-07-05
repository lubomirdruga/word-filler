package com.lubomirdruga.wordfiller.provider

import com.lubomirdruga.wordfiller.WordFillerConfig

/**
 * Provider for evaluating template expressions with a data context.
 */
fun interface TemplateDataProvider {
    /**
     * The config this provider was created with, or null for providers that do not
     * resolve templates by path (e.g. simple lambdas). When non-null, WordFiller
     * requires it to match its own config so both resolve templates under the same root.
     */
    val config: WordFillerConfig?
        get() = null

    /**
     * Evaluate a template expression with the given context.
     *
     * @param expression The expression to evaluate
     * @param template The template name/identifier
     * @param value The data object available in the expression context
     * @return The evaluated result as a string
     */
    fun evaluateExpression(
        expression: String,
        template: String,
        value: Any?,
    ): String
}
