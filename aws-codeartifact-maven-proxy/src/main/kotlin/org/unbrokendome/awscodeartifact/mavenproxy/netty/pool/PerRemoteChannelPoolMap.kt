package org.unbrokendome.awscodeartifact.mavenproxy.netty.pool

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.pool.ChannelPool
import io.netty.channel.pool.ChannelPoolHandler
import io.netty.channel.pool.SimpleChannelPool
import io.netty.handler.codec.http.HttpScheme
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.Promise
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.URI


/**
 * Describes a remote host. Used as a key for [PerRemoteChannelPoolMap].
 */
internal data class RemoteKey(
    val host: String,
    val port: Int,
    val useSsl: Boolean
) {

    companion object {

        private val httpSchemes = listOf(HttpScheme.HTTPS, HttpScheme.HTTP)

        /**
         * Constructs a [RemoteKey] from a URI.
         *
         * This only looks at the scheme, host and port of the URI. Any additional components
         * are disregarded. That means that two URIs with the same scheme, host and port will
         * produce an equal [RemoteKey] instance, even if they are different in other components
         * like the path or query string.
         *
         * The returned key will have [useSsl] set to `true` if the URI scheme is `https`.
         *
         * @param uri the URI
         * @return a [RemoteKey] corresponding to the URI
         */
        fun fromUri(uri: URI): RemoteKey {
            val scheme = httpSchemes.find { it.name().contentEquals(uri.scheme) }
                ?: throw IllegalArgumentException("Unsupported URI scheme \"$uri.scheme\" in URI: $uri")
            return RemoteKey(
                host = uri.host,
                port = if (uri.port != -1) uri.port else scheme.port(),
                useSsl = scheme == HttpScheme.HTTPS
            )
        }
    }
}


/**
 * Maintains a channel pool for each remote host.
 */
internal interface PerRemoteChannelPoolMap :
    AsyncCloseAwareChannelPoolMap<RemoteKey, AsyncCloseableChannelPool>, Closeable {

    /**
     * Gets a channel pool for the remote specified by the given URI.
     *
     * @param uri Only the scheme, host and port components
     *        are considered as a key.
     * @return a [ChannelPool] for the remote
     */
    fun get(uri: URI): ChannelPool =
        get(RemoteKey.fromUri(uri))

    /**
     * Acquires a channel for the remote specified by the given URI.
     *
     * @param uri the URI representing the remote. Only the scheme, host and port components
     *        are considered as a key.
     * @param promise a [Promise] that will be signaled when acquisition is complete
     * @return a [Future] that represents the acquisition operation
     */
    fun acquire(uri: URI, promise: Promise<Channel>): Future<Channel> =
        get(uri).acquire(promise)


    /**
     * Acquires a channel for the remote specified by the given URI.
     *
     * @param uri the URI representing the remote. Only the scheme, host and port components
     *        are considered as a key.
     * @return a [Future] that represents the acquisition operation
     */
    fun acquire(uri: URI): Future<Channel> =
        get(uri).acquire()


    /**
     * Strategy to construct a [Bootstrap] for connections to a remote endpoint.
     */
    fun interface BootstrapFactory {

        /**
         * Creates a bootstrap for the given remote address and SSL flag.
         *
         * Note that any [handler][Bootstrap.handler] configured with the Bootstrap
         * will be ignored; this is due to the implementation of [SimpleChannelPool].
         * Instead, pass a `channelInitializer` function to [create].
         *
         * @param host the host name of the remote
         * @param port the port of the remote
         * @param useSsl whether to use SSL/TLS on the connection
         * @return a [Bootstrap] for connections to the remote
         */
        fun createBootstrap(host: String, port: Int, useSsl: Boolean): Bootstrap
    }


    companion object {

        /**
         * Creates a new [PerRemoteChannelPoolMap].
         *
         * @param bootstrapFactory the [BootstrapFactory] strategy to construct [Bootstrap]
         *        instances for remote endpoints
         * @param channelInitializer a function that will be called on every new [Channel]
         *        after creation. Use this instead of setting a [ChannelInitializer] handler
         *        on the bootstrap, because the [SimpleChannelPool] will replace any configured
         *        handler.
         */
        fun create(
            bootstrapFactory: BootstrapFactory,
            channelInitializer: (Channel) -> Unit = {}
        ): PerRemoteChannelPoolMap {
            return DefaultPerRemoteChannelPoolMap(bootstrapFactory, channelInitializer)
        }
    }
}


internal class DefaultPerRemoteChannelPoolMap(
    private val bootstrapFactory: PerRemoteChannelPoolMap.BootstrapFactory,
    private val channelInitializer: (Channel) -> Unit = {}
) : AbstractAsyncCloseAwareChannelPoolMap<RemoteKey, AsyncCloseableChannelPool>(),
    PerRemoteChannelPoolMap {

    private val logger = LoggerFactory.getLogger(javaClass)


    private val poolHandler = object : ChannelPoolHandler {

        override fun channelAcquired(ch: Channel) {
            logger.debug("Channel acquired from pool: {}", ch)
        }

        override fun channelReleased(ch: Channel) {
            logger.debug("Channel released into pool: {}", ch)
        }

        override fun channelCreated(ch: Channel) {
            logger.debug("Channel created: {}", ch)
            channelInitializer(ch)
        }
    }


    override fun newPool(key: RemoteKey): AsyncCloseableChannelPool {
        val bootstrap = bootstrapFactory.createBootstrap(
            key.host, key.port, key.useSsl
        )
        return TaggingChannelPool(bootstrap, poolHandler)
    }
}
