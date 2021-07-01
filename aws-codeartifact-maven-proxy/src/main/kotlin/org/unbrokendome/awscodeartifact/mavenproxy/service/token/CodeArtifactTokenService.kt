package org.unbrokendome.awscodeartifact.mavenproxy.service.token

import java.util.concurrent.CompletableFuture


/**
 * Provides authorization tokens for AWS CodeArtifact repositories.
 */
internal interface CodeArtifactTokenService {

    /**
     * Retrieves an authorization token for a CodeArtifact repository.
     *
     * The return type of this method is [CodeArtifactTokenResult], which contains the authorization
     * token as well as its expiration. The service will never return an expired token,
     * but the expiration may be interesting for outer caching decorators.
     *
     * @param key the [CodeArtifactTokenKey] specifying the coordinates to the repository
     * @return a [CompletableFuture] that provides the authorization token as a
     *         [CodeArtifactTokenResult] object
     */
    fun getAuthorizationToken(key: CodeArtifactTokenKey): CompletableFuture<CodeArtifactTokenResult>

    /**
     * Retrieves an authorization token for a CodeArtifact repository.
     *
     * Use this as a shorthand if you are only interested in the authorization token (not in the expiration).
     * The service will never return an expired token.
     *
     * @param key the [CodeArtifactTokenKey] specifying the coordinates to the repository
     * @return a [CompletableFuture] that provides the authorization token
     */
    fun getAuthorizationTokenOnly(key: CodeArtifactTokenKey): CompletableFuture<String> =
        getAuthorizationToken(key)
            .thenApply { it.authorizationToken }
}
