package org.unbrokendome.awscodeartifact.mavenproxy.netty.util

import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.FutureListener
import java.util.concurrent.CompletableFuture


/**
 * Adds the specified success/error listeners to this future.
 *
 * @receiver a [Future] of some result
 * @param onSuccess a function that gets notified if the future completed with success
 * @param onError a function that gets notified if the future completed with error
 * @return this [Future]
 */
internal fun <V> Future<V>.addListener(
    onSuccess: (V) -> Unit,
    onError: (Throwable) -> Unit
): Future<V> = apply {
    addListener(FutureListener<V> {
        if (it.isSuccess) {
            onSuccess(it.now)
        } else {
            onError(it.cause())
        }
    })
}


/**
 * Adds the specified success/error listeners to this future.
 *
 * @receiver a [ChannelFuture]
 * @param onSuccess a function that gets notified if the future completed with success
 * @param onError a function that gets notified if the future completed with error
 * @return this [ChannelFuture]
 */
internal fun ChannelFuture.addListener(
    onSuccess: (Channel) -> Unit,
    onError: (Channel, Throwable) -> Unit = { _, _ -> }
): ChannelFuture = apply {
    addListener(ChannelFutureListener {
        if (it.isSuccess) {
            onSuccess(it.channel())
        } else {
            onError(it.channel(), it.cause())
        }
    })
}


internal fun <V> Future<V>.toCompletableFuture(): CompletableFuture<V> =
    CompletableFuture<V>().also { completableFuture ->
        addListener(
            onSuccess = { result -> completableFuture.complete(result) },
            onError = { error -> completableFuture.completeExceptionally(error) }
        )
    }
