package org.unbrokendome.awscodeartifact.mavenproxy.util

import java.util.concurrent.CompletableFuture


internal fun <T> CompletableFuture<T>.toResult(): CompletableFuture<Result<T>> =
    thenApply { Result.success(it) }
        .exceptionally { error -> Result.failure(error) }


internal fun <T> CompletableFuture<Result<T>>.extractResult(): CompletableFuture<T> =
    thenApply { result ->
        result.getOrThrow()
    }
