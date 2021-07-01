package org.unbrokendome.awscodeartifact.mavenproxy

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import org.unbrokendome.awscodeartifact.mavenproxy.error.toHttpResponse
import org.unbrokendome.awscodeartifact.mavenproxy.netty.handler.ForwardingChannelInboundHandler
import org.unbrokendome.awscodeartifact.mavenproxy.netty.pool.closeAndReleaseIntoPool
import org.unbrokendome.awscodeartifact.mavenproxy.netty.pool.releaseIntoPool
import org.unbrokendome.awscodeartifact.mavenproxy.netty.util.addListener
import java.util.*


internal class ProxyFrontendHandler(
    private val requestHandler: ProxyRequestHandler
) : ChannelDuplexHandler() {

    companion object {

        const val ForwardHandlerName = "backend-response-forwarder"

        private val logger = LoggerFactory.getLogger(ProxyFrontendHandler::class.java)
    }


    /**
     * Defines various states in a request/response conversation.
     */
    private enum class State(val responseStarted: Boolean = false) {
        /**
         * Waiting for new incoming requests. This is the initial state.
         */
        WAITING_FOR_REQUEST,

        /**
         * The start of a request has been received, but the request is not complete yet.
         * The backend channel for the forwarding has not been set up. Messages that are
         * part of the request need to be buffered temporarily until they can be forwarded.
         */
        START_REQUEST,

        /**
         * The entire request has been received (as indicated by a [LastHttpContent] message),
         * but the backend channel for the forwarding has not been set up.
         */
        START_REQUEST_COMPLETE,

        /**
         * The channel to the backend is active, and the request has not been received completely.
         * Remaining parts of the request can be forwarded directly to the backend, without
         * buffering.
         */
        FORWARDING_REQUEST,

        /**
         * The complete request has been forwarded to the backend, and we are waiting for a
         * response.
         */
        WAITING_FOR_BACKEND_RESPONSE,

        /**
         * The response from the backend has started to arrive. Forwarding the response to the
         * outbound.
         */
        FORWARDING_BACKEND_RESPONSE(responseStarted = true),

        /**
         * An error has occurred, and we are sending a response describing the error.
         */
        SENDING_ERROR_RESPONSE(responseStarted = true)
    }

    @Volatile
    private var _state: State = State.WAITING_FOR_REQUEST

    private var state: State
        get() = _state
        set(value) {
            if (value != _state) {
                val oldState = _state
                _state = value
                logger.debug("State transition: {} -> {}", oldState, value)
            }
        }

    /**
     * The channel to forward requests to the backend. This should be only be written to
     * in the state [State.FORWARDING_REQUEST]. It is `null` when there is no forward connection
     * set up.
     */
    @Volatile
    private var backendChannel: Channel? = null

    /**
     * A queue of buffered request messages. Used in the states [State.START_REQUEST] and
     * [State.START_REQUEST_COMPLETE].
     *
     * This is necessary because of the HTTP codec, which might produce more than one HttpMessage
     * from one raw inbound message. We must buffer these parts of the request and wait for the
     * [channelReadComplete] before we can start processing the request, otherwise more messages
     * might arrive while we're busy setting up the forward connection.
     */
    private val requestMessages: Queue<Any> = LinkedList()


    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.fireChannelActive()

        logger.debug("Trigger initial read from inbound")
        ctx.read()
    }


    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {

        require(msg is HttpObject) {
            "Received unexpected message of type ${msg.javaClass.name}, expected type HttpObject"
        }

        when (state) {
            State.WAITING_FOR_REQUEST -> {
                assert(requestMessages.isEmpty())
                require(msg is HttpRequest) {
                    "Received unexpected out-of-bound HttpContent while waiting for a request"
                }
                requestMessages.add(msg)
                state = if (msg is LastHttpContent) State.START_REQUEST_COMPLETE else State.START_REQUEST
            }

            State.START_REQUEST -> {
                // A backend channel for the request has not been set up, so cache this message
                // until it's ready to be sent
                require(msg is HttpContent) {
                    "Received unexpected HttpRequest message while another request is being processed"
                }
                requestMessages.add(msg)
                if (msg is LastHttpContent) {
                    state = State.START_REQUEST_COMPLETE
                }
            }

            State.FORWARDING_REQUEST -> {
                val backendChannel = checkNotNull(this.backendChannel) { "Backend channel has not been set up" }
                backendChannel.writeAndFlush(msg)

                if (msg is LastHttpContent) {
                    logger.debug("Request received completely")
                    logger.debug("Waiting for response from backend")
                    state = State.WAITING_FOR_BACKEND_RESPONSE
                    backendChannel.read()
                }
            }

            State.START_REQUEST_COMPLETE -> {
                logger.warn("Received an unexpected inbound message after request was complete: {}", msg)
                ReferenceCountUtil.release(msg)
            }

            State.WAITING_FOR_BACKEND_RESPONSE, State.FORWARDING_BACKEND_RESPONSE -> {
                logger.warn("Received an unexpected inbound message while processing backend response: {}", msg)
                ReferenceCountUtil.release(msg)
            }

            State.SENDING_ERROR_RESPONSE -> {
                logger.warn("Received an unexpected inbound message while sending an error response: {}", msg)
                ReferenceCountUtil.release(msg)
            }
        }
    }


    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        when (state) {
            State.START_REQUEST, State.START_REQUEST_COMPLETE -> {
                startHandlingRequest(ctx)
            }
            else -> {
                ctx.fireChannelReadComplete()
            }
        }
    }


    /**
     * Start handling a request. This will use the [ProxyRequestHandler] to open a connection
     * to the backend and forward the beginning of the request, then forward any remaining
     * request parts that were buffered.
     *
     * After the async operation completes, the state will be either [State.FORWARDING_REQUEST]
     * (if the request is not complete yet) or [State.WAITING_FOR_BACKEND_RESPONSE] (if the request
     * has been sent completely), and the [backendChannel] property will be set to the backend channel.
     *
     * @param ctx the [ChannelHandlerContext]
     */
    private fun startHandlingRequest(ctx: ChannelHandlerContext) {

        assert(requestMessages.isNotEmpty())

        val isCompleteRequest = state == State.START_REQUEST_COMPLETE
        val requestStartMessage = requestMessages.remove()

        // Reading of the initial chunk of messages of a request is complete. Now we need to handle the request,
        // open a connection to the backend and forward the request to it.

        requestHandler.handleRequest(
            requestStartMessage as HttpRequest,
            ctx.executor().newPromise()
        ).addListener(
            onSuccess = { backendChannel ->

                this.backendChannel = backendChannel

                logger.debug("Adding forwarding handler to backend channel {}", backendChannel)
                backendChannel.pipeline()
                    .addLast(
                        ForwardHandlerName,
                        ForwardingChannelInboundHandler(ctx.channel())
                    )

                backendChannel.eventLoop().execute {

                    drainQueuedMessages(backendChannel)
                        .addListener(
                            onSuccess = {
                                if (isCompleteRequest) {
                                    logger.debug("Waiting for response from backend")
                                    state = State.WAITING_FOR_BACKEND_RESPONSE
                                    backendChannel.read()
                                } else {
                                    logger.debug("Trigger next read from inbound")
                                    state = State.FORWARDING_REQUEST
                                    ctx.read()
                                }
                            },
                            onError = { error ->
                                logger.warn(
                                    "Error forwarding request to backend, closing backend channel {}",
                                    backendChannel, error
                                )
                                // backend channel might be in a bad state, better close it and not reuse it
                                backendChannel.closeAndReleaseIntoPool()
                                sendErrorResponse(ctx, error)
                            }
                        )
                }
            },
            onError = { error ->
                // There was an error in handling the request, or we could not open a channel to the
                // backend. Send a response describing the error
                sendErrorResponse(ctx, error)
            }
        )
    }


    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        when (state) {
            State.WAITING_FOR_BACKEND_RESPONSE, State.FORWARDING_BACKEND_RESPONSE -> {
                if (msg is LastHttpContent) {
                    ctx.writeAndFlush(msg, promise)
                    afterResponseSent(ctx)
                } else {
                    state = State.FORWARDING_BACKEND_RESPONSE
                    ctx.write(msg, promise)
                    backendChannel!!.read()
                }
            }
            else -> {
                logger.warn("Received an unexpected inbound message while processing frontend request: {}", msg)
                ReferenceCountUtil.release(msg)
            }
        }
    }


    private fun drainQueuedMessages(backendChannel: Channel): ChannelFuture {

        if (requestMessages.isNotEmpty()) {

            logger.debug("Forwarding {} queued messages to backend {}", requestMessages.size, backendChannel)

            val promise = backendChannel.newPromise()

            val listener = object : ChannelFutureListener {
                override fun operationComplete(future: ChannelFuture) {
                    logger.debug("Forwarding message completed")
                    if (future.isSuccess) {
                        val nextMessage = requestMessages.poll()
                        if (nextMessage != null) {
                            logger.debug("Forwarding next message to backend {}: {}", backendChannel, nextMessage)
                            future.channel().writeAndFlush(nextMessage)
                                .addListener(this)
                        } else {
                            logger.debug("No more messages to be forwarded")
                            future.channel().flush()
                            promise.setSuccess()
                        }
                    } else {
                        requestMessages.forEach { remainingMessage ->
                            ReferenceCountUtil.release(remainingMessage)
                        }
                        requestMessages.clear()
                        promise.setFailure(future.cause())
                    }
                }
            }

            val firstMessage = requestMessages.remove()
            logger.debug("Forwarding first message to backend {}: {}", backendChannel, firstMessage)
            backendChannel.writeAndFlush(firstMessage)
                .addListener(listener)

            return promise

        } else {
            logger.debug("No queued messages to be forwarded to backend")
            backendChannel.flush()
            return backendChannel.newSucceededFuture()
        }
    }


    private fun sendErrorResponse(ctx: ChannelHandlerContext, error: Throwable, closeConnection: Boolean = false) {

        check(!state.responseStarted) {
            "Cannot send error response: Sending of another response has already started"
        }

        state = State.SENDING_ERROR_RESPONSE

        val errorResponse = error.toHttpResponse(ctx.alloc())
        if (closeConnection) {
            errorResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        }

        ctx.writeAndFlush(errorResponse)
            .addListener(ChannelFutureListener.CLOSE_ON_FAILURE)

        afterResponseSent(ctx)
    }


    /**
     * Perform cleanup tasks after a response has been sent.
     *
     * This will reset the state to [State.WAITING_FOR_REQUEST] and trigger a new read
     * from the inbound.
     */
    private fun afterResponseSent(ctx: ChannelHandlerContext) {
        // Clean up and wait for the next request
        logger.debug("Response sent completely, waiting for next request")
        cleanupAllState()
        ctx.read()
    }


    override fun channelInactive(ctx: ChannelHandlerContext) {
        cleanupBackendChannel()
        ctx.fireChannelInactive()
    }


    /**
     * Cleans up all state of a previous request/response conversation and resets the
     * state so new inbound requests can be received.
     */
    private fun cleanupAllState() {
        cleanupBackendChannel()
        cleanupQueuedMessages()
        state = State.WAITING_FOR_REQUEST
    }


    /**
     * Cleans up the backend channel and releases it into the channel pool.
     *
     * This will remove the forwarding handler from the channel, so it can be reused for other
     * requests.
     */
    private fun cleanupBackendChannel() {
        val backendChannel = this.backendChannel
        if (backendChannel != null) {
            backendChannel.pipeline().remove(ForwardHandlerName)
            backendChannel.releaseIntoPool()
            this.backendChannel = null
        }
    }


    private fun cleanupQueuedMessages() {
        for (message in requestMessages) {
            ReferenceCountUtil.release(message)
        }
        requestMessages.clear()
    }


    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (!state.responseStarted) {
            sendErrorResponse(ctx, cause, true)
        } else {
            logger.error("Exception caught", cause)
        }

        cleanupAllState()
    }
}
