package com.lubomirdruga.wordfiller

/**
 * Thrown when a template cannot be found or processed: missing Word/Velocity
 * templates, malformed or failing expressions, and unterminated placeholders.
 *
 * Wiring errors (e.g. mismatched [WordFillerConfig]
 * instances) throw [IllegalArgumentException] instead.
 */
class WordFillerException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
