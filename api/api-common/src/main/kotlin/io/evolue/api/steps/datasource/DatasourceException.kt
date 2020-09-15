package io.evolue.api.steps.datasource

/**
 * Exception generated when parsing a single line of the file.
 *
 * @author Eric Jessé
 */
class DatasourceException constructor(message: String) : RuntimeException(message) {

    constructor(rowIndex: Long, message: String?) : this("Row $rowIndex: $message")

}