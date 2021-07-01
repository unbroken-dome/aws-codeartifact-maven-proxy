package org.unbrokendome.awscodeartifact.mavenproxy.netty.handler

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import org.slf4j.LoggerFactory


/**
 * Logs HTTP requests and response statuses as short one-liners.
 */
internal class HttpAccessLoggingHandler : ChannelDuplexHandler() {

    companion object {
        private val logger = LoggerFactory.getLogger(HttpAccessLoggingHandler::class.java)
    }

    private var requestMethod: HttpMethod? = null
    private var requestPath: String? = null


    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is HttpRequest) {
            requestMethod = msg.method()
            requestPath = msg.uri()
        }
        ctx.fireChannelRead(msg)
    }


    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {

        if (msg is HttpResponse) {
            logger.info("{} {} -> {} {}", requestMethod, requestPath, msg.status().code(), msg.status().reasonPhrase())
            requestMethod = null
            requestPath = null
        }

        ctx.write(msg, promise)
    }
}
