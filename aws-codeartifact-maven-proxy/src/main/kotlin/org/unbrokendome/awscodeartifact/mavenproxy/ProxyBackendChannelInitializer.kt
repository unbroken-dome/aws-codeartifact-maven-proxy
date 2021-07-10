package org.unbrokendome.awscodeartifact.mavenproxy

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.pool.SimpleChannelPool
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.logging.ByteBufFormat
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslHandler
import io.netty.util.AttributeKey
import org.slf4j.LoggerFactory
import org.unbrokendome.awscodeartifact.mavenproxy.netty.handler.HttpConnectionCloseClientHandler
import org.unbrokendome.awscodeartifact.mavenproxy.netty.handler.NotifyActiveHandler
import org.unbrokendome.awscodeartifact.mavenproxy.netty.handler.TriggerSslHandshakeHandler
import org.unbrokendome.awscodeartifact.mavenproxy.wiretap.WiretapLoggerNames
import javax.net.ssl.SSLContext


/**
 * Initializes channels for the repository backends.
 *
 * Note that this is not a subclass of [ChannelInitializer] because it is used with a
 * channel pool (whose implementation [SimpleChannelPool] does not let us install our
 * own initializer handlers on a [Bootstrap].
 */
internal class ProxyBackendChannelInitializer(
    wiretapLogLevel: LogLevel = LogLevel.TRACE,
    private val sslContextProvider: () -> SSLContext = { SSLContext.getDefault() }
) {

    companion object {

        private val logger = LoggerFactory.getLogger(ProxyBackendChannelInitializer::class.java)

        val UseSslAttributeKey: AttributeKey<Boolean> =
            AttributeKey.newInstance("useSsl")
    }


    private val sslLoggingHandler = LoggingHandler(WiretapLoggerNames.BackendSsl, wiretapLogLevel, ByteBufFormat.SIMPLE)
    private val httpLoggingHandler = LoggingHandler(WiretapLoggerNames.BackendHttp, wiretapLogLevel)
    private val triggerSslHandshakeHandler = TriggerSslHandshakeHandler()
    private val notifyActiveHandler = NotifyActiveHandler()


    fun initChannel(ch: Channel) {

        logger.debug("Initializing backend channel {}", ch)

        val pipeline = ch.pipeline()

        val useSsl = ch.attr(UseSslAttributeKey).get() ?: false
        if (ch is SocketChannel && useSsl) {
            val sslContext = sslContextProvider()
            val sslEngine = sslContext.createSSLEngine()
            sslEngine.useClientMode = true
            pipeline.addLast(
                sslLoggingHandler,
                SslHandler(sslEngine),
                triggerSslHandshakeHandler
            )
        }

        pipeline.addLast(
            //rawLoggingHandler,
            notifyActiveHandler,
            HttpClientCodec(),
            HttpConnectionCloseClientHandler(true),
            httpLoggingHandler
        )
    }
}
