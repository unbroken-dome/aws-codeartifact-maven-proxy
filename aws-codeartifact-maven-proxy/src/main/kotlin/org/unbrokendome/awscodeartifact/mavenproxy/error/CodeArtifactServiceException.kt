package org.unbrokendome.awscodeartifact.mavenproxy.error

import io.netty.handler.codec.http.HttpResponseStatus
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException
import software.amazon.awssdk.services.codeartifact.model.ValidationException
import java.net.ConnectException
import java.net.UnknownHostException


/**
 * Wraps an exception thrown by the CodeartifactClient from the AWS SDK.
 *
 * Provides an appropriate response status for reporting the header as an HTTP response.
 */
internal class CodeArtifactServiceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause), HasHttpResponseStatus {

    override val httpStatus: HttpResponseStatus =
        when (cause) {
            is ValidationException -> HttpResponseStatus.BAD_REQUEST
            is ResourceNotFoundException -> HttpResponseStatus.NOT_FOUND
            is SdkClientException -> {
                when (this.rootCause()) {
                    is UnknownHostException, is ConnectException ->
                        HttpResponseStatus.SERVICE_UNAVAILABLE
                    else ->
                        HttpResponseStatus.INTERNAL_SERVER_ERROR
                }
            }
            else ->
                HttpResponseStatus.INTERNAL_SERVER_ERROR
        }
}
