package org.unbrokendome.awscodeartifact.mavenproxy

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import org.unbrokendome.awscodeartifact.mavenproxy.netty.handler.HttpAccessLoggingHandler
import org.unbrokendome.awscodeartifact.mavenproxy.netty.handler.HttpConnectionCloseServerHandler
import org.unbrokendome.awscodeartifact.mavenproxy.netty.handler.HttpServerHeaderHandler
import org.unbrokendome.awscodeartifact.mavenproxy.netty.handler.HttpStripResponseHeadersHandler
import org.unbrokendome.awscodeartifact.mavenproxy.wiretap.WiretapLoggerNames


/**
 * Initializer for new proxy frontend channels.
 *
 * @param requestHandler a [ProxyRequestHandler] for handling incoming requests
 */
internal class ProxyFrontendChannelInitializer(
    wiretapLogLevel: LogLevel = LogLevel.TRACE,
    private val requestHandler: ProxyRequestHandler
) : ChannelInitializer<SocketChannel>() {

    private val httpLoggingHandler = LoggingHandler(WiretapLoggerNames.FrontendHttp, wiretapLogLevel)
    private val httpServerHeaderHandler = HttpServerHeaderHandler()
    private val httpStripResponseHeadersHandler = HttpStripResponseHeadersHandler(HttpHeaderNames.CONNECTION)

    override fun initChannel(ch: SocketChannel) {
        ch.pipeline().addLast(
            HttpServerCodec(),
            HttpAccessLoggingHandler(),
            httpLoggingHandler,
            HttpConnectionCloseServerHandler(),
            httpServerHeaderHandler,
            httpStripResponseHeadersHandler,
            ProxyFrontendHandler(requestHandler)
        )
    }
}
