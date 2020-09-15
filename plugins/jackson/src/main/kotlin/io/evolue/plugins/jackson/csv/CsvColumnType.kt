package io.evolue.plugins.jackson.csv

/**
 * @author Eric Jessé
 */
internal enum class CsvColumnType(val converter: String?.() -> Any? = { this }) {
    NULLABLE_STRING({ if (this.isNullOrEmpty()) null else this }),
    STRING({ (if (this.isNullOrEmpty()) null else this) ?: error("The value cannot be empty") }),
    NULLABLE_INTEGER({ this?.toIntOrNull() }),
    INTEGER({ this?.toIntOrNull() ?: error("The value cannot be converted to a non-null integer") }),
    NULLABLE_BIG_INTEGER({ this?.toBigIntegerOrNull() }),
    BIG_INTEGER({ this?.toBigIntegerOrNull() ?: error("The value cannot be converted to a non-null big integer") }),
    NULLABLE_DOUBLE({ this?.toDoubleOrNull() }),
    DOUBLE({ this?.toDoubleOrNull() ?: error("The value cannot be converted to a non-null double") }),
    NULLABLE_BIG_DECIMAL({ this?.toBigDecimalOrNull() }),
    BIG_DECIMAL({ this?.toBigDecimalOrNull() ?: error("The value cannot be converted to a non-null big integer") }),
    NULLABLE_LONG({ this?.toLongOrNull() }),
    LONG({ this?.toLongOrNull() ?: error("The value cannot be converted to a non-null long") }),
    NULLABLE_FLOAT({ this?.toFloatOrNull() }),
    FLOAT({ this?.toFloatOrNull() ?: error("The value cannot be converted to a non-null float") }),
    NULLABLE_BOOLEAN({ if (this.isNullOrBlank()) null else this.toBoolean() }),
    BOOLEAN({
        (if (this.isNullOrBlank()) null else this.toBoolean()) ?: error(
                "The value cannot be converted to a non-null boolean")
    })
}