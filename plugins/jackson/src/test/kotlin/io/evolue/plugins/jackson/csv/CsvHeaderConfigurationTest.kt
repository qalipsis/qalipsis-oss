package io.evolue.plugins.jackson.csv

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import io.evolue.api.exceptions.InvalidSpecificationException
import io.evolue.test.assertk.prop
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * @author Eric Jessé
 */
internal class CsvHeaderConfigurationTest {

    @Test
    internal fun `should have default values`() {
        val config = CsvHeaderConfiguration()

        assertThat(config).all {
            prop(CsvHeaderConfiguration::columns).hasSize(0)
            prop("skipFirstDataRow").isEqualTo(false)
            prop("withHeader").isEqualTo(false)
        }
    }

    @Test
    internal fun `should skip first data row`() {
        val config = CsvHeaderConfiguration().skipFirstDataRow()

        assertThat(config).all {
            prop(CsvHeaderConfiguration::columns).hasSize(0)
            prop("skipFirstDataRow").isEqualTo(true)
            prop("withHeader").isEqualTo(false)
        }
    }

    @Test
    internal fun `should have a header`() {
        val config = CsvHeaderConfiguration().withHeader()

        assertThat(config).all {
            prop(CsvHeaderConfiguration::columns).hasSize(0)
            prop("skipFirstDataRow").isEqualTo(false)
            prop("withHeader").isEqualTo(true)
        }
    }

    @Test
    internal fun `should have new columns`() {
        val config = CsvHeaderConfiguration().also {
            it.column("my-column")
            it.column("my-column-1")
            it.column(4)
            it.column(2, "my-column-2")
        }

        assertThat(config).all {
            prop(CsvHeaderConfiguration::columns).all {
                hasSize(5)
                index(0).all {
                    prop(CsvColumnConfiguration<*>::name).isEqualTo("my-column")
                    prop(CsvColumnConfiguration<*>::index).isEqualTo(0)
                }
                index(1).all {
                    prop(CsvColumnConfiguration<*>::name).isEqualTo("my-column-1")
                    prop(CsvColumnConfiguration<*>::index).isEqualTo(1)
                }
                index(2).all {
                    prop(CsvColumnConfiguration<*>::name).isEqualTo("my-column-2")
                    prop(CsvColumnConfiguration<*>::index).isEqualTo(2)
                }
                index(3).isNull()
                index(4).all {
                    prop(CsvColumnConfiguration<*>::name).isEqualTo("field-4")
                    prop(CsvColumnConfiguration<*>::index).isEqualTo(4)
                }
            }
            prop("skipFirstDataRow").isEqualTo(false)
            prop("withHeader").isEqualTo(false)
        }
    }

    @Test
    internal fun `should throw an error when creating a column with blank name`() {
        assertThrows<InvalidSpecificationException> {
            CsvHeaderConfiguration().column("  ")
        }
    }

    @Test
    internal fun `should throw an error when creating two columns with same index`() {
        val config = CsvHeaderConfiguration().also {
            it.column("any")
        }

        assertThrows<InvalidSpecificationException> {
            config.column(0, "my-column")
        }
        assertThrows<InvalidSpecificationException> {
            config.column(0)
        }
    }

    @Test
    internal fun `should throw an error when creating two columns with same name`() {
        val config = CsvHeaderConfiguration().also {
            it.column("any")
        }

        assertThrows<InvalidSpecificationException> {
            config.column(1, "any")
        }
        assertThrows<InvalidSpecificationException> {
            config.column("any")
        }
    }
}
