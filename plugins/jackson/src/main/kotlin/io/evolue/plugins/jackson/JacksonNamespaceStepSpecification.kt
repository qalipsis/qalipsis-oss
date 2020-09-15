package io.evolue.plugins.jackson

import io.evolue.api.scenario.ScenarioSpecification
import io.evolue.api.steps.AbstractPluginStepWrapper
import io.evolue.api.steps.AbstractScenarioSpecificationWrapper
import io.evolue.api.steps.StepSpecification


/**
 * Interface of a Jackson step to define it in the appropriate step specifications namespace.
 *
 * @author Eric Jessé
 */
interface JacksonNamespaceStepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF>

/**
 * Step wrapper to enter the namespace for the Jackson step specifications.
 *
 * @author Eric Jessé
 */
class JacksonNamespaceStepSpecificationImpl<INPUT, OUTPUT>(wrappedStepSpec: StepSpecification<INPUT, OUTPUT, *>) :
    AbstractPluginStepWrapper<INPUT, OUTPUT>(wrappedStepSpec),
    JacksonNamespaceStepSpecification<INPUT, OUTPUT, AbstractPluginStepWrapper<INPUT, OUTPUT>>

fun <INPUT, OUTPUT> StepSpecification<INPUT, OUTPUT, *>.jackson(): JacksonNamespaceStepSpecification<INPUT, OUTPUT, *> =
    JacksonNamespaceStepSpecificationImpl(this)

/**
 * Scenario wrapper to enter the namespace for the Jackson step specifications.
 *
 * @author Eric Jessé
 */
class JacksonNamespaceScenarioSpecification(scenario: ScenarioSpecification) :
    AbstractScenarioSpecificationWrapper(scenario)

fun ScenarioSpecification.jackson() = JacksonNamespaceScenarioSpecification(this)
