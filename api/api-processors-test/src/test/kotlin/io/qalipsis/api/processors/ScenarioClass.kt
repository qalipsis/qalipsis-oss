@file:Suppress("UNUSED_PARAMETER")

package io.qalipsis.api.processors

import io.qalipsis.api.annotations.Property
import io.qalipsis.api.annotations.Scenario
import java.time.Duration
import java.util.Optional

import javax.inject.Named

internal val injectedForMethodOutsideAClass = mutableMapOf<String, Any?>()
internal val injectedIntoConstructor = mutableMapOf<String, Any?>()
internal val injectedIntoScenarioOfClass = mutableMapOf<String, Any?>()
internal val injectedIntoScenarioOfObject = mutableMapOf<String, Any?>()

@Scenario
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

    @Scenario
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

        @Scenario
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
