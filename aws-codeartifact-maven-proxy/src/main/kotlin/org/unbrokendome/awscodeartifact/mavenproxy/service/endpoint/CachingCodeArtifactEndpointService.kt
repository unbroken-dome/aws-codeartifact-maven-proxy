package org.unbrokendome.awscodeartifact.mavenproxy.service.endpoint

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import org.unbrokendome.awscodeartifact.mavenproxy.util.extractResult
import org.unbrokendome.awscodeartifact.mavenproxy.util.toResult
import java.net.URI
import java.time.Duration
import java.util.concurrent.CompletableFuture


/**
 * Implementation of [CodeArtifactEndpointService] that decorates another instance
 * with an in-memory caching layer.
 */
internal class CachingCodeArtifactEndpointService(
    /**
     * The inner [CodeArtifactEndpointService] to be decorated.
     */
    private val delegate: CodeArtifactEndpointService,
    /**
     * Expire cached endpoint URIs after this duration. If `null`, then endpoint URIs will
     * stay in the cache forever.
     */
    private val cacheExpiration: Duration? = null
) : CodeArtifactEndpointService {

    private val cache: AsyncLoadingCache<CodeArtifactEndpointKey, Result<URI>> =
        Caffeine.newBuilder()
            .apply {
                cacheExpiration?.let { expireAfterWrite(it) }
            }
            .buildAsync { key, _ ->
                delegate.getEndpoint(key).toResult()
            }


    override fun getEndpoint(key: CodeArtifactEndpointKey): CompletableFuture<URI> =
        cache.get(key).extractResult()
}


/**
 * Adds an in-memory caching layer around a [CodeArtifactEndpointService].
 *
 * @receiver the [CodeArtifactEndpointService] to be decorated with a caching layer
 * @param cacheExpiration Expire cached endpoint URIs after this duration. If `null`, then endpoint
 *        URIs will stay in the cache forever.
 * @return a [CodeArtifactEndpointService] that adds caching
 */
internal fun CodeArtifactEndpointService.cache(cacheExpiration: Duration? = null): CodeArtifactEndpointService =
    CachingCodeArtifactEndpointService(this, cacheExpiration)
