package org.unbrokendome.awscodeartifact.mavenproxy.netty.pool

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelPromise
import io.netty.channel.pool.ChannelHealthChecker
import io.netty.channel.pool.ChannelPool
import io.netty.channel.pool.ChannelPoolHandler
import io.netty.channel.pool.SimpleChannelPool
import io.netty.util.AttributeKey
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.Promise
import org.slf4j.LoggerFactory


/**
 * A specialization of [SimpleChannelPool] that tags each channel so it "remembers" which
 * pool it belongs to. This simplifies releasing channels after use.
 *
 * Note that [SimpleChannelPool] already adds its own attribute to the channel, but unfortunately
 * it is not made public so we cannot re-use it.
 */
internal class TaggingChannelPool(
    bootstrap: Bootstrap,
    handler: ChannelPoolHandler,
    healthChecker: ChannelHealthChecker = ChannelHealthChecker.ACTIVE,
    releaseHealthCheck: Boolean = true,
    lastRecentUsed: Boolean = true
) : AsyncCloseableSimpleChannelPool(bootstrap, handler, healthChecker, releaseHealthCheck, lastRecentUsed) {

    companion object {
        val PoolAttributeKey: AttributeKey<ChannelPool> =
            AttributeKey.newInstance("channelPool")
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun connectChannel(bs: Bootstrap): ChannelFuture {
        val taggingBootstrap = bs.attr(PoolAttributeKey, this)
        logger.debug("Connecting to {}", bs.config().remoteAddress())
        return taggingBootstrap.connect()
    }
}


/**
 * Get the pool that this channel was acquired from.
 *
 * @receiver the [Channel]
 * @return the [ChannelPool], or `null` if this channel was not acquired by a [TaggingChannelPool]
 */
private fun Channel.pool(): ChannelPool? =
    attr(TaggingChannelPool.PoolAttributeKey).get()


/**
 * Requests to close a channel, and releases it into its pool _after_ it is closed.
 *
 * @receiver the [Channel] to close and release
 * @return a [ChannelFuture] that indicates when the operation is complete
 */
internal fun Channel.closeAndReleaseIntoPool(): ChannelFuture =
    closeAndReleaseIntoPool(newPromise())


/**
 * Requests to close a channel, and releases it into its pool _after_ it is closed.
 *
 * @receiver the [Channel] to close and release
 * @param promise a [Promise] that will get signaled when the operation is complete
 * @return a [ChannelFuture] that indicates when the operation is complete
 */
internal fun Channel.closeAndReleaseIntoPool(promise: ChannelPromise): ChannelFuture {
    close().addListener {
        releaseIntoPool(promise)
    }
    return promise
}


/**
 * Releases this channel into the pool it was acquired from.
 *
 * @receiver the [Channel] to be released
 * @param promise a [Promise] that will get signaled when the operation is complete.
 *        Set to `null` to automatically construct one.
 * @return a [Future] that indicates when the operation is complete
 */
internal fun Channel.releaseIntoPool(promise: Promise<Void?>? = null): Future<Void?> {

    val channelPool = pool()
    checkNotNull(channelPool) { "Channel $this was not acquired from a TaggingChannelPool" }

    return if (promise != null) {
        channelPool.release(this, promise)
    } else {
        channelPool.release(this)
    }
}
