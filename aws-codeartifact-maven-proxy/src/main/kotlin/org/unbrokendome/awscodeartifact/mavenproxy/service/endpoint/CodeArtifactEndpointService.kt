package org.unbrokendome.awscodeartifact.mavenproxy.service.endpoint

import java.net.URI
import java.util.concurrent.CompletableFuture


/**
 * Provides HTTP(S) endpoints for AWS CodeArtifact repositories that can be used with standard
 * Maven repository clients like Maven or Gradle.
 */
internal interface CodeArtifactEndpointService {

    /**
     * Retrieves an endpoint for a CodeArtifact repository.
     *
     * @param key the [CodeArtifactEndpointKey] specifying the coordinates to the repository
     * @return a [CompletableFuture] that provides the endpoint as a [URI] on completion
     */
    fun getEndpoint(key: CodeArtifactEndpointKey): CompletableFuture<URI>
}
