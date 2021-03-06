package org.unbrokendome.awscodeartifact.mavenproxy.netty.handler

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponse
import io.netty.util.AsciiString


/**
 * Adds a `Server` or `Via` header to outgoing responses.
 *
 * If an outgoing response already contains a `Server` header, then it is assumed the response
 * is proxied by this server, so a `Via` header is added to the response.
 *
 * If an outgoing response does not contain a `Server` header, then it is assumed the response
 * was generated by this server, so a `Server` header is added.
 */
@ChannelHandler.Sharable
internal class HttpServerHeaderHandler : ChannelOutboundHandlerAdapter() {

    companion object {

        private val ServerHeaderValue = AsciiString.of("AWS CodeArtifact Maven Proxy")
        private val ViaHeaderValue = AsciiString.of("awscodeartifact-maven-proxy")
    }


    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {

        if (msg is HttpResponse) {
            val headers = msg.headers()
            if (headers.contains(HttpHeaderNames.SERVER)) {
                headers.add(HttpHeaderNames.VIA, ViaHeaderValue)
            } else {
                headers.set(HttpHeaderNames.SERVER, ServerHeaderValue)
            }
        }

        ctx.write(msg, promise)
    }
}
