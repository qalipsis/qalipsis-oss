package io.evolue.plugins.jackson.csv

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.math.BigInteger
import java.util.stream.Stream

/**
 * @author Eric Jessé
 */
internal class CsvColumnTypeTest {

    @DisplayName("Conversion of a string")
    @ParameterizedTest(name = "{index} => Conversion of ''{1}'' as {0}")
    @MethodSource("conversionInputAndOutput")
    internal fun `should validate and convert`(type: CsvColumnType, input: String?, expectedResult: Any?,
                                               expectedSuccess: Boolean) {
        var success = true
        try {
            val result = type.converter(input)
            Assertions.assertEquals(expectedResult, result)
        } catch (e: IllegalStateException) {
            success = false
        }
        Assertions.assertEquals(expectedSuccess, success)
    }

    companion object {

        const val succeeds = true

        const val fails = false

        @JvmStatic
        fun conversionInputAndOutput() = Stream.of(
                // Type, input, expected output, expected success
                arguments(CsvColumnType.STRING, "a string", "a string", succeeds),
                arguments(CsvColumnType.STRING, "  ", "  ", succeeds),
                arguments(CsvColumnType.STRING, "", null, fails),
                arguments(CsvColumnType.STRING, null, null, fails),

                arguments(CsvColumnType.NULLABLE_STRING, "a string", "a string", succeeds),
                arguments(CsvColumnType.NULLABLE_STRING, "  ", "  ", succeeds),
                arguments(CsvColumnType.NULLABLE_STRING, "", null, succeeds),
                arguments(CsvColumnType.NULLABLE_STRING, null, null, succeeds),

                arguments(CsvColumnType.NULLABLE_INTEGER, "1", 1, succeeds),
                arguments(CsvColumnType.NULLABLE_INTEGER, "1.865", null, succeeds),
                arguments(CsvColumnType.NULLABLE_INTEGER, "a string", null, succeeds),
                arguments(CsvColumnType.NULLABLE_INTEGER, "  ", null, succeeds),
                arguments(CsvColumnType.NULLABLE_INTEGER, null, null, succeeds),

                arguments(CsvColumnType.INTEGER, "1", 1, succeeds),
                arguments(CsvColumnType.INTEGER, "1.865", null, fails),
                arguments(CsvColumnType.INTEGER, "a string", null, fails),
                arguments(CsvColumnType.INTEGER, "  ", null, fails),
                arguments(CsvColumnType.INTEGER, null, null, fails),

                arguments(CsvColumnType.NULLABLE_BIG_INTEGER, "1", BigInteger.ONE, succeeds),
                arguments(CsvColumnType.NULLABLE_BIG_INTEGER, "1.865", null, succeeds),
                arguments(CsvColumnType.NULLABLE_BIG_INTEGER, "a string", null, succeeds),
                arguments(CsvColumnType.NULLABLE_BIG_INTEGER, "  ", null, succeeds),
                arguments(CsvColumnType.NULLABLE_BIG_INTEGER, null, null, succeeds),

                arguments(CsvColumnType.BIG_INTEGER, "1", BigInteger.ONE, succeeds),
                arguments(CsvColumnType.BIG_INTEGER, "1.865", null, fails),
                arguments(CsvColumnType.BIG_INTEGER, "a string", null, fails),
                arguments(CsvColumnType.BIG_INTEGER, "  ", null, fails),
                arguments(CsvColumnType.BIG_INTEGER, null, null, fails),

                arguments(CsvColumnType.NULLABLE_DOUBLE, "1.865", 1.865, succeeds),
                arguments(CsvColumnType.NULLABLE_DOUBLE, "a string", null, succeeds),
                arguments(CsvColumnType.NULLABLE_DOUBLE, "  ", null, succeeds),
                arguments(CsvColumnType.NULLABLE_DOUBLE, null, null, succeeds),

                arguments(CsvColumnType.DOUBLE, "1.865", 1.865, succeeds),
                arguments(CsvColumnType.DOUBLE, "a string", null, fails),
                arguments(CsvColumnType.DOUBLE, "  ", null, fails),
                arguments(CsvColumnType.DOUBLE, null, null, fails),

                arguments(CsvColumnType.NULLABLE_BIG_DECIMAL, "1.865", BigDecimal.valueOf(1.865), succeeds),
                arguments(CsvColumnType.NULLABLE_BIG_DECIMAL, "a string", null, succeeds),
                arguments(CsvColumnType.NULLABLE_BIG_DECIMAL, "  ", null, succeeds),
                arguments(CsvColumnType.NULLABLE_BIG_DECIMAL, null, null, succeeds),

                arguments(CsvColumnType.BIG_DECIMAL, "1.865", BigDecimal.valueOf(1.865), succeeds),
                arguments(CsvColumnType.BIG_DECIMAL, "a string", null, fails),
                arguments(CsvColumnType.BIG_DECIMAL, "  ", null, fails),
                arguments(CsvColumnType.BIG_DECIMAL, null, null, fails),

                arguments(CsvColumnType.NULLABLE_LONG, "1", 1L, succeeds),
                arguments(CsvColumnType.NULLABLE_LONG, "1.865", null, succeeds),
                arguments(CsvColumnType.NULLABLE_LONG, "a string", null, succeeds),
                arguments(CsvColumnType.NULLABLE_LONG, "  ", null, succeeds),
                arguments(CsvColumnType.NULLABLE_LONG, null, null, succeeds),

                arguments(CsvColumnType.LONG, "1", 1L, succeeds),
                arguments(CsvColumnType.LONG, "1.865", null, fails),
                arguments(CsvColumnType.LONG, "a string", null, fails),
                arguments(CsvColumnType.LONG, "  ", null, fails),
                arguments(CsvColumnType.LONG, null, null, fails),

                arguments(CsvColumnType.NULLABLE_FLOAT, "1.865", 1.865F, succeeds),
                arguments(CsvColumnType.NULLABLE_FLOAT, "a string", null, succeeds),
                arguments(CsvColumnType.NULLABLE_FLOAT, "  ", null, succeeds),
                arguments(CsvColumnType.NULLABLE_FLOAT, null, null, succeeds),

                arguments(CsvColumnType.FLOAT, "1.865", 1.865F, succeeds),
                arguments(CsvColumnType.FLOAT, "a string", null, fails),
                arguments(CsvColumnType.FLOAT, "  ", null, fails),
                arguments(CsvColumnType.FLOAT, null, null, fails),

                arguments(CsvColumnType.NULLABLE_BOOLEAN, "true", true, succeeds),
                arguments(CsvColumnType.NULLABLE_BOOLEAN, "a string", false, succeeds),
                arguments(CsvColumnType.NULLABLE_BOOLEAN, "  ", null, succeeds),
                arguments(CsvColumnType.NULLABLE_BOOLEAN, null, null, succeeds),

                arguments(CsvColumnType.BOOLEAN, "true", true, succeeds),
                arguments(CsvColumnType.BOOLEAN, "a string", false, succeeds),
                arguments(CsvColumnType.BOOLEAN, "  ", null, fails),
                arguments(CsvColumnType.BOOLEAN, null, null, fails)
        )
    }
}