package com.lubomirdruga.wordfiller

/**
 * Configuration for WordFiller. Single source of truth for the template root.
 *
 * @property templateBasePath Classpath root for all templates (default: "word-filler").
 *   Word templates resolve to `/<templateBasePath>/<template>.docx`; template data
 *   providers derive their own resource locations from this root via [resolve].
 */
data class WordFillerConfig(
    val templateBasePath: String = "word-filler",
) {
    /**
     * Classpath location of a Word template.
     */
    fun wordTemplatePath(template: String): String = resolve("$template.docx")

    /**
     * Resolve a path relative to the template root. Intended for [com.lubomirdruga.wordfiller.provider.TemplateDataProvider]
     * implementations to locate their own resources under the shared root.
     */
    fun resolve(relativePath: String): String = "/${templateBasePath.trim('/')}/${relativePath.trimStart('/')}"
}
