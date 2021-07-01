package org.unbrokendome.awscodeartifact.mavenproxy.netty.handler

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOutboundInvoker
import org.slf4j.LoggerFactory


/**
 * An inbound channel handler that forwards all received messages to another target.
 *
 * It should be the last inbound handler in a pipeline, because further handlers will
 * not receive the message anymore.
 */
internal class ForwardingChannelInboundHandler(
    private val forwardTarget: ChannelOutboundInvoker
) : ChannelInboundHandlerAdapter() {

    companion object {
        private val logger = LoggerFactory.getLogger(ForwardingChannelInboundHandler::class.java)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        logger.debug("Received inbound message, forwarding to {}: {}", forwardTarget, msg)
        forwardTarget.write(msg)
    }


    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        logger.debug("Channel read complete, flush forwarding target {}", forwardTarget)
        forwardTarget.flush()
    }
}
