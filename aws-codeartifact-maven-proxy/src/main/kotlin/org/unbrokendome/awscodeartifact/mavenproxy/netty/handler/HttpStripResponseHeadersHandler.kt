package org.unbrokendome.awscodeartifact.mavenproxy.netty.handler

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.HttpResponse


/**
 * Outbound channel handler that strips certain headers from a response.
 *
 * Used for proxied responses where certain headers from a backend response do not make sense
 * to be passed on into the frontend response.
 */
@ChannelHandler.Sharable
internal class HttpStripResponseHeadersHandler(
    private val headersToRemove: List<CharSequence>
) : ChannelOutboundHandlerAdapter() {

    constructor(vararg headersToRemove: CharSequence) : this(headersToRemove.asList())


    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {

        if (msg is HttpResponse) {
            val headers = msg.headers()
            for (headerToRemove in headersToRemove) {
                headers.remove(headerToRemove)
            }
        }

        ctx.write(msg, promise)
    }
}
