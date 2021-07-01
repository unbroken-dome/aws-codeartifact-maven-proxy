package org.unbrokendome.awscodeartifact.mavenproxy.service.endpoint

import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.codeartifact.CodeartifactAsyncClient
import software.amazon.awssdk.services.codeartifact.model.PackageFormat
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier


/**
 * Default implementation of [CodeArtifactEndpointService] that calls the
 * [CodeartifactAsyncClient] from the AWS SDK.
 */
internal class DefaultCodeArtifactEndpointService(
    private val codeArtifactClientSupplier: Supplier<CodeartifactAsyncClient>
) : CodeArtifactEndpointService {

    private val logger = LoggerFactory.getLogger(javaClass)


    override fun getEndpoint(key: CodeArtifactEndpointKey): CompletableFuture<URI> {

        logger.info(
            "Requesting CodeArtifact endpoint for domain={}, domainOwner={}, repository={}",
            key.domain, key.domainOwner ?: "(default)", key.repository
        )

        val codeArtifactClient = codeArtifactClientSupplier.get()

        return codeArtifactClient.getRepositoryEndpoint { request ->
            request.domain(key.domain)
                .domainOwner(key.domainOwner)
                .repository(key.repository)
                .format(PackageFormat.MAVEN)
        }.thenApply { response ->
            URI(response.repositoryEndpoint())
                .also {
                    logger.info("CodeArtifact endpoint: {}", it)
                }
        }
    }
}
