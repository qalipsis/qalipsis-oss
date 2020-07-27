package io.evolue.plugins.netty.udp

import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.plugins.netty.Server
import io.evolue.plugins.netty.ServerUtils
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBufUtil.getBytes
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import java.net.InetSocketAddress


/**
 * Instance of a local UDP server for test purpose.
 *
 * @author Eric Jessé
 */
open class UdpServer internal constructor(
    override val port: Int,
    private val bootstrap: Bootstrap,
    private val eventGroups: Collection<NioEventLoopGroup>
) : Server {

    private var channelFuture: ChannelFuture? = null

    override fun start() {
        if (channelFuture == null) {
            channelFuture = bootstrap.bind().sync()
        }
    }

    override fun stop() {
        channelFuture?.let { chFuture ->
            try {
                chFuture.channel().close().sync()
            } finally {
                eventGroups.forEach { group -> group.shutdownGracefully() }
            }
            channelFuture = null
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

        /**
         * Build a new TCP server. The server is not started yet once built. You can use it either as a JUnit extension or start and stop manually.
         *
         * @param host the host name to listen, or null (default) or all have to be read.
         * @param port the port to use, or null (default) to use a random available one.
         * @param enableTls enables TLS on the server. False by default.
         * @param tlsProtocols list of TLS protocols to support, by default all the ones supported by Netty.
         * @param handler conversion operation of the received payload, default is a simple echo.
         */
        fun new(host: String? = null, port: Int? = null, handler: (ByteArray) -> ByteArray = { it }): Server {
            val bossGroup = NioEventLoopGroup()

            val inetSocketAddress = if (host.isNullOrBlank()) {
                if (port != null) {
                    InetSocketAddress(port)
                } else {
                    InetSocketAddress(ServerUtils.availableTcpPort())
                }
            } else {
                if (port != null) {
                    InetSocketAddress(host, port)
                } else {
                    InetSocketAddress(host, ServerUtils.availableTcpPort())
                }
            }

            val bootstrap = Bootstrap()
                .group(bossGroup)
                .channel(NioDatagramChannel::class.java)
                .localAddress(inetSocketAddress)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.AUTO_READ, true)
                .handler(object : ChannelInitializer<NioDatagramChannel>() {

                    override fun initChannel(ch: NioDatagramChannel) {
                        val pipeline = ch.pipeline()
                        pipeline.addLast("request", object : SimpleChannelInboundHandler<DatagramPacket>() {

                            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                                log.error(cause.message, cause)
                            }

                            override fun channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket) {
                                val input = getBytes(msg.content())
                                val output = DatagramPacket(Unpooled.wrappedBuffer(handler(input)), msg.sender(),
                                    msg.recipient())
                                ctx.writeAndFlush(output).addListener {
                                    if (!it.isSuccess) {
                                        log.error(it.cause().message, it.cause())
                                    }
                                }
                            }

                        })
                    }
                })
            return UdpServer(inetSocketAddress.port, bootstrap, listOf(bossGroup))
        }
    }

}