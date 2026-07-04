package com.lubomirdruga.wordfiller.config

/**
 * Configuration for WordFiller. Single source of truth for where templates are loaded from.
 *
 * @property templateBasePath Classpath root for all templates (default: "word-filler").
 *   Word templates resolve to "/<templateBasePath>/<template>.docx" and nested Velocity
 *   templates to "/<templateBasePath>/<template>/<name>.vm".
 */
data class WordFillerConfig(
    val templateBasePath: String = "word-filler",
) {

    /**
     * Classpath location of a Word template.
     */
    fun wordTemplatePath(template: String): String = "/${templateBasePath.trim('/')}/$template.docx"

    /**
     * Classpath location of a nested Velocity template belonging to a Word template.
     */
    fun velocityTemplatePath(template: String, subTemplate: String): String =
        "/${templateBasePath.trim('/')}/$template/$subTemplate.vm"
}
