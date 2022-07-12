package io.qalipsis.core.factory.injector

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import assertk.assertions.isSameAs
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.convert.ConversionService
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

/**
 * @author Svetlana Paliashchuk
 */
@WithMockk
@MicronautTest(startApplication = false)
@PropertySource(
    Property(name = "name", value = "some value"),
    Property(name = "number", value = "13"),
    Property(name = "bool", value = "true"),
    Property(name = "noValue", value = "gggg"),
)
internal class DefaultInjectorIntegrationTest {

    @Inject
    private lateinit var applicationContext: ApplicationContext

    @Inject
    private lateinit var conversionService: ConversionService<*>

    private val defaultInjector: DefaultInjector by lazy {
        DefaultInjector(applicationContext, conversionService)
    }

    @Test
    fun `should get a string property`() {
        // given
        val requiredType = String::class.java
        val propertyName = "name"
        val propertyValue = "some value"

        // when
        val result = defaultInjector.getProperty(propertyName, requiredType)

        // then
        assertThat(result.get().javaClass).isSameAs(requiredType)
        assertThat(result.get()).isEqualTo(propertyValue)
    }

    @Test
    fun `should get a number property`() {
        // given
        val requiredType = Integer::class.java
        val propertyName = "number"
        val propertyValue = 13

        // when
        val result = defaultInjector.getProperty(propertyName, requiredType)

        // then
        assertThat(result.get().javaClass).isSameAs(requiredType)
        assertThat(result.get()).isEqualTo(propertyValue)
    }

    @Test
    fun `should get a boolean property`() {
        // given
        val requiredType = java.lang.Boolean::class.java
        val propertyName = "bool"
        val propertyValue = true

        // when
        val result = defaultInjector.getProperty(propertyName, requiredType)

        // then
        assertThat(result.get().javaClass).isSameAs(requiredType)
        assertThat(result.get()).isSameAs(propertyValue)
    }

    @Test
    fun `should get a property when default value is provided`() {
        // given
        val requiredType = String::class.java
        val propertyName = "name"
        val propertyValue = "some value"
        val defaultValue = "13"

        // when
        val result = defaultInjector.getProperty(propertyName, requiredType, defaultValue)

        // then
        assertThat(result.javaClass).isSameAs(requiredType)
        assertThat(result).isEqualTo(propertyValue)
    }

    @Test
    fun `should get a property when it does not have required type and default value is provided`() {
        // given
        val requiredType = Long::class.java
        val propertyName = "wrongValue"
        val defaultValue = 25L

        // when
        val result = defaultInjector.getProperty(propertyName, requiredType, defaultValue)

        // then
        assertThat(result.javaClass).isSameAs(requiredType)
        assertThat(result).isEqualTo(defaultValue)
    }

    @Test
    fun `should get a required property`() {
        // given
        val requiredType = String::class.java
        val propertyName = "name"
        val propertyValue = "some value"

        // when
        val result = defaultInjector.getRequiredProperty(propertyName, requiredType)

        // then
        assertThat(result.javaClass).isSameAs(requiredType)
        assertThat(result).isEqualTo(propertyValue)
    }

    @Test
    fun `should get a bean of the given type`() {
        // given
        val requiredType = ClassToInject::class.java

        // when
        val result = defaultInjector.getBean(requiredType)

        // then
        assertThat(result.javaClass).isSameAs(requiredType)
    }

    @Test
    fun `should get a bean when qualifier is provided`() {
        // given
        val requiredType = InterfaceToInject::class.java

        // when
        val result = defaultInjector.getBean(requiredType, Qualifiers.byName("classToInject"))

        // then
        assertThat(result.javaClass).isSameAs(ClassToInject::class.java)
    }

    @Test
    fun `should find a bean of the given type`() {
        // given
        val requiredType = ClassToInject::class.java

        // when
        val result = defaultInjector.findBean(requiredType)

        // then
        assertThat(result.get().javaClass).isSameAs(requiredType)
    }

    @Test
    fun `should find a bean of the given type when qualifier is provided`() {
        // given
        val requiredType = InterfaceToInject::class.java

        // when
        val result = defaultInjector.findBean(requiredType, Qualifiers.byName("otherClassToInject"))

        // then
        assertThat(result.get().javaClass).isSameAs(OtherClassToInject::class.java)
    }

    @Test
    fun `should get all beans of the given type`() {
        // given
        val requiredType = InterfaceToInject::class.java

        // when
        val result = defaultInjector.getBeansOfType(requiredType)

        // then
        assertThat { result.size == 2 }
        assertThat(result.map { it.javaClass }).containsAll(ClassToInject::class.java, OtherClassToInject::class.java)
    }

    @Test
    fun `should get all beans of the given type when qualifier is provided`() {
        // given
        val requiredType = InterfaceToInject::class.java

        // when
        val result = defaultInjector.getBeansOfType(requiredType, Qualifiers.byName("classToInject"))

        // then
        assertThat { result.size == 1 }
        assertThat(result.map { it.javaClass }).containsAll(ClassToInject::class.java)
    }

}