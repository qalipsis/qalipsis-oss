/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.injector

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
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
        assertThat(result.get().javaClass).isSameInstanceAs(requiredType)
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
        assertThat(result.get().javaClass).isSameInstanceAs(requiredType)
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
        assertThat(result.get().javaClass).isSameInstanceAs(requiredType)
        assertThat(result.get()).isSameInstanceAs(propertyValue)
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
        assertThat(result.javaClass).isSameInstanceAs(requiredType)
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
        assertThat(result.javaClass).isSameInstanceAs(requiredType)
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
        assertThat(result.javaClass).isSameInstanceAs(requiredType)
        assertThat(result).isEqualTo(propertyValue)
    }

    @Test
    fun `should get a bean of the given type`() {
        // given
        val requiredType = ClassToInject::class.java

        // when
        val result = defaultInjector.getBean(requiredType)

        // then
        assertThat(result.javaClass).isSameInstanceAs(requiredType)
    }

    @Test
    fun `should get a bean when qualifier is provided`() {
        // given
        val requiredType = InterfaceToInject::class.java

        // when
        val result = defaultInjector.getBean(requiredType, Qualifiers.byName("classToInject"))

        // then
        assertThat(result.javaClass).isSameInstanceAs(ClassToInject::class.java)
    }

    @Test
    fun `should find a bean of the given type`() {
        // given
        val requiredType = ClassToInject::class.java

        // when
        val result = defaultInjector.findBean(requiredType)

        // then
        assertThat(result.get().javaClass).isSameInstanceAs(requiredType)
    }

    @Test
    fun `should find a bean of the given type when qualifier is provided`() {
        // given
        val requiredType = InterfaceToInject::class.java

        // when
        val result = defaultInjector.findBean(requiredType, Qualifiers.byName("otherClassToInject"))

        // then
        assertThat(result.get().javaClass).isSameInstanceAs(OtherClassToInject::class.java)
    }

    @Test
    fun `should get all beans of the given type`() {
        // given
        val requiredType = InterfaceToInject::class.java

        // when
        val result = defaultInjector.getBeansOfType(requiredType)

        // then
        assertThat(result).hasSize(2)
        assertThat(result.map { it.javaClass }).containsExactlyInAnyOrder(
            ClassToInject::class.java,
            OtherClassToInject::class.java
        )
    }

    @Test
    fun `should get all beans of the given type when qualifier is provided`() {
        // given
        val requiredType = InterfaceToInject::class.java

        // when
        val result = defaultInjector.getBeansOfType(requiredType, Qualifiers.byName("classToInject"))

        // then
        assertThat(result).hasSize(1)
        assertThat(result.map { it.javaClass }).containsExactlyInAnyOrder(ClassToInject::class.java)
    }

}