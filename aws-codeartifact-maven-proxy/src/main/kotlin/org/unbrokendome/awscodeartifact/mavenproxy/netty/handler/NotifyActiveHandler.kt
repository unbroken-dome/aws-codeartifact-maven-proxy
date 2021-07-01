package org.unbrokendome.awscodeartifact.mavenproxy.netty.handler

import io.netty.channel.*
import io.netty.util.AttributeKey


/**
 * An inbound channel handler that provides an "active" future, and signals it when the
 * channel becomes active.
 *
 * This provides some flexibility over [Channel.isActive] in the case that the propagation
 * of the "channel active" event is deferred by upstream handlers.
 *
 * The "active" future is made available through the [ActiveFutureAttributeKey] attribute.
 */
@ChannelHandler.Sharable
internal class NotifyActiveHandler : ChannelInboundHandlerAdapter() {

    companion object {

        val ActiveFutureAttributeKey: AttributeKey<ChannelFuture> =
            AttributeKey.newInstance("activeFuture")
    }


    override fun channelRegistered(ctx: ChannelHandlerContext) {

        val activePromise = ctx.newPromise()
        ctx.channel().attr(ActiveFutureAttributeKey).set(activePromise)

        ctx.fireChannelRegistered()
    }


    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.fireChannelActive()

        val activePromise = ctx.channel().attr(ActiveFutureAttributeKey).get() as ChannelPromise?
        activePromise?.setSuccess()

        ctx.pipeline().remove(this)
    }
}
