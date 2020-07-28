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
interface NettyPluginSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF>

/**
 * Step wrapper to append to all steps before using a step from the Netty plugin.
 *
 * @author Eric Jessé
 */
class NettyPluginSpecificationImpl<INPUT, OUTPUT>(wrappedStepSpec: StepSpecification<INPUT, OUTPUT, *>) :
    AbstractPluginStepWrapper<INPUT, OUTPUT>(wrappedStepSpec),
    NettyPluginSpecification<INPUT, OUTPUT, AbstractPluginStepWrapper<INPUT, OUTPUT>>

fun <INPUT, OUTPUT> StepSpecification<INPUT, OUTPUT, *>.netty(): NettyPluginSpecification<INPUT, OUTPUT, *> =
    NettyPluginSpecificationImpl(this)

/**
 * Scenario wrapper to append to a scenario before using a step from the Netty plugin.
 *
 * @author Eric Jessé
 */
class NettyScenarioSpecification(scenario: ScenarioSpecification) : AbstractScenarioSpecificationWrapper(scenario)

fun ScenarioSpecification.netty() = NettyScenarioSpecification(this)
