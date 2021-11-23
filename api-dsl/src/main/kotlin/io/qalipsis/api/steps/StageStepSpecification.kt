package io.qalipsis.api.steps

import cool.graph.cuid.Cuid
import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry

/**
 * Parent specification for the step implied in a [io.qalipsis.core.factory.steps.GroupStep].
 *
 * @author Eric Jessé
 */
@Introspected
abstract class StageStepSpecification<INPUT, OUTPUT> :
    AbstractStepSpecification<INPUT, OUTPUT, StageStepSpecification<INPUT, OUTPUT>>()

/**
 * Specification for a [io.qalipsis.core.factory.steps.GroupStep].
 *
 * @author Eric Jessé
 */
@Introspected
class StageStepStartSpecification<INPUT> : StageStepSpecification<INPUT, INPUT>()

/**
 * Specification for a [io.qalipsis.core.factory.steps.GroupStep] to forward the configuration to the start step.
 *
 * @author Eric Jessé
 */
@Introspected
class StageStepEndSpecification<START_INPUT, OUTPUT>(val start: StageStepSpecification<START_INPUT, START_INPUT>) :
    StageStepSpecification<START_INPUT, OUTPUT>() {

    override fun configure(
        specification: StageStepSpecification<START_INPUT, OUTPUT>.() -> Unit
    ): StepSpecification<START_INPUT, OUTPUT, *> {
        // Applies the configuration to the step specification opening the group.
        @Suppress("UNCHECKED_CAST")
        start.configure(specification as StageStepSpecification<START_INPUT, START_INPUT>.() -> Unit)
        return this
    }
}

/**
 * Creates a group of steps, providing the ability to configure repetition, retry for the whole group.
 *
 * @param stageName name of the stage, defaults to a random name if left empty.
 * @param steps the rule to convert the input into the output.
 *
 * @author Eric Jessé
 */
fun <OUTPUT> ScenarioSpecification.stage(
    stageName: String = "",
    steps: ScenarioSpecification.() -> StepSpecification<*, OUTPUT, *>
): StageStepSpecification<Unit, OUTPUT> {
    val stepRegistry = this as StepSpecificationRegistry

    // Shelves the list of the current roots.
    val rootsBeforeStage = listOf(*stepRegistry.rootSteps.toTypedArray())
    // Adds all the step specifications from the stage as a new root of the scenario.
    val tail = this.steps()
    // Identifies the brand new root.
    val addedRootStep = (stepRegistry.rootSteps - rootsBeforeStage).first()

    // Replaces the new root by a stage start step.

    // We actually creates a start and end boundaries, surrounding the specifications to group.
    val groupStartName = if (stageName.isBlank()) Cuid.createCuid() else stageName
    val start = StageStepStartSpecification<Unit>()
    start.scenario = this
    start.name = groupStartName

    // Replace the brand new root by the start of the stage.
    stepRegistry.insertRoot(start, addedRootStep)

    // The tail of the group has a forced step specification to mark the end boundary of the group.
    val end = StageStepEndSpecification<Unit, OUTPUT>(start)
    end.scenario = this
    tail.add(end)

    // The end boundary of the group is returned to that next step specifications can be added to the group.
    return end
}

/**
 * Creates a group of steps, providing the ability to configure repetition, retry for the whole group.
 *
 * @param stageName name of the stage, defaults to a random name if left empty.
 * @param steps the rule to convert the input into the output.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.stage(
    stageName: String = "",
    steps: StepSpecification<*, INPUT, *>.() -> StepSpecification<*, OUTPUT, *>
): StageStepSpecification<INPUT, OUTPUT> {

    // We actually creates a start and end boundaries, surrounding the specifications to group.
    val groupStartName = if (stageName.isBlank()) Cuid.createCuid() else stageName
    val start = StageStepStartSpecification<INPUT>()
    start.scenario = this.scenario
    this.add(start)
    start.name = groupStartName

    // Adds all the step specifications from the stage as next of the start.
    val tail = start.steps()

    // The tail of the group has a forced step specification to mark the end boundary of the group.
    val end = StageStepEndSpecification<INPUT, OUTPUT>(start)
    end.scenario = this.scenario
    tail.add(end)

    // The end boundary of the group is returned to that next step specifications can be added to the group.
    return end
}
