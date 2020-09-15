package io.evolue.plugins.jackson.csv

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.natpryce.hamkrest.isA
import io.evolue.api.scenario.ScenarioSpecificationImplementation
import io.evolue.api.scenario.scenario
import io.evolue.api.steps.DummyStepSpecification
import io.evolue.api.steps.SingletonConfiguration
import io.evolue.api.steps.SingletonType
import io.evolue.plugins.jackson.config.SourceConfiguration
import io.evolue.plugins.jackson.jackson
import org.junit.jupiter.api.Test
import java.net.URL
import java.nio.file.Path
import java.time.Duration

/**
 *
 * @author Eric Jessé
 */
internal class CsvReaderStepSpecificationTest {

    @Test
    internal fun `should add minimal specification to the scenario that generate an array`() {
        val scenario = scenario("my-scenario") as ScenarioSpecificationImplementation
        scenario.jackson().csvToList {
            file("/path/to/my/file")
        }

        assertThat(scenario.rootSteps[0]).all {
            isA<CsvReaderStepSpecification<List<*>>>()
            prop(CsvReaderStepSpecification<List<*>>::sourceConfiguration).isDataClassEqualTo(SourceConfiguration(
                    url = Path.of("/path/to/my/file").toUri().toURL()
            ))
            prop(CsvReaderStepSpecification<List<*>>::singletonConfiguration).isDataClassEqualTo(
                    SingletonConfiguration(SingletonType.BROADCAST))
            prop(CsvReaderStepSpecification<List<*>>::parsingConfiguration).isDataClassEqualTo(
                    CsvParsingConfiguration())
            prop(CsvReaderStepSpecification<List<*>>::headerConfiguration).isDataClassEqualTo(
                    CsvHeaderConfiguration())
        }
    }

    @Test
    internal fun `should add minimal specification to the scenario that generate a map`() {
        val scenario = scenario("my-scenario") as ScenarioSpecificationImplementation
        scenario.jackson().csvToMap {
            classpath("/path/to/my/file")
        }

        assertThat(scenario.rootSteps[0]).all {
            isA<CsvReaderStepSpecification<Map<String, Any?>>>()
            prop(CsvReaderStepSpecification<Map<String, Any?>>::sourceConfiguration).isDataClassEqualTo(
                    SourceConfiguration(
                            url = this::class.java.getResource("path/to/my/file")
                    ))
            prop(CsvReaderStepSpecification<Map<String, Any?>>::singletonConfiguration).isDataClassEqualTo(
                    SingletonConfiguration(SingletonType.BROADCAST))
            prop(CsvReaderStepSpecification<Map<String, Any?>>::parsingConfiguration).isDataClassEqualTo(
                    CsvParsingConfiguration())
            prop(CsvReaderStepSpecification<Map<String, Any?>>::headerConfiguration).isDataClassEqualTo(
                    CsvHeaderConfiguration())
        }
    }

    @Test
    internal fun `should add minimal specification to the scenario that generate an object`() {
        val scenario = scenario("my-scenario") as ScenarioSpecificationImplementation
        scenario.jackson().csvToObject(MyPojo::class) {
            url("http://path/to/my/file")
        }

        assertThat(scenario.rootSteps[0]).all {
            isA<CsvReaderStepSpecification<MyPojo>>()
            prop(CsvReaderStepSpecification<MyPojo>::sourceConfiguration).isDataClassEqualTo(SourceConfiguration(
                    url = URL("http://path/to/my/file")
            ))
            prop(CsvReaderStepSpecification<MyPojo>::singletonConfiguration).isDataClassEqualTo(
                    SingletonConfiguration(SingletonType.BROADCAST))
            prop(CsvReaderStepSpecification<MyPojo>::parsingConfiguration).isDataClassEqualTo(CsvParsingConfiguration())
            prop(CsvReaderStepSpecification<MyPojo>::headerConfiguration).isDataClassEqualTo(CsvHeaderConfiguration())
        }
    }

    @Test
    internal fun `should add minimal specification that generate an array as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToList {
            file("/path/to/my/file")
        }

        assertThat(previousStep.nextSteps[0]).all {
            isA<CsvReaderStepSpecification<List<*>>>()
            prop(CsvReaderStepSpecification<List<*>>::sourceConfiguration).isDataClassEqualTo(SourceConfiguration(
                    url = Path.of("/path/to/my/file").toUri().toURL()
            ))
            prop(CsvReaderStepSpecification<List<*>>::singletonConfiguration).isDataClassEqualTo(
                    SingletonConfiguration(SingletonType.BROADCAST))
            prop(CsvReaderStepSpecification<List<*>>::parsingConfiguration).isDataClassEqualTo(
                    CsvParsingConfiguration())
            prop(CsvReaderStepSpecification<List<*>>::headerConfiguration).isDataClassEqualTo(
                    CsvHeaderConfiguration())
        }
    }

    @Test
    internal fun `should add minimal specification that generate a map as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToMap {
            classpath("/path/to/my/file")
        }

        assertThat(previousStep.nextSteps[0]).all {
            isA<CsvReaderStepSpecification<Map<String, Any?>>>()
            prop(CsvReaderStepSpecification<Map<String, Any?>>::sourceConfiguration).isDataClassEqualTo(
                    SourceConfiguration(
                            url = this::class.java.getResource("path/to/my/file")
                    ))
            prop(CsvReaderStepSpecification<Map<String, Any?>>::singletonConfiguration).isDataClassEqualTo(
                    SingletonConfiguration(SingletonType.BROADCAST))
            prop(CsvReaderStepSpecification<Map<String, Any?>>::parsingConfiguration).isDataClassEqualTo(
                    CsvParsingConfiguration())
            prop(CsvReaderStepSpecification<Map<String, Any?>>::headerConfiguration).isDataClassEqualTo(
                    CsvHeaderConfiguration())
        }
    }

    @Test
    internal fun `should add minimal specification that generate a POJO as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            url("http://path/to/my/file")
        }

        assertThat(previousStep.nextSteps[0]).all {
            isA<CsvReaderStepSpecification<MyPojo>>()
            prop(CsvReaderStepSpecification<MyPojo>::sourceConfiguration).isDataClassEqualTo(SourceConfiguration(
                    url = URL("http://path/to/my/file")
            ))
            prop(CsvReaderStepSpecification<MyPojo>::singletonConfiguration).isDataClassEqualTo(
                    SingletonConfiguration(SingletonType.BROADCAST))
            prop(CsvReaderStepSpecification<MyPojo>::parsingConfiguration).isDataClassEqualTo(CsvParsingConfiguration())
            prop(CsvReaderStepSpecification<MyPojo>::headerConfiguration).isDataClassEqualTo(CsvHeaderConfiguration())
        }
    }

    @Test
    internal fun `should configure the parsing`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            lineSeparator('L')
            columnSeparator('C')
            quoteChar('Q')
            escapeChar('E')
            allowComments()
        }

        assertThat(previousStep.nextSteps[0]).all {
            prop(CsvReaderStepSpecification<MyPojo>::parsingConfiguration).isDataClassEqualTo(CsvParsingConfiguration(
                    lineSeparator = "L",
                    columnSeparator = 'C',
                    quoteChar = 'Q',
                    escapeChar = 'E',
                    allowComments = true
            ))
        }
    }

    @Test
    internal fun `should configure the header`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            header {
                column("my-column").boolean()
                column("my-column2").boolean()
                column("my-column3").boolean()
            }
        }

        assertThat(previousStep.nextSteps[0]).all {
            prop(CsvReaderStepSpecification<MyPojo>::headerConfiguration).all {
                prop(CsvHeaderConfiguration::columns).hasSize(3)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as default loop`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            loop()
        }

        assertThat(previousStep.nextSteps[0]).all {
            prop(CsvReaderStepSpecification<MyPojo>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.LOOP)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as loop with specified timeout`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            loop(Duration.ofDays(3))
        }

        assertThat(previousStep.nextSteps[0]).all {
            prop(CsvReaderStepSpecification<MyPojo>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.LOOP)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofDays(3))
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as default broadcast`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            broadcast()
        }

        assertThat(previousStep.nextSteps[0]).all {
            prop(CsvReaderStepSpecification<MyPojo>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.BROADCAST)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as broadcast with specified timeout and buffer`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            broadcast(123, Duration.ofDays(3))
        }

        assertThat(previousStep.nextSteps[0]).all {
            prop(CsvReaderStepSpecification<MyPojo>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.BROADCAST)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofDays(3))
                prop(SingletonConfiguration::bufferSize).isEqualTo(123)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as default unicast`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            forwardOnce()
        }

        assertThat(previousStep.nextSteps[0]).all {
            prop(CsvReaderStepSpecification<MyPojo>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
            }
        }
    }

    @Test
    internal fun `should configure the singleton as unicast with specified timeout and buffer`() {
        val previousStep = DummyStepSpecification()
        previousStep.jackson().csvToObject(MyPojo::class) {
            forwardOnce(123, Duration.ofDays(3))
        }

        assertThat(previousStep.nextSteps[0]).all {
            prop(CsvReaderStepSpecification<MyPojo>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofDays(3))
                prop(SingletonConfiguration::bufferSize).isEqualTo(123)
            }
        }
    }

    data class MyPojo(
            val field: String
    )
}
