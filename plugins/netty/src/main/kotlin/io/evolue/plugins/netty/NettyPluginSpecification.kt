package io.evolue.plugins.netty

import io.evolue.api.scenario.ScenarioSpecification
import io.evolue.api.steps.AbstractPluginStepWrapper
import io.evolue.api.steps.AbstractScenarioSpecificationWrapper
import io.evolue.api.steps.StepSpecification

/**
 * Step wrapper to append to all steps before using a step from the Netty plugin.
 *
 * @author Eric Jessé
 */
class NettyPluginSpecification<I, O>(wrappedStepSpec: StepSpecification<I, O, *>) :
    AbstractPluginStepWrapper<I, O>(wrappedStepSpec)

fun <INPUT, OUTPUT> StepSpecification<INPUT, OUTPUT, *>.netty() = NettyPluginSpecification(this)

/**
 * Scenario wrapper to append to a scenario before using a step from the Netty plugin.
 *
 * @author Eric Jessé
 */
class NettyScenarioSpecification(scenario: ScenarioSpecification) : AbstractScenarioSpecificationWrapper(scenario)

fun ScenarioSpecification.netty() = NettyScenarioSpecification(this)
