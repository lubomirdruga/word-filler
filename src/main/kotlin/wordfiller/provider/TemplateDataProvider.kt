package com.lubomirdruga.wordfiller.provider

/**
 * Provider for evaluating template expressions with a data context.
 */
fun interface TemplateDataProvider {

    /**
     * Evaluate a template expression with the given context.
     *
     * @param expression The expression to evaluate
     * @param template The template name/identifier
     * @param value The data object available in the expression context
     * @return The evaluated result as a string
     */
    fun evaluateExpression(expression: String, template: String, value: Any?): String
}
