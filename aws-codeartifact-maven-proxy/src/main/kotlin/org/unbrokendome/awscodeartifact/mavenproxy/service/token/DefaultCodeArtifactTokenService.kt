package org.unbrokendome.awscodeartifact.mavenproxy.service.token

import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.codeartifact.CodeartifactAsyncClient
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier


/**
 * Default implementation of [CodeArtifactTokenService] that calls the
 * [CodeartifactAsyncClient] from the AWS SDK.
 */
internal class DefaultCodeArtifactTokenService(
    /**
     * Provides a [CodeartifactAsyncClient] when one is needed. Note that this function
     * will be called for every request.
     */
    private val codeArtifactClientSupplier: Supplier<CodeartifactAsyncClient>,
    /**
     * The duration to request for new authorization tokens. Valid values are [Duration.ZERO]
     * and values between 15 minutes and 12 hours.
     *
     * A value of [Duration.ZERO] will set the expiration of the authorization token to the same expiration of
     * the user's role's temporary credentials. If `null`, the duration is not included in the request, and
     * the default from AWS CodeArtifact is used (currently 12 hours).
     */
    duration: Duration? = null
) : CodeArtifactTokenService {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val durationSeconds = duration?.seconds


    override fun getAuthorizationToken(key: CodeArtifactTokenKey): CompletableFuture<CodeArtifactTokenResult> {

        logger.info(
            "Requesting CodeArtifact authorization token for domain={}, domainOwner={}",
            key.domain, key.domainOwner ?: "(default)"
        )

        val codeArtifactClient = codeArtifactClientSupplier.get()

        return codeArtifactClient.getAuthorizationToken { request ->
            request.domain(key.domain)
                .domainOwner(key.domainOwner)
            durationSeconds?.let { request.durationSeconds(it) }
        }.thenApply { response ->
            CodeArtifactTokenResult(
                authorizationToken = response.authorizationToken(),
                expiration = response.expiration()
            ).also {
                logger.info("Successfully retrieved authorization token; token is valid until {}", it.expiration)
            }
        }
    }
}
