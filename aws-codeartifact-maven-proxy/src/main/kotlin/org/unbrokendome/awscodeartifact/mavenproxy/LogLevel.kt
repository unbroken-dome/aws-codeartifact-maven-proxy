package org.unbrokendome.awscodeartifact.mavenproxy

import io.netty.handler.logging.LogLevel as NettyLogLevel


enum class LogLevel(internal val nettyLogLevel: NettyLogLevel) {

    TRACE(NettyLogLevel.TRACE),
    DEBUG(NettyLogLevel.DEBUG),
    INFO(NettyLogLevel.INFO),
    WARN(NettyLogLevel.WARN),
    ERROR(NettyLogLevel.ERROR)
}
