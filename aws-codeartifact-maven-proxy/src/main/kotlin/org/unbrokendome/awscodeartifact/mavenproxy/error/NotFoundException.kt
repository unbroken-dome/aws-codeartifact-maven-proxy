package org.unbrokendome.awscodeartifact.mavenproxy.error

import io.netty.handler.codec.http.HttpResponseStatus


/**
 * Thrown when a requested repository does not exist.
 */
internal class NotFoundException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause), HasHttpResponseStatus {

    override val httpStatus: HttpResponseStatus
        get() = HttpResponseStatus.NOT_FOUND
}
