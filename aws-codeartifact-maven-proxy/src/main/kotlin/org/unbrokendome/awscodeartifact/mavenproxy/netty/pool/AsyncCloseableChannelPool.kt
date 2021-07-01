package org.unbrokendome.awscodeartifact.mavenproxy.netty.pool

import io.netty.channel.pool.ChannelPool
import org.unbrokendome.awscodeartifact.mavenproxy.netty.util.AsyncCloseable


internal interface AsyncCloseableChannelPool : ChannelPool, AsyncCloseable
