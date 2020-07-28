package io.evolue.plugins.netty.tcp

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.steps.AbstractStep

/**
 * Step to close a TCP connection that was created in an earlier step and kept open.
 *
 * @author Eric Jessé
 */
internal class CloseTcpClientStep<I>(
    id: StepId,
    private val connectionOwner: TcpClientStep<*>
) : AbstractStep<I, I>(id, null) {

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.input.receive()
        connectionOwner.close(context)
        context.output.send(input)
    }

}
