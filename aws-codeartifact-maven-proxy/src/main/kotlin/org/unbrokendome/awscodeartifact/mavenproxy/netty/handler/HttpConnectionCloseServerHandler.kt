package org.unbrokendome.awscodeartifact.mavenproxy.netty.handler

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.*


/**
 * Handles the HTTP `Connection` header on the server side.
 *
 * The `Connection` header is added to any outgoing response if it is not present in the response:
 *
 * - If the `Connection` header was present in the corresponding request, then the same value will
 *   be used for the response.
 * - If no `Connection` header was present in the corresponding request, the protocol default
 *   (`Keep-Alive` for HTTP/1.1 or `Close` for HTTP/1.0) will be used.
 *
 * After the response is sent, the connection will be closed if the `Connection` header has
 * the value `Close`.
 */
internal class HttpConnectionCloseServerHandler : ChannelDuplexHandler() {

    private var keepAliveRequested: Boolean = false
    private var keepAliveAfterResponse: Boolean = false
    private var responseWritten = false


    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is HttpRequest) {
            keepAliveRequested = HttpUtil.isKeepAlive(msg)
            responseWritten = false
        }
        ctx.fireChannelRead(msg)
    }


    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {

        if (msg is HttpResponse) {
            // If response already contains a Connection header, honor it
            val responseConnectionHeader = msg.headers().get(HttpHeaderNames.CONNECTION)

            keepAliveAfterResponse = if (responseConnectionHeader != null) {
                HttpHeaderValues.KEEP_ALIVE.contentEquals(responseConnectionHeader)
            } else keepAliveRequested
        }

        ctx.write(msg, promise)

        if (msg is LastHttpContent) {
            responseWritten = true
        }
    }


    override fun flush(ctx: ChannelHandlerContext) {
        if (responseWritten && !keepAliveAfterResponse) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                .addListener(ChannelFutureListener.CLOSE)
        } else {
            ctx.flush()
        }
        responseWritten = false
    }
}
