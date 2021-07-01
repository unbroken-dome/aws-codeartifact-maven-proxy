package org.unbrokendome.awscodeartifact.mavenproxy.service.token


/**
 * Describes the coordinates to an AWS CodeArtifact repository that are necessary
 * to retrieve authorization tokens.
 *
 * This can be used as a cache key for tokens. Note, however that the case
 * where the domain owner is implied from the current AWS account ID (i.e. [domainOwner] is `null`)
 * and the case where the domain owner is explicitly given, will each produce a different
 * key even if they indicate the same repository. This is intentional, so we can avoid calls
 * to `sts:GetCallerIdentity` when using a cache.
 */
internal data class CodeArtifactTokenKey(
    /**
     * The domain of the repository.
     */
    val domain: String,
    /**
     * The domain owner (AWS account ID) of the repository, or null to use the implicit
     * domain owner from the current AWS credentials.
     */
    val domainOwner: String?
) {
    companion object
}
