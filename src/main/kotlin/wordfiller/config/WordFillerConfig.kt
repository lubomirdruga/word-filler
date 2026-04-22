package com.lubomirdruga.wordfiller.config

/**
 * Configuration for WordFiller.
 *
 * @property templateBasePath Base path for Word templates (default: "word-filler")
 */
data class WordFillerConfig(
    val templateBasePath: String = "word-filler"
)
