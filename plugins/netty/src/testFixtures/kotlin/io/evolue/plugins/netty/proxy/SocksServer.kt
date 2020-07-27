package io.evolue.plugins.netty.proxy

import io.evolue.plugins.netty.Server
import io.evolue.plugins.netty.ServerUtils
import io.evolue.plugins.netty.tcp.TcpServer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.example.socksproxy.SocksServerInitializer
import java.net.InetSocketAddress


/**
 * Instance of a local Socks server for test purpose.
 *
 * @author Eric Jessé
 */
class SocksServer private constructor(
    port: Int,
    bootstrap: ServerBootstrap,
    eventGroups: Collection<NioEventLoopGroup>
) : TcpServer(port, bootstrap, eventGroups) {

    companion object {

        /**
         * Build a new Socks proxy server. The server is not started yet once built. You can use it either as a JUnit extension or start and stop manually.
         *
         * @param host the host name to listen, or null (default) or all have to be read.
         * @param port the port to use, or null (default) to use a random available one.
         */
        fun new(host: String? = null, port: Int? = null): Server {
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

            val bootstrap = ServerBootstrap().group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .localAddress(inetSocketAddress)
                .childHandler(SocksServerInitializer())
            return SocksServer(inetSocketAddress.port, bootstrap,
                listOf(bossGroup, workerGroup))
        }
    }

}