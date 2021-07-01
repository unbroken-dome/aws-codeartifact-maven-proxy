package org.unbrokendome.awscodeartifact.mavenproxy.netty.pool

import io.netty.bootstrap.Bootstrap
import io.netty.channel.pool.ChannelHealthChecker
import io.netty.channel.pool.ChannelPoolHandler
import io.netty.channel.pool.SimpleChannelPool
import org.unbrokendome.awscodeartifact.mavenproxy.netty.util.AsyncCloseable


/**
 * An extension of [SimpleChannelPool] that also exposes its [closeAsync] method via the
 * [AsyncCloseable] interface.
 */
internal open class AsyncCloseableSimpleChannelPool(
    bootstrap: Bootstrap,
    handler: ChannelPoolHandler,
    healthCheck: ChannelHealthChecker = ChannelHealthChecker.ACTIVE,
    releaseHealthCheck: Boolean = true,
    lastRecentUsed: Boolean = true
) : SimpleChannelPool(bootstrap, handler, healthCheck, releaseHealthCheck, lastRecentUsed),
    AsyncCloseableChannelPool
