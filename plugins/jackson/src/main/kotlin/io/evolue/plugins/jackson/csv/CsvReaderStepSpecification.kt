package io.evolue.plugins.jackson.csv

import io.evolue.api.exceptions.InvalidSpecificationException
import io.evolue.api.scenario.MutableScenarioSpecification
import io.evolue.api.steps.datasource.DatasourceRecord
import io.evolue.plugins.jackson.AbstractJacksonStepSpecification
import io.evolue.plugins.jackson.JacksonNamespaceScenarioSpecification
import io.evolue.plugins.jackson.JacksonNamespaceStepSpecification
import kotlin.reflect.KClass

/**
 * Specification for a [io.evolue.api.steps.datasource.IterativeDatasourceStep] for CSV files.
 *
 * @property targetClass class to which the output lines should be mapped.
 *
 * @author Eric Jessé
 */
data class CsvReaderStepSpecification<O : Any> internal constructor(
        internal var targetClass: KClass<O>,
        internal val configurationBlock: CsvReaderStepSpecification<O>.() -> Unit
) : AbstractJacksonStepSpecification<DatasourceRecord<O>, CsvReaderStepSpecification<O>>() {

    internal val parsingConfiguration = CsvParsingConfiguration()

    internal val headerConfiguration = CsvHeaderConfiguration()

    init {
        configurationBlock()
    }

    /**
     * Sets the line separator. Default is [System.lineSeparator].
     *
     * @param sep the feed character / line separator to use
     */
    fun lineSeparator(sep: String): CsvReaderStepSpecification<O> {
        if (sep.isBlank()) {
            throw InvalidSpecificationException("The line separator should not be blank")
        }
        parsingConfiguration.lineSeparator = sep
        return this
    }

    /**
     * Sets the line separator. Default is [System.lineSeparator].
     *
     * @param sep the feed character / line separator to use
     */
    fun lineSeparator(sep: Char): CsvReaderStepSpecification<O> {
        parsingConfiguration.lineSeparator = sep.toString()
        return this
    }

    /**
     * Sets the column separator. Default is `,`.
     *
     * @param sep the character sequence to split the columns
     */
    fun columnSeparator(sep: Char): CsvReaderStepSpecification<O> {
        parsingConfiguration.columnSeparator = sep
        return this
    }

    /**
     * Sets the character to quote sequences of string without splitting them into columns,
     * even if they contain a column or line separator. Default is `"`.
     *
     * @param quoteChar the character to quote unsplittable sequences
     */
    fun quoteChar(quoteChar: Char): CsvReaderStepSpecification<O> {
        parsingConfiguration.quoteChar = quoteChar
        return this
    }

    /**
     * Sets the character to escape a character that should otherwise be interpreted. Default is `\`.
     *
     * @param escapeChar the character to escape signs
     */
    fun escapeChar(escapeChar: Char): CsvReaderStepSpecification<O> {
        parsingConfiguration.escapeChar = escapeChar
        return this
    }

    /**
     * Enables the comments in the source files: lines where the first non-whitespace character is '#' are ignored.
     */
    fun allowComments(): CsvReaderStepSpecification<O> {
        parsingConfiguration.allowComments = true
        return this
    }

    fun header(config: CsvHeaderConfiguration.() -> Unit): CsvReaderStepSpecification<O> {
        this.headerConfiguration.config()
        return this
    }
}

/**
 * Create a new [CsvReaderStepSpecification] with the provided configuration into, each line being interpreted as a [Map].
 *
 * @author Eric Jessé
 */
fun JacksonNamespaceStepSpecification<*, *, *>.csvToMap(
        configurationBlock: CsvReaderStepSpecification<Map<String, *>>.() -> Unit
): CsvReaderStepSpecification<Map<String, *>> {
    val step = CsvReaderStepSpecification(Map::class as KClass<Map<String, *>>, configurationBlock)
    this.add(step)
    return step
}

/**
 * Creates a new [CsvReaderStepSpecification] with the provided configuration into, each line being interpreted as a [List].
 *
 * @author Eric Jessé
 */
fun JacksonNamespaceStepSpecification<*, *, *>.csvToList(
        configurationBlock: CsvReaderStepSpecification<List<*>>.() -> Unit
): CsvReaderStepSpecification<List<*>> {
    val step = CsvReaderStepSpecification(List::class, configurationBlock)
    this.add(step)
    return step
}

/**
 * Creates a new [CsvReaderStepSpecification] with the provided configuration. Each read line is mapped onto an instanceof type OUTPUT.
 *
 * @author Eric Jessé
 */
fun <OUTPUT : Any> JacksonNamespaceStepSpecification<*, *, *>.csvToObject(
        mappingClass: KClass<OUTPUT>,
        configurationBlock: CsvReaderStepSpecification<OUTPUT>.() -> Unit
): CsvReaderStepSpecification<OUTPUT> {
    val step = CsvReaderStepSpecification(mappingClass, configurationBlock)
    this.add(step)
    return step
}

/**
 * Creates a new [CsvReaderStepSpecification] with the provided configuration, each line being interpreted as a [List].
 *
 * @author Eric Jessé
 */
fun JacksonNamespaceScenarioSpecification.csvToList(
        configurationBlock: CsvReaderStepSpecification<List<*>>.() -> Unit
): CsvReaderStepSpecification<List<Any?>> {
    val step = CsvReaderStepSpecification(List::class, configurationBlock)
    (this as MutableScenarioSpecification).add(step)
    return step
}

/**
 * Creates a new [CsvReaderStepSpecification] with the provided configuration, each line being interpreted as a [Map].
 *
 * @author Eric Jessé
 */
fun JacksonNamespaceScenarioSpecification.csvToMap(
        configurationBlock: CsvReaderStepSpecification<Map<String, *>>.() -> Unit
): CsvReaderStepSpecification<Map<String, *>> {
    val step = CsvReaderStepSpecification(Map::class as KClass<Map<String, *>>, configurationBlock)
    (this as MutableScenarioSpecification).add(step)
    return step
}

/**
 * Creates a new [CsvReaderStepSpecification] with the provided configuration. Each read line is mapped onto an instanceof type OUTPUT.
 *
 * @author Eric Jessé
 */
fun <OUTPUT : Any> JacksonNamespaceScenarioSpecification.csvToObject(
        mappingClass: KClass<OUTPUT>,
        configurationBlock: CsvReaderStepSpecification<OUTPUT>.() -> Unit
): CsvReaderStepSpecification<OUTPUT> {
    val step = CsvReaderStepSpecification(mappingClass, configurationBlock)
    (this as MutableScenarioSpecification).add(step)
    return step
}
