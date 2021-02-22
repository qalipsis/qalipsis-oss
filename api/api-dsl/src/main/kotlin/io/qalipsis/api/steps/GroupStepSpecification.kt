package io.qalipsis.api.steps

import cool.graph.cuid.Cuid
import io.micronaut.core.annotation.Introspected

/**
 * Parent specification for the step implied in a [io.qalipsis.core.factories.steps.GroupStep].
 *
 * @author Eric Jessé
 */
@Introspected
abstract class GroupStepSpecification<INPUT, OUTPUT> :
    AbstractStepSpecification<INPUT, OUTPUT, GroupStepSpecification<INPUT, OUTPUT>>()

/**
 * Specification for a [io.qalipsis.core.factories.steps.GroupStep].
 *
 * @author Eric Jessé
 */
@Introspected
class GroupStepStartSpecification<INPUT> : GroupStepSpecification<INPUT, INPUT>()

/**
 * Specification for a [io.qalipsis.core.factories.steps.GroupStep] to forward the configuration to the start step.
 *
 * @author Eric Jessé
 */
@Introspected
class GroupStepEndSpecification<START_INPUT, OUTPUT>(val start: GroupStepSpecification<START_INPUT, START_INPUT>) :
    GroupStepSpecification<START_INPUT, OUTPUT>() {

    override fun configure(
        specification: GroupStepSpecification<START_INPUT, OUTPUT>.() -> Unit): StepSpecification<START_INPUT, OUTPUT, *> {
        // Applies the configuration to the step specification opening the group.
        @Suppress("UNCHECKED_CAST")
        start.configure(specification as GroupStepSpecification<START_INPUT, START_INPUT>.() -> Unit)
        return this
    }
}

/**
 * Groups steps all-together, providing the ability to configure repetition, retry for the whole group instead of unique step.
 *
 * @param steps the rule to convert the input into the output.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.group(
    steps: StepSpecification<*, INPUT, *>.() -> StepSpecification<*, OUTPUT, *>
): GroupStepSpecification<INPUT, OUTPUT> {

    // We actually creates a start and end boundaries, surrounding the specifications to
    val groupStartName = Cuid.createCuid()
    val start = GroupStepStartSpecification<INPUT>()
    start.scenario = this.scenario
    this.add(start)
    start.name = groupStartName

    // Adds all the step specifications from the group as next of the start.
    val tail = start.steps()

    // The tail of the group has a forced step specification to mark the end boundary of the group.
    val end = GroupStepEndSpecification<INPUT, OUTPUT>(start)
    end.scenario = this.scenario
    tail.add(end)

    // The end boundary of the group is returned to that next step specifications can be added to the group.
    return end
}
