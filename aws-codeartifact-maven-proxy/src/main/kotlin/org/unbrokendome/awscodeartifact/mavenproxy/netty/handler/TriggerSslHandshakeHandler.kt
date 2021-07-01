package org.unbrokendome.awscodeartifact.mavenproxy.netty.handler

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import org.slf4j.LoggerFactory


/**
 * An inbound channel handler that triggers an SSL handshake [ChannelHandlerContext.read] once
 * it becomes active, and delays propagation of the "channel active" event to downstream handlers
 * until the SSL handshake is complete.
 *
 * Intended to be used after an [SslHandler] on channels where "auto-read" mode is off.
 */
@ChannelHandler.Sharable
internal class TriggerSslHandshakeHandler : ChannelInboundHandlerAdapter() {

    private val logger = LoggerFactory.getLogger(javaClass)


    override fun channelActive(ctx: ChannelHandlerContext) {
        logger.debug("Triggering read to initiate SSL handshake")
        ctx.read()
    }


    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is SslHandshakeCompletionEvent) {
            logger.debug("SSL handshake completed, firing channel active notification")
            ctx.fireChannelActive()
        }
        ctx.fireUserEventTriggered(evt)
    }
}
