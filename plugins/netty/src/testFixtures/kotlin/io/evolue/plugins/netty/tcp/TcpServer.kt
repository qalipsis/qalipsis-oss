package io.evolue.plugins.netty.tcp

import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.plugins.netty.Server
import io.evolue.plugins.netty.ServerUtils
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.util.SelfSignedCertificate
import java.net.InetSocketAddress


/**
 * Instance of a local TCP server for test purpose.
 *
 * @author Eric Jessé
 */
open class TcpServer internal constructor(
    override val port: Int,
    private val bootstrap: ServerBootstrap,
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
        fun new(host: String? = null, port: Int? = null, enableTls: Boolean = false,
            tlsProtocols: Array<String> = arrayOf(),
            handler: (ByteArray) -> ByteArray = { it }): Server {
            val bossGroup = NioEventLoopGroup()
            val workerGroup = NioEventLoopGroup()

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

            val sslSelfSignedCertificate = if (enableTls) SelfSignedCertificate() else null
            val sslContext = if (enableTls) SslContextBuilder.forServer(sslSelfSignedCertificate!!.certificate(),
                sslSelfSignedCertificate.privateKey()).build() else null

            val bootstrap = ServerBootstrap().group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .localAddress(inetSocketAddress)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.AUTO_READ, true)
                .childHandler(object : ChannelInitializer<SocketChannel>() {

                    override fun initChannel(ch: SocketChannel) {
                        val pipeline = ch.pipeline()
                        if (enableTls) {
                            val sslEngine = sslContext!!.newEngine(ch.alloc())
                            if (tlsProtocols.isNotEmpty()) {
                                sslEngine.enabledProtocols = tlsProtocols
                            } else {
                                sslEngine.enabledProtocols = sslEngine.supportedProtocols
                            }
                            pipeline.addFirst("ssl", SslHandler(sslEngine))
                        }
                        pipeline.addLast("request", object : SimpleChannelInboundHandler<ByteBuf>() {

                            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                                log.error(cause.message, cause)
                            }

                            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
                                val input = ByteBufUtil.getBytes(msg)
                                val output = Unpooled.wrappedBuffer(handler(input))
                                ctx.writeAndFlush(output).addListener {
                                    if (!it.isSuccess) {
                                        log.error(it.cause().message, it.cause())
                                    }
                                }
                            }

                        })
                    }
                })

            return TcpServer(inetSocketAddress.port, bootstrap,
                listOf(bossGroup, workerGroup))
        }
    }

}