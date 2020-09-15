package io.evolue.plugins.jackson.csv

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.fasterxml.jackson.module.kotlin.KotlinModule
import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.annotations.VisibleForTest
import io.evolue.api.exceptions.InvalidSpecificationException
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.api.steps.datasource.DatasourceIterativeReader
import io.evolue.api.steps.datasource.DatasourceObjectProcessor
import io.evolue.api.steps.datasource.DatasourceRecord
import io.evolue.api.steps.datasource.DatasourceRecordObjectConverter
import io.evolue.api.steps.datasource.IterativeDatasourceStep
import io.evolue.api.steps.datasource.processors.MapDatasourceObjectProcessor
import io.evolue.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.evolue.plugins.jackson.JacksonDatasourceIterativeReader
import io.micronaut.jackson.modules.BeanIntrospectionModule
import java.io.InputStreamReader
import kotlin.reflect.full.isSuperclassOf

/**
 * [StepSpecificationConverter] from [CsvReaderStepSpecification] to [IterativeDatasourceStep] for a CSV data source.
 *
 * @author Eric Jessé
 */
@StepConverter
internal class CsvReaderStepSpecificationConverter : StepSpecificationConverter<CsvReaderStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is CsvReaderStepSpecification<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<CsvReaderStepSpecification<*>>) {
        creationContext.createdStep(convert(creationContext.stepSpecification as CsvReaderStepSpecification<out Any>))
    }

    private fun <O : Any> convert(
            spec: CsvReaderStepSpecification<O>): IterativeDatasourceStep<O, O, DatasourceRecord<O>> {
        return IterativeDatasourceStep(spec.name ?: Cuid.createCuid(),
                createReader(spec), createProcessor(spec), DatasourceRecordObjectConverter()
        )
    }

    @VisibleForTest
    internal fun <O : Any> createReader(spec: CsvReaderStepSpecification<O>): DatasourceIterativeReader<O> {
        val sourceUrl = spec.sourceConfiguration.url ?: throw InvalidSpecificationException("No source specified")
        val targetClass = if (List::class.isSuperclassOf(spec.targetClass)) LinkedHashMap::class else spec.targetClass
        return JacksonDatasourceIterativeReader(
                InputStreamReader(sourceUrl.openStream(), spec.sourceConfiguration.encoding),
                createMapper().readerFor(targetClass.java).with(createSchema(spec))
        )
    }

    @VisibleForTest
    internal fun <O : Any> createSchema(spec: CsvReaderStepSpecification<O>): CsvSchema {
        val schemaBuilder = CsvSchema.builder()

        spec.headerConfiguration.columns.filterNotNull().forEach { column ->
            if (column.isArray) {
                schemaBuilder.addColumn(CsvSchema.Column(column.index, column.name, CsvSchema.ColumnType.ARRAY,
                        column.listSeparator))
            } else {
                schemaBuilder.addColumn(
                        CsvSchema.Column(column.index, column.name, CsvSchema.ColumnType.STRING))
            }
        }
        schemaBuilder
            .setLineSeparator(spec.parsingConfiguration.lineSeparator)
            .setColumnSeparator(spec.parsingConfiguration.columnSeparator)
            .setEscapeChar(spec.parsingConfiguration.escapeChar)
            .setQuoteChar(spec.parsingConfiguration.quoteChar)
            .setAllowComments(spec.parsingConfiguration.allowComments)
            .setSkipFirstDataRow(spec.headerConfiguration.skipFirstDataRow)
            .setUseHeader(spec.headerConfiguration.withHeader)

        return schemaBuilder.build()
    }

    @VisibleForTest
    internal fun createMapper() = CsvMapper().also { mapper ->
        mapper.findAndRegisterModules()
        mapper.registerModule(KotlinModule())
        mapper.registerModule(BeanIntrospectionModule())
    }

    @VisibleForTest
    internal fun <O : Any> createProcessor(spec: CsvReaderStepSpecification<O>): DatasourceObjectProcessor<O, O> {
        return when {
            List::class.isSuperclassOf(spec.targetClass) -> createListProcessor(
                    spec as CsvReaderStepSpecification<List<*>>)
            Map::class.isSuperclassOf(spec.targetClass) -> createMapProcessor(
                    spec as CsvReaderStepSpecification<Map<String, *>>)
            else -> NoopDatasourceObjectProcessor()
        }
    }

    private fun <O> createListProcessor(spec: CsvReaderStepSpecification<List<*>>): DatasourceObjectProcessor<O, O> {
        val columnConversionByIndex = mutableListOf<((Any?) -> Any?)>()
        spec.headerConfiguration.columns.filterNotNull().forEach { column ->
            columnConversionByIndex.add(column.index, buildConverter(column))
        }
        return LinkedHashMapToListObjectProcessor(columnConversionByIndex) as DatasourceObjectProcessor<O, O>
    }

    private fun <O> createMapProcessor(
            spec: CsvReaderStepSpecification<Map<String, Any?>>): DatasourceObjectProcessor<O, O> {
        val columnConversionByName = mutableMapOf<String, ((Any?) -> Any?)>()
        spec.headerConfiguration.columns.filterNotNull().forEach { column ->
            columnConversionByName[column.name] = buildConverter(column)
        }
        return MapDatasourceObjectProcessor(columnConversionByName) as DatasourceObjectProcessor<O, O>
    }

    @VisibleForTest
    internal fun buildConverter(column: CsvColumnConfiguration<*>): (Any?.() -> Any?) {
        val typeConverter = column.type.converter

        var converter: ((Any?) -> Any?) = if (column.trim) {
            { value ->
                typeConverter((value as String?)?.trim())
            }
        } else {
            { value ->
                typeConverter(value as String?)
            }
        }

        if (column.isArray) {
            // Converts each value for the list.
            val previousConverter = converter
            converter = { value ->
                when (value) {
                    is Iterable<*> -> {
                        value.map {
                            previousConverter(it)
                        }
                    }
                    is Array<*> -> {
                        value.map {
                            previousConverter(it)
                        }.toTypedArray()
                    }
                    else -> {
                        value
                    }
                }
            }
        }

        // Surrounds with the error management.
        if (column.ignoreError) {
            val previousConverter = converter
            converter = { value ->
                try {
                    previousConverter(value)
                } catch (e: Exception) {
                    column.defaultValue
                }
            }
        }
        return converter
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
