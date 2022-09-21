/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

@file:Suppress("UNUSED_PARAMETER")

package io.qalipsis.api.processors.scenario

import io.qalipsis.api.annotations.Property
import io.qalipsis.api.annotations.Scenario
import jakarta.inject.Named
import java.time.Duration
import java.util.Optional

internal val injectedForMethodOutsideAClass = mutableMapOf<String, Any?>()
internal val injectedIntoConstructor = mutableMapOf<String, Any?>()
internal val injectedIntoScenarioOfClass = mutableMapOf<String, Any?>()
internal val injectedIntoScenarioOfObject = mutableMapOf<String, Any?>()

@Scenario(name = "a-method-outside-a-class", description = "Scenario in a file", version = "0.1")
internal fun aMethodOutsideAClass(
    classToInject: ClassToInject,
    mayBeOtherClassToInject: Optional<OtherClassToInject>,
    @Named("myInjectable") namedInterfaceToInject: InterfaceToInject,
    injectables: List<InterfaceToInject>,
    @Named("myInjectable") namedInjectables: List<InterfaceToInject>,
    @Property(name = "this-is-a-test") property: Duration,
    @Property(name = "this-is-another-test", orElse = "10") propertyWithDefaultValue: Int,
    @Property(name = "this-is-yet-another-test") mayBeProperty: Optional<String>
) {
    injectedForMethodOutsideAClass["classToInject"] = classToInject
    injectedForMethodOutsideAClass["mayBeOtherClassToInject"] = mayBeOtherClassToInject
    injectedForMethodOutsideAClass["namedInterfaceToInject"] = namedInterfaceToInject
    injectedForMethodOutsideAClass["injectables"] = injectables
    injectedForMethodOutsideAClass["namedInjectables"] = namedInjectables
    injectedForMethodOutsideAClass["property"] = property
    injectedForMethodOutsideAClass["propertyWithDefaultValue"] = propertyWithDefaultValue
    injectedForMethodOutsideAClass["mayBeProperty"] = mayBeProperty
}

internal class ScenarioClass(
    classToInject: ClassToInject,
    mayBeOtherClassToInject: Optional<OtherClassToInject>,
    @Named("myInjectable") namedInterfaceToInject: InterfaceToInject,
    injectables: List<InterfaceToInject>,
    @Named("myInjectable") namedInjectables: List<InterfaceToInject>,
    @Property(name = "this-is-a-test") property: Duration,
    @Property(name = "this-is-another-test", orElse = "10") propertyWithDefaultValue: Int,
    @Property(name = "this-is-yet-another-test") mayBeProperty: Optional<String>
) {

    init {
        injectedIntoConstructor["classToInject"] = classToInject
        injectedIntoConstructor["mayBeOtherClassToInject"] = mayBeOtherClassToInject
        injectedIntoConstructor["namedInterfaceToInject"] = namedInterfaceToInject
        injectedIntoConstructor["injectables"] = injectables
        injectedIntoConstructor["namedInjectables"] = namedInjectables
        injectedIntoConstructor["property"] = property
        injectedIntoConstructor["propertyWithDefaultValue"] = propertyWithDefaultValue
        injectedIntoConstructor["mayBeProperty"] = mayBeProperty
    }

    @Scenario("a-method-inside-a-class", description = "Scenario in a class", version = "0.2.3")
    fun aMethodInsideAClass(
        classToInject: ClassToInject,
        mayBeOtherClassToInject: Optional<OtherClassToInject>,
        @Named("myInjectable") namedInterfaceToInject: InterfaceToInject,
        injectables: List<InterfaceToInject>,
        @Named("myInjectable") namedInjectables: List<InterfaceToInject>,
        @Property(name = "this-is-a-test") property: Duration,
        @Property(name = "this-is-another-test", orElse = "10") propertyWithDefaultValue: Int,
        @Property(name = "this-is-yet-another-test") mayBeProperty: Optional<String>
    ) {
        injectedIntoScenarioOfClass["classToInject"] = classToInject
        injectedIntoScenarioOfClass["mayBeOtherClassToInject"] = mayBeOtherClassToInject
        injectedIntoScenarioOfClass["namedInterfaceToInject"] = namedInterfaceToInject
        injectedIntoScenarioOfClass["injectables"] = injectables
        injectedIntoScenarioOfClass["namedInjectables"] = namedInjectables
        injectedIntoScenarioOfClass["property"] = property
        injectedIntoScenarioOfClass["propertyWithDefaultValue"] = propertyWithDefaultValue
        injectedIntoScenarioOfClass["mayBeProperty"] = mayBeProperty
    }

    object ScenarioDeclaration {

        @Scenario("a-method-inside-an-object", version = "1.34")
        fun aMethodInsideAnObject(
            classToInject: ClassToInject,
            mayBeOtherClassToInject: Optional<OtherClassToInject>,
            @Named("myInjectable") namedInterfaceToInject: InterfaceToInject,
            injectables: List<InterfaceToInject>,
            @Named("myInjectable") namedInjectables: List<InterfaceToInject>,
            @Property(name = "this-is-a-test") property: Duration,
            @Property(name = "this-is-another-test", orElse = "10") propertyWithDefaultValue: Int,
            @Property(name = "this-is-yet-another-test") mayBeProperty: Optional<String>
        ) {
            injectedIntoScenarioOfObject["classToInject"] = classToInject
            injectedIntoScenarioOfObject["mayBeOtherClassToInject"] = mayBeOtherClassToInject
            injectedIntoScenarioOfObject["namedInterfaceToInject"] = namedInterfaceToInject
            injectedIntoScenarioOfObject["injectables"] = injectables
            injectedIntoScenarioOfObject["namedInjectables"] = namedInjectables
            injectedIntoScenarioOfObject["property"] = property
            injectedIntoScenarioOfObject["propertyWithDefaultValue"] = propertyWithDefaultValue
            injectedIntoScenarioOfObject["mayBeProperty"] = mayBeProperty
        }
    }
}
