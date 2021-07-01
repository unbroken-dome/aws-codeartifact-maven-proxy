package org.unbrokendome.awscodeartifact.mavenproxy.netty.handler

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.*


/**
 * Handles the HTTP `Connection` header on the client side.
 *
 * The `keepAlive` value passed in the constructor parameter is added to all outbound requests
 * that do not already contain the `Connection` header. If they do contain one, then its value
 * is left untouched.
 *
 * When a response is received, the value of its `Connection` header is noted (or the protocol
 * default if it is not present, i.e. `Keep-Alive` for HTTP/1.1). Once the response is complete,
 * indicated by a [LastHttpContent] and a READ-COMPLETE event, the connection is closed if
 * indicated by the response.
 *
 * @param keepAlive whether to request connection keep-alive
 */
internal class HttpConnectionCloseClientHandler(
    keepAlive: Boolean
) : ChannelDuplexHandler() {

    private val connectionHeaderValue =
        if (keepAlive) HttpHeaderValues.KEEP_ALIVE else HttpHeaderValues.CLOSE

    private var keepAliveAfterResponse: Boolean = false
    private var responseReceived: Boolean = false


    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {

        if (msg is HttpRequest) {

            // If request already contains a Connection header, honor it
            val requestConnectionHeader = msg.headers().get(HttpHeaderNames.CONNECTION)
            if (requestConnectionHeader == null) {
                msg.headers().add(HttpHeaderNames.CONNECTION, connectionHeaderValue)
            }
            responseReceived = false
        }

        ctx.write(msg, promise)
    }


    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {

        if (msg is HttpResponse) {
            keepAliveAfterResponse = HttpUtil.isKeepAlive(msg)
        }

        ctx.fireChannelRead(msg)

        if (msg is LastHttpContent) {
            responseReceived = true
        }
    }


    override fun channelReadComplete(ctx: ChannelHandlerContext) {

        ctx.fireChannelReadComplete()

        if (responseReceived) {
            if (!keepAliveAfterResponse) {
                ctx.close()
            }

            responseReceived = false
        }
    }
}
