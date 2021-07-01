package org.unbrokendome.awscodeartifact.mavenproxy

import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import org.slf4j.LoggerFactory
import org.unbrokendome.awscodeartifact.mavenproxy.netty.pool.PerRemoteChannelPoolMap
import org.unbrokendome.awscodeartifact.mavenproxy.netty.util.addListener
import org.unbrokendome.awscodeartifact.mavenproxy.netty.util.closeAsyncIfPossible
import org.unbrokendome.awscodeartifact.mavenproxy.netty.util.toCompletableFuture
import org.unbrokendome.awscodeartifact.mavenproxy.service.endpoint.DefaultCodeArtifactEndpointService
import org.unbrokendome.awscodeartifact.mavenproxy.service.endpoint.cache
import org.unbrokendome.awscodeartifact.mavenproxy.service.token.DefaultCodeArtifactTokenService
import org.unbrokendome.awscodeartifact.mavenproxy.service.token.cache
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codeartifact.CodeartifactAsyncClient
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier


/**
 * A proxy server for AWS CodeArtifact Maven repositories.
 *
 * It handles HTTP requests with a path of the form `/<domain>/<domainOwner>/<repository>` by
 * looking up the correct repository endpoint, retrieving the temporary credentials and
 * forwarding the authenticated request to the repository. Endpoint URIs and credentials are
 * cached in memory and refreshed when they expire.
 *
 * The server needs to authenticate with AWS. It does so via the standard methods (e.g.
 * `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables) by default, but it
 * is possible to plug in a custom [AwsCredentialsProvider] instead.
 */
class CodeArtifactMavenProxyServer
private constructor(
    private val channel: Channel,
    private val shutdownOp: () -> CompletableFuture<Void?>
) : AutoCloseable {

    private val stopLatch = CountDownLatch(1)
        .apply {
            channel.closeFuture().addListener { countDown() }
        }


    /**
     * The actual port on which the server is listening for requests.
     */
    val actualPort: Int
        get() = (channel.localAddress() as InetSocketAddress).port

    /**
     * Indicates whether the server is currently running.
     */
    val isRunning: Boolean
        get() = channel.isOpen


    init {
        channel.closeFuture().addListener(ChannelFutureListener {
            logger.info("Server shutdown complete")
            stopLatch.countDown()
        })
    }

    /**
     * Stops the server.
     *
     * @return a [CompletableFuture] that is completed when the shutdown is complete
     */
    fun stop(): CompletableFuture<Void?> {

        if (!channel.isOpen) {
            return CompletableFuture.completedFuture(null)
        }

        logger.info("AWS CodeArtifact Maven proxy server shutting down")

        return channel.close().toCompletableFuture()
            .thenComposeAsync { shutdownOp() }
            .thenRun { stopLatch.countDown() }
    }


    /**
     * Triggers the shutdown of the server, and blocks until it is stopped.
     *
     * If the server is already stopped, this method returns immediately.
     */
    fun stopSync() {
        stop().join()
    }


    /**
     * Blocks and waits for the server to be shut down.
     */
    fun join() {
        stopLatch.await()
    }


    fun join(timeout: Long, unit: TimeUnit): Boolean =
        stopLatch.await(timeout, unit)


    override fun close() {
        stopSync()
    }


    data class Options(

        /**
         * The local address on which the server should listen fo requests.
         *
         * Defaults to the local loopback address (127.0.0.1).
         */
        val bindAddress: InetAddress = InetAddress.getLoopbackAddress(),

        /**
         * The local port on which the server should listen to requests.
         *
         * If this is zero, then the server will pick a random free port to listen on. The
         * actual port will be available through the [actualPort] property when the server
         * is started.
         */
        val port: Int = 0,

        /**
         * Override the endpoint for the AWS CodeArtifact service API.
         *
         * If `null` (the default), the default AWS API endpoint is used.
         */
        val codeArtifactEndpointOverride: URI? = null,

        /**
         * The [AwsCredentialsProvider] for authenticating with AWS APIs.
         *
         * If `null` (the default), the default authentication provider will be used.
         */
        val awsCredentialsProvider: AwsCredentialsProvider? = null,

        /**
         * The AWS region to use for CodeArtifact.
         *
         * If `null` (the default), the default region resolution will be used.
         */
        val awsRegion: Region? = null,

        /**
         * If `true`, AWS clients will be initialized eagerly when the server starts. This
         * will cause the startup to take a little longer but reduces the response time for
         * the initial request. If `false` (the default), AWS clients will be initialized lazily on the
         * first request.
         */
        val eagerInitAwsClient: Boolean = false,

        /**
         * The duration to request for new authorization tokens with AWS CodeArtifact.
         * Valid values are [Duration.ZERO] and values between 15 minutes and 12 hours.
         *
         * A value of [Duration.ZERO] will set the expiration of the authorization token to the same expiration of
         * the user's role's temporary credentials. If `null`, the duration is not included in the request, and
         * the default from AWS CodeArtifact is used (currently 12 hours).
         */
        val tokenTtl: Duration? = null,

        /**
         * The duration for which to cache repository endpoints returned by the AWS CodeArtifact service in memory.
         * If `null`, endpoints are cached indefinitely.
         */
        val endpointCacheTtl: Duration? = null,

        /**
         * The number of worker threads to use for serving requests.
         *
         * If zero (the default), use a default number of threads based on the number of available processors.
         */
        val workerThreads: Int = 0
    )


    companion object {

        private val logger = LoggerFactory.getLogger(CodeArtifactMavenProxyServer::class.java)


        private fun buildCodeArtifactClientSupplier(options: Options): Supplier<CodeartifactAsyncClient> {
            return if (options.eagerInitAwsClient) {
                val client = buildCodeArtifactClient(options)
                Supplier { client }
            } else {
                val lazyClient = lazy { buildCodeArtifactClient(options) }
                Supplier { lazyClient.value }
            }
        }


        private fun buildCodeArtifactClient(options: Options): CodeartifactAsyncClient {
            return CodeartifactAsyncClient.builder().run {
                options.codeArtifactEndpointOverride?.let { endpointOverride(it) }
                options.awsCredentialsProvider?.let { credentialsProvider(it) }
                options.awsRegion?.let { region(it) }
                build()
            }
        }


        /**
         * Starts a new server with the given options.
         *
         * @param options the options for the server
         * @return a [CompletableFuture] that completes with a [CodeArtifactMavenProxyServer] when the server is up
         *         and running
         */
        fun start(
            options: Options = Options()
        ): CompletableFuture<CodeArtifactMavenProxyServer> {

            val codeArtifactClientSupplier = buildCodeArtifactClientSupplier(options)

            val codeArtifactEndpointService = DefaultCodeArtifactEndpointService(codeArtifactClientSupplier)
                .cache()
            val codeArtifactCredentialsService = DefaultCodeArtifactTokenService(
                codeArtifactClientSupplier, options.tokenTtl
            ).cache()

            val bossGroup = NioEventLoopGroup(1)
            val workerGroup = NioEventLoopGroup()

            val backendBootstrapFactory =
                PerRemoteChannelPoolMap.BootstrapFactory { remoteHost, remotePort, useSsl ->
                    Bootstrap()
                        .group(workerGroup)
                        .channel(NioSocketChannel::class.java)
                        .remoteAddress(remoteHost, remotePort)
                        .attr(ProxyBackendChannelInitializer.UseSslAttributeKey, useSsl)
                        .option(ChannelOption.AUTO_READ, false)
                }

            val backendChannelInitializer = ProxyBackendChannelInitializer()
            val backendChannelPoolMap = PerRemoteChannelPoolMap.create(backendBootstrapFactory) { channel ->
                backendChannelInitializer.initChannel(channel)
            }

            val proxyRequestHandler = ProxyRequestHandler(
                codeArtifactEndpointService, codeArtifactCredentialsService, backendChannelPoolMap
            )

            val bootstrap = ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .handler(object : ChannelOutboundHandlerAdapter() {
                    override fun close(ctx: ChannelHandlerContext, promise: ChannelPromise) {

                        ctx.close(promise)
                    }
                })
                .childHandler(
                    ProxyFrontendChannelInitializer(proxyRequestHandler)
                )
                .childOption(ChannelOption.AUTO_READ, false)

            val channelFuture = bootstrap.bind(options.bindAddress, options.port)

            fun shutdown(): CompletableFuture<Void?> {
                val cleanupFuture = CompletableFuture<Void?>()
                val shutdownCount = AtomicInteger(3)
                val shutdownListener = GenericFutureListener<Future<in Any?>> {
                    if (shutdownCount.decrementAndGet() == 0) {
                        cleanupFuture.complete(null)
                    }
                }
                bossGroup.shutdownGracefully().addListener(shutdownListener)
                workerGroup.shutdownGracefully().addListener(shutdownListener)
                backendChannelPoolMap.closeAsyncIfPossible().addListener(shutdownListener)
                return cleanupFuture
            }

            val serverFuture = CompletableFuture<CodeArtifactMavenProxyServer>()
            channelFuture.addListener(
                onSuccess = { channel ->
                    val server = CodeArtifactMavenProxyServer(channel, ::shutdown)
                    logger.info("AWS CodeArtifact Maven proxy server listening on {}", channel.localAddress())
                    serverFuture.complete(server)
                },
                onError = { _, error ->
                    shutdown().handle { _, _ ->
                        serverFuture.completeExceptionally(error)
                    }
                }
            )

            return serverFuture
        }


        /**
         * Starts a server and blocks until it is started.
         *
         * @param options the options for the server
         * @return a [CodeArtifactMavenProxyServer] that can be used to query the port and stop the server
         */
        fun startSync(
            options: Options
        ): CodeArtifactMavenProxyServer =
            start(options).join()
    }
}
