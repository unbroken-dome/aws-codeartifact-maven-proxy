package org.unbrokendome.awscodeartifact.mavenproxy.service.token

import java.time.Instant


/**
 * Describes an authorization token for an AWS CodeArtifact repository, and its expiration.
 */
internal data class CodeArtifactTokenResult(
    /**
     * The authorization token to be used when interacting with the repository.
     */
    val authorizationToken: String,
    /**
     * The point in time after which the token is no longer valid.
     */
    val expiration: Instant
) {

    companion object
}
