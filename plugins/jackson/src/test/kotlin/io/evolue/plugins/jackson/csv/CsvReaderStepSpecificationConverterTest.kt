package io.evolue.plugins.jackson.csv

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import io.evolue.api.exceptions.InvalidSpecificationException
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.api.steps.datasource.DatasourceIterativeReader
import io.evolue.api.steps.datasource.DatasourceObjectProcessor
import io.evolue.api.steps.datasource.DatasourceRecordObjectConverter
import io.evolue.api.steps.datasource.IterativeDatasourceStep
import io.evolue.api.steps.datasource.processors.MapDatasourceObjectProcessor
import io.evolue.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.evolue.plugins.jackson.JacksonDatasourceIterativeReader
import io.evolue.test.assertk.prop
import io.evolue.test.assertk.typedProp
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.mockk.verifyNever
import io.evolue.test.mockk.verifyOnce
import io.evolue.test.steps.AbstractStepSpecificationConverterTest
import io.mockk.every
import io.mockk.spyk
import io.mockk.verifyOrder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.InputStreamReader
import java.util.stream.Stream
import kotlin.reflect.KClass

/**
 * @author Eric Jessé
 */
internal class CsvReaderStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<CsvReaderStepSpecificationConverter>() {

    lateinit var spiedConverter: CsvReaderStepSpecificationConverter

    @BeforeEach
    internal fun setUp() {
        spiedConverter = spyk(converter)
    }

    @Test
    override fun `should support expected spec`() {
        // when+then
        Assertions.assertTrue(converter.support(relaxedMockk<CsvReaderStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name`() {
        // given
        val spec = CsvReaderStepSpecification(Map::class as KClass<Map<String, *>>) {
            name = "my-step"
            file(createTempFile().absolutePath)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val reader: DatasourceIterativeReader<Map<String, Any?>> = relaxedMockk { }
        val processor: DatasourceObjectProcessor<Map<String, Any?>, Map<String, Any?>> = relaxedMockk { }
        every { spiedConverter.createReader(refEq(spec)) } returns reader
        every { spiedConverter.createProcessor(refEq(spec)) } returns processor

        // when
        runBlocking {
            spiedConverter.convert<Unit, Map<String, *>>(
                    creationContext as StepCreationContext<CsvReaderStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(IterativeDatasourceStep::class)
                prop("id").isEqualTo("my-step")
                prop("reader").isSameAs(reader)
                prop("processor").isSameAs(processor)
                typedProp<Any>("converter").isInstanceOf(DatasourceRecordObjectConverter::class)
            }
        }
    }

    @Test
    internal fun `should convert spec without name`() {
        // given
        val spec = CsvReaderStepSpecification(List::class) {
            file(createTempFile().absolutePath)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val reader: DatasourceIterativeReader<List<*>> = relaxedMockk { }
        val processor: DatasourceObjectProcessor<List<*>, List<*>> = relaxedMockk { }
        every { spiedConverter.createReader(refEq(spec)) } returns reader
        every { spiedConverter.createProcessor(refEq(spec)) } returns processor

        // when
        runBlocking {
            spiedConverter.convert<Unit, List<*>>(
                    creationContext as StepCreationContext<CsvReaderStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(IterativeDatasourceStep::class)
                prop("id").isNotNull()
                prop("reader").isSameAs(reader)
                prop("processor").isSameAs(processor)
                typedProp<Any>("converter").isInstanceOf(DatasourceRecordObjectConverter::class)
            }
        }
    }

    @Test
    internal fun `should generate an error when creating a mapper without source`() {
        // given
        val spec = CsvReaderStepSpecification(Map::class as KClass<Map<String, *>>) {}
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        assertThrows<InvalidSpecificationException> {
            runBlocking {
                converter.convert<Unit, List<*>>(
                        creationContext as StepCreationContext<CsvReaderStepSpecification<*>>)
            }
        }
    }

    @ParameterizedTest
    @MethodSource("testConverters")
    internal fun `should build converter`(columnConfiguration: CsvColumnConfiguration<*>, input: Any?, expected: Any?,
                                          success: Boolean) {

        // given
        val valueConverter = converter.buildConverter(columnConfiguration)

        // when + then
        if (success) {
            val result = valueConverter(input)
            if (expected is Array<*>) {
                Assertions.assertArrayEquals(expected, result as Array<*>)
            } else {
                Assertions.assertEquals(expected, result)
            }
        } else {
            assertThrows<RuntimeException> { valueConverter(input) }
        }
    }

    @Test
    internal fun `should create jackson CSV mapper`() {
        // when
        val mapper = converter.createMapper()

        // then
        assertThat(mapper).all {
            transform { it.registeredModuleIds }.containsAll(
                    "com.fasterxml.jackson.module.kotlin.KotlinModule",
                    "com.fasterxml.jackson.datatype.jdk8.Jdk8Module",
                    "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule",
                    "io.micronaut.jackson.modules.BeanIntrospectionModule"
            )
        }
    }

    @Test
    internal fun `should create processor for a Map`() {
        // given
        val spec = CsvReaderStepSpecification(Map::class as KClass<Map<String, *>>) {
            header {
                column("column-1")
                column("column-2")
                column("column-3")
            }
        }

        // when
        val processor = spiedConverter.createProcessor(spec)

        // then
        assertThat(processor).isInstanceOf(MapDatasourceObjectProcessor::class)
        assertThat(spec.headerConfiguration.columns).hasSize(3)
        spec.headerConfiguration.columns.forEach { col ->
            verifyOnce { spiedConverter.buildConverter(refEq(col!!)) }
        }
    }

    @Test
    internal fun `should create processor for a list`() {
        // given
        val spec = CsvReaderStepSpecification(List::class) {
            header {
                column("column-1")
                column("column-2")
                column("column-3")
            }
        }

        // when
        val processor = spiedConverter.createProcessor(spec)

        // then
        assertThat(processor).isInstanceOf(LinkedHashMapToListObjectProcessor::class)
        assertThat(spec.headerConfiguration.columns).hasSize(3)
        spec.headerConfiguration.columns.forEach { col ->
            verifyOnce { spiedConverter.buildConverter(refEq(col!!)) }
        }
    }

    @Test
    internal fun `should create processor for a POJO`() {
        // given
        val spec = CsvReaderStepSpecification(TestPojo::class) {
            header {
                column("column-1")
                column("column-2")
                column("column-3")
            }
        }

        // when
        val processor = spiedConverter.createProcessor(spec)

        // then
        assertThat(processor).isInstanceOf(NoopDatasourceObjectProcessor::class)
        verifyNever { spiedConverter.buildConverter(any()) }
    }

    @Test
    internal fun `should create schema without column for a Map`() {
        // given
        val spec = CsvReaderStepSpecification(Map::class as KClass<Map<String, *>>) {
            lineSeparator("LS")
            columnSeparator('C')
            escapeChar('E')
            quoteChar('Q')
            allowComments()
            header {
                skipFirstDataRow()
                withHeader()
            }
        }

        // when
        val schema = converter.createSchema(spec)

        // then
        assertThat(schema).all {
            typedProp<Array<CsvSchema.Column>>("_columns").hasSize(0)
            typedProp<CharArray>("_lineSeparator").isEqualTo(charArrayOf('L', 'S'))
            prop("_columnSeparator").isEqualTo('C')
            prop("_escapeChar").isEqualTo('E'.toInt())
            prop("_quoteChar").isEqualTo('Q'.toInt())
            // ENCODING_FEATURE_ALLOW_COMMENTS = 0x0004
            typedProp<Int>("_features").transform { it.and(0x0004) }.isEqualTo(0x0004)
            // ENCODING_FEATURE_USE_HEADER = 0x0001
            typedProp<Int>("_features").transform { it.and(0x0001) }.isEqualTo(0x0001)
            // ENCODING_FEATURE_SKIP_FIRST_DATA_ROW = 0x0002
            typedProp<Int>("_features").transform { it.and(0x0002) }.isEqualTo(0x0002)
        }
    }


    @Test
    internal fun `should create schema with columns for a list`() {
        // given
        val spec = CsvReaderStepSpecification(List::class) {
            lineSeparator("LS")
            columnSeparator('C')
            escapeChar('E')
            quoteChar('Q')
            header {
                column("column-1")
                column("column-2").array("CS")
                column("column-3")
            }
        }

        // when
        val schema = converter.createSchema(spec)

        // then
        assertThat(schema).all {
            typedProp<Array<CsvSchema.Column>>("_columns").all {
                hasSize(3)
                index(0).all {
                    prop("_name").isEqualTo("column-1")
                    prop("_index").isEqualTo(0)
                    prop("_type").isEqualTo(CsvSchema.ColumnType.STRING)
                }
                index(1).all {
                    prop("_name").isEqualTo("column-2")
                    prop("_index").isEqualTo(1)
                    prop("_type").isEqualTo(CsvSchema.ColumnType.ARRAY)
                    prop("_arrayElementSeparator").isEqualTo("CS")
                }
                index(2).all {
                    prop("_name").isEqualTo("column-3")
                    prop("_index").isEqualTo(2)
                    prop("_type").isEqualTo(CsvSchema.ColumnType.STRING)
                }
            }
            typedProp<CharArray>("_lineSeparator").isEqualTo(charArrayOf('L', 'S'))
            prop("_columnSeparator").isEqualTo('C')
            prop("_escapeChar").isEqualTo('E'.toInt())
            prop("_quoteChar").isEqualTo('Q'.toInt())
            // ENCODING_FEATURE_ALLOW_COMMENTS = 0x0004
            typedProp<Int>("_features").transform { it.and(0x0004) }.isEqualTo(0)
            // ENCODING_FEATURE_USE_HEADER = 0x0001.
            typedProp<Int>("_features").transform { it.and(0x0001) }.isEqualTo(0)
            // ENCODING_FEATURE_SKIP_FIRST_DATA_ROW = 0x0002
            typedProp<Int>("_features").transform { it.and(0x0002) }.isEqualTo(0)
        }
    }

    @Test
    internal fun `should create reader when a source is specified`() {
        // given
        val csvFile = createTempFile()
        csvFile.writeText("This is a test")
        val spec = CsvReaderStepSpecification(List::class) {
            file(csvFile.absolutePath)
        }
        val mapper: CsvMapper = relaxedMockk { }
        every { spiedConverter.createMapper() } returns mapper
        val objectReader: ObjectReader = relaxedMockk { }
        every { mapper.readerFor(any<Class<*>>()) } returns objectReader
        val schema: CsvSchema = relaxedMockk { }
        every { spiedConverter.createSchema(refEq(spec)) } returns schema
        every { objectReader.with(refEq(schema)) } returns objectReader

        // when
        val reader = spiedConverter.createReader(spec)

        // then
        assertThat(reader).all {
            isInstanceOf(JacksonDatasourceIterativeReader::class)
            typedProp<InputStreamReader>("inputStreamReader").all {
                transform { it.ready() }.isEqualTo(true)
            }
            prop("objectReader").isSameAs(objectReader)
        }
        verifyOrder {
            spiedConverter.createMapper()
            mapper.readerFor(refEq(LinkedHashMap::class.java))
            objectReader.with(refEq(schema))
        }
    }

    private data class TestPojo(val field1: Int)

    companion object {

        const val success = true
        const val failure = !success

        @JvmStatic
        fun testConverters(): Stream<Arguments> = Stream.of(
                Arguments.arguments(columnTemplate(), "My value", "My value", success),
                Arguments.arguments(columnTemplate().nullableString(true), "   My value   \t\n", "My value", success),
                Arguments.arguments(columnTemplate(), null, null, failure),
                Arguments.arguments(columnTemplate().ignoreError("My default value"), null, "My default value",
                        success),
                Arguments.arguments(columnTemplate().nullableString(true), "   My value   \t\n", "My value", success),
                Arguments.arguments(columnTemplate(), "   My value   \t\n", "   My value   \t\n", success),
                Arguments.arguments(columnTemplate().nullableString(true).array(","),
                        arrayOf("   My value   \t\n", "Other value"),
                        arrayOf("My value", "Other value"), success),
                Arguments.arguments(columnTemplate().nullableString(true).array(","),
                        listOf("   My value   \t\n", "Other value"),
                        listOf("My value", "Other value"), success),
                Arguments.arguments(columnTemplate().nullableString(true), "  \r    \t\n", null, success),
                Arguments.arguments(columnTemplate().double(), " 345.43   ", 345.43, success),
                Arguments.arguments(columnTemplate().double().array(), arrayOf(" 345.43   ", "1541.23"),
                        arrayOf(345.43, 1541.23), success),
                Arguments.arguments(columnTemplate().double(), "    ", null, failure),
                // No need to test all the conversions for all the types, this is done in [CsvColumnTypeTest].
        )

        private fun columnTemplate() = CsvColumnConfiguration<String>(
                index = 0,
                name = "column",
                type = CsvColumnType.STRING
        )
    }
}
