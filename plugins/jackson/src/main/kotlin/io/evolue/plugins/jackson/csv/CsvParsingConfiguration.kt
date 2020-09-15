package io.evolue.plugins.jackson.csv

/**
 * Configuration to parse data from a CSV file.
 *
 * @author Eric Jessé
 */
data class CsvParsingConfiguration internal constructor(
        internal var lineSeparator: String = System.lineSeparator(),
        internal var columnSeparator: Char = ',',
        internal var escapeChar: Char = '\\',
        internal var quoteChar: Char = '"',
        internal var allowComments: Boolean = false
)
