package org.unbrokendome.awscodeartifact.mavenproxy

import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpRequest
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.Promise
import org.slf4j.LoggerFactory
import org.unbrokendome.awscodeartifact.mavenproxy.error.CodeArtifactServiceException
import org.unbrokendome.awscodeartifact.mavenproxy.error.NotFoundException
import org.unbrokendome.awscodeartifact.mavenproxy.netty.handler.NotifyActiveHandler
import org.unbrokendome.awscodeartifact.mavenproxy.netty.http.clone
import org.unbrokendome.awscodeartifact.mavenproxy.netty.http.toShortString
import org.unbrokendome.awscodeartifact.mavenproxy.netty.pool.PerRemoteChannelPoolMap
import org.unbrokendome.awscodeartifact.mavenproxy.netty.pool.closeAndReleaseIntoPool
import org.unbrokendome.awscodeartifact.mavenproxy.netty.util.addListener
import org.unbrokendome.awscodeartifact.mavenproxy.util.basicAuthHeaderValue
import org.unbrokendome.awscodeartifact.mavenproxy.netty.util.hostAndPort
import org.unbrokendome.awscodeartifact.mavenproxy.service.token.CodeArtifactTokenKey
import org.unbrokendome.awscodeartifact.mavenproxy.service.token.CodeArtifactTokenService
import org.unbrokendome.awscodeartifact.mavenproxy.service.endpoint.CodeArtifactEndpointKey
import org.unbrokendome.awscodeartifact.mavenproxy.service.endpoint.CodeArtifactEndpointService
import java.net.URI
import java.util.concurrent.CompletableFuture


/**
 * Handles HTTP request in the proxy frontend.
 *
 * - Parse the request path into CodeArtifact repository coordinates
 * - Retrieve endpoint and credentials for the CodeArtifact repository
 * - Open a backend connection to the endpoint
 * - Forward the initial request part to the backend
 */
internal class ProxyRequestHandler(
    private val codeArtifactEndpointService: CodeArtifactEndpointService,
    private val codeArtifactTokenService: CodeArtifactTokenService,
    private val backendChannelPoolMap: PerRemoteChannelPoolMap
) {

    companion object {

        private val logger = LoggerFactory.getLogger(ProxyRequestHandler::class.java)
    }


    /**
     * Structured representation of a request for a repository.
     */
    private data class RepositoryRequestPath(
        /** The domain of the repository. */
        val domain: String,
        /**
         * The domain owner of the repository, or `null` to use the implicit domain owner from the
         * current AWS account ID.
         */
        val domainOwner: String?,
        /**
         * The name of the repository.
         */
        val repository: String,
        /**
         * The sub-path inside the repository (usually containing Maven coordinates of the artifact
         * to retrieve or publish).
         */
        val subPath: String
    ) {
        /**
         * Construct a key for the endpoint lookup from this request.
         *
         * @return the [CodeArtifactEndpointKey]
         */
        fun toEndpointKey() = CodeArtifactEndpointKey(domain, domainOwner, repository)

        /**
         * Construct a key for the credential lookup from this request.
         *
         * @return the [CodeArtifactTokenKey]
         */
        fun toCredentialsKey() = CodeArtifactTokenKey(domain, domainOwner)


        companion object {

            private const val DefaultDomainOwnerValue = "default"

            private val PathRegex = Regex(
                "^/(?<domain>[a-z][a-z0-9\\-]{0,48}[a-z0-9])" +
                        "/(?<domainOwner>${Regex.escape(DefaultDomainOwnerValue)}|[0-9]{12})" +
                        "/(?<repository>[A-Za-z0-9][A-Za-z0-9._\\-]{1,99})" +
                        "(?:/(?<subPath>.*))?$"
            )

            /**
             * Parse the path from an HTTP request and extract the repository coordinates.
             *
             * Returns `null` if the path does not describe a repository path as per the
             * proxy server's convention.
             *
             * @param requestPath the request path to parse
             */
            fun parse(requestPath: String): RepositoryRequestPath? =
                PathRegex.matchEntire(requestPath)
                    ?.let { matchResult ->
                        val (domain, domainOwnerValue, repository, subPath) = matchResult.destructured
                        val domainOwner = domainOwnerValue.takeUnless { it == DefaultDomainOwnerValue }
                        RepositoryRequestPath(domain, domainOwner, repository, subPath)
                    }
        }
    }


    /**
     * Handle an incoming HTTP request to the proxy.
     *
     * When the returned future completes successfully, it will provide a [Channel] that is open
     * and ready to accept additional parts of the request
     *
     * @param request the (beginning of the) request to handle
     * @param promise a [Promise] to be used as the return [Future]
     * @return a [Future] representing the result of the async operation
     */
    fun handleRequest(
        request: HttpRequest, promise: Promise<Channel>
    ): Future<Channel> {
        if (logger.isDebugEnabled) {
            logger.debug("Handling request: {}", request.toShortString())
        }

        val repositoryRequestPath = RepositoryRequestPath.parse(request.uri())
            ?: throw NotFoundException(
                "Request path {} does not match repository pattern /<domain-owner>/<domain>/<repository>/*"
            )

        codeArtifactEndpointService.getEndpoint(repositoryRequestPath.toEndpointKey())
            .handleCodeArtifactErrors(promise, "get repository endpoint")
            .thenComposeAsync { endpoint ->

                codeArtifactTokenService.getAuthorizationTokenOnly(repositoryRequestPath.toCredentialsKey())
                    .handleCodeArtifactErrors(promise, "get repository credentials")
                    .thenAcceptAsync { authorizationToken ->

                        backendChannelPoolMap.acquire(endpoint)
                            .addListener(
                                onSuccess = { backendChannel ->

                                    val forwardRequest = buildForwardRequest(
                                        request, repositoryRequestPath, endpoint, authorizationToken
                                    )
                                    sendForwardRequestWhenActive(backendChannel, forwardRequest)
                                        .addListener(
                                            onSuccess = promise::setSuccess,
                                            onError = { _, error ->
                                                promise.setFailure(error)
                                            }
                                        )
                                },
                                onError = { error ->
                                    logger.warn("Failed to acquire a channel to forward to backend {}", endpoint)
                                    promise.setFailure(error)
                                }
                            )
                    }
            }

        return promise
    }

    /**
     * Waits for the backend channel to become active, then forwards the request message.
     *
     * @param channel the backend channel
     * @param forwardRequest the request to be forwarded
     * @return a [ChannelFuture] representing the result of the async operation
     */
    private fun sendForwardRequestWhenActive(
        channel: Channel, forwardRequest: HttpRequest
    ): ChannelFuture =
        sendForwardRequestWhenActive(channel, forwardRequest, channel.newPromise())

    /**
     * Waits for the backend channel to become active, then forwards the request message.
     *
     * @param channel the backend channel
     * @param forwardRequest the (beginning of the) request to be forwarded
     * @return a [ChannelFuture] representing the result of the async operation
     */
    private fun sendForwardRequestWhenActive(
        channel: Channel, forwardRequest: HttpRequest, promise: ChannelPromise
    ): ChannelFuture {

        val activeFuture = channel.attr(NotifyActiveHandler.ActiveFutureAttributeKey).get()

        return if (activeFuture.isDone) {
            sendForwardRequest(channel, forwardRequest, promise)

        } else {
            logger.debug("Waiting for channel to become active: {}", channel)
            activeFuture.addListener(
                onSuccess = {
                    sendForwardRequest(channel, forwardRequest, promise)
                }
            )
            promise
        }
    }

    /**
     * Send the forward request to the backend channel.
     *
     * @param channel the backend channel
     * @param forwardRequest the (beginning of the) request to be forwarded
     * @param promise a [ChannelPromise] to use for the returned [Future]
     * @return a [ChannelFuture] representing the result of the async operation
     */
    private fun sendForwardRequest(
        channel: Channel, forwardRequest: HttpRequest, promise: ChannelPromise
    ): ChannelFuture {
        if (logger.isDebugEnabled) {
            logger.debug("Starting to forward request to backend {}: {}", channel, forwardRequest.toShortString())
        }
        channel.writeAndFlush(forwardRequest)
            .addListener(
                onSuccess = {
                    promise.setSuccess()
                },
                onError = { error ->
                    logger.warn(
                        "Failed to forward request to backend {}: {}", channel, forwardRequest.toShortString(), error
                    )
                    channel.closeAndReleaseIntoPool()
                    promise.setFailure(error)
                }
            )
        return promise
    }

    /**
     * Handle exceptions thrown by the CodeArtifact service.
     *
     * This will wrap exceptions in a [CodeArtifactServiceException]
     * which provides an appropriate response status code for an error response.
     *
     * @receiver a [CompletableFuture] representing an async call to CodeArtifact services
     * @param promise a [Promise] to signal as failed in case of an error
     * @param operationDescription a human readable description of the operation that was
     *        attempted, will be used in exception messages
     * @return a [CompletableFuture] that handles the errors
     */
    private fun <T> CompletableFuture<T>.handleCodeArtifactErrors(
        promise: Promise<*>, operationDescription: String
    ): CompletableFuture<T> =
        whenComplete { _, error ->
            if (error != null) {
                val wrappedError = CodeArtifactServiceException(
                    "Failed to $operationDescription from AWS CodeArtifact: ${error.message}", error
                )
                promise.setFailure(wrappedError)
            }
        }

    /**
     * Construct a request to be forwarded to the backend, based on the original inbound request
     * to the proxy.
     *
     * @param originalRequest the (beginning of the) original HTTP request
     * @param repositoryRequestPath the request path, parsed as a [RepositoryRequestPath]
     * @param endpoint the endpoint URI for the CodeArtifact repository
     * @param authorizationToken the authorization token for the CodeArtifact repository
     * @return a new [HttpRequest] that should be forwarded to the backend
     */
    private fun buildForwardRequest(
        originalRequest: HttpRequest,
        repositoryRequestPath: RepositoryRequestPath,
        endpoint: URI, authorizationToken: String
    ): HttpRequest {

        return originalRequest.clone().apply {

            uri = buildForwardRequestPath(endpoint, repositoryRequestPath.subPath)

            headers()
                .set(HttpHeaderNames.HOST, endpoint.hostAndPort())
                .set(HttpHeaderNames.AUTHORIZATION, basicAuthHeaderValue("aws", authorizationToken))
        }
    }


    /**
     * Builds a path for a forward request.
     *
     * @param endpoint the endpoint URI for the CodeArtifact repository
     * @param subPath the sub-path inside the repository (usually containing Maven coordinates of
     *        the artifact o retrieve or publish).
     * @return the request path for the forward request
     */
    private fun buildForwardRequestPath(endpoint: URI, subPath: String) = buildString {
        append(endpoint.rawPath)
        if (!endsWith('/')) append('/')
        append(subPath)
        endpoint.rawQuery?.let {
            append('?')
            append(it)
        }
    }
}
