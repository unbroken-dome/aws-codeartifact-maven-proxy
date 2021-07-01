package org.unbrokendome.awscodeartifact.mavenproxy.service.token

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import org.unbrokendome.awscodeartifact.mavenproxy.util.extractResult
import org.unbrokendome.awscodeartifact.mavenproxy.util.toResult
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture


/**
 * Implementation of [CodeArtifactTokenService] that decorates another instance
 * with an in-memory caching layer.
 */
internal class CachingCodeArtifactTokenService(
    /**
     * The inner [CodeArtifactTokenService] to be decorated.
     */
    private val delegate: CodeArtifactTokenService,
    /**
     * The clock to be used for retrieving the current timestamp. In production use, the default
     * value of [Clock.systemUTC] should be used. Other [Clock] implementations can be supplied
     * for testing.
     */
    clock: Clock = Clock.systemUTC()
) : CodeArtifactTokenService {

    private class ExpirationStrategy(
        private val clock: Clock
    ) : Expiry<CodeArtifactTokenKey, Result<CodeArtifactTokenResult>> {

        override fun expireAfterCreate(
            key: CodeArtifactTokenKey, value: Result<CodeArtifactTokenResult>, currentTime: Long
        ): Long {
            return value.getOrNull()?.let { tokenResult ->
                val duration = Duration.between(clock.instant(), tokenResult.expiration)
                duration.toNanos()
            } ?: 0L
        }

        override fun expireAfterUpdate(
            key: CodeArtifactTokenKey, value: Result<CodeArtifactTokenResult>,
            currentTime: Long, currentDuration: Long
        ): Long = currentDuration

        override fun expireAfterRead(
            key: CodeArtifactTokenKey, value: Result<CodeArtifactTokenResult>,
            currentTime: Long, currentDuration: Long
        ): Long = currentDuration
    }


    private val cache: AsyncLoadingCache<CodeArtifactTokenKey, Result<CodeArtifactTokenResult>> =
        Caffeine.newBuilder()
            .expireAfter(ExpirationStrategy(clock))
            .buildAsync { key, _ ->
                delegate.getAuthorizationToken(key).toResult()
            }


    override fun getAuthorizationToken(key: CodeArtifactTokenKey): CompletableFuture<CodeArtifactTokenResult> =
        cache.get(key).extractResult()
}


/**
 * Adds an in-memory caching layer around a [CodeArtifactTokenService].
 *
 * @receiver the [CodeArtifactTokenService] to be decorated with a caching layer
 * @param clock The clock to be used for retrieving the current timestamp. In production use, the default
 *        value of [Clock.systemUTC] should be used. Other [Clock] implementations can be supplied
 *        for testing.
 * @return a [CodeArtifactTokenService] that adds caching
 */
internal fun CodeArtifactTokenService.cache(clock: Clock = Clock.systemUTC()): CodeArtifactTokenService =
    CachingCodeArtifactTokenService(this, clock)
