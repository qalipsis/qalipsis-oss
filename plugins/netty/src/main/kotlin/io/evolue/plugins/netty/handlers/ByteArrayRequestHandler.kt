package io.evolue.plugins.netty.handlers

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Simple handler to write and read bytes from the channel.
 *
 * @author Eric Jessé
 */
internal class ByteArrayRequestHandler(
    private val inboundChannel: Channel<ByteArray>,
    private val resultChannel: Channel<Result<ByteArray>>
) : SimpleChannelInboundHandler<ByteArray>() {

    /**
     * Indicates if some data were already received (even if an empty [ByteArray]).
     */
    private var dataReceived = false

    /**
     * Buffer to keep the data while it was not completely received.
     */
    private var readBuffer = ByteArray(0)

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        resultChannel.offer(Result.failure(cause.cause ?: cause))
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray?) {
        if (msg == null) {
            return
        }
        dataReceived = true
        readBuffer += msg
        ReferenceCountUtil.release(msg)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        // When TLS is active, the read complete is called during the handshake without any received data.
        if (dataReceived) {
            resultChannel.offer(Result.success(readBuffer))
            resetBuffer()
        }
        super.channelReadComplete(ctx)
        // Prepare to read the next response from the server.
        ctx.read()
    }

    private fun resetBuffer() {
        readBuffer = ByteArray(0)
        dataReceived = false
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        GlobalScope.launch {
            for (b in inboundChannel) {
                try {
                    ctx.writeAndFlush(b).addListener {
                        if (!it.isSuccess) {
                            resultChannel.offer(Result.failure(it.cause()))
                        }
                    }
                } catch (e: Exception) {
                    resultChannel.send(Result.failure(e))
                }
            }
        }
        // Prepare to read the next response from the server.
        ctx.read()
    }

}
