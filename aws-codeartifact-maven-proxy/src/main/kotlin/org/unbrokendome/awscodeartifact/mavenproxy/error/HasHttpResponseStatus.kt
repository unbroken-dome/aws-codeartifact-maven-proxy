package org.unbrokendome.awscodeartifact.mavenproxy.error

import io.netty.handler.codec.http.HttpResponseStatus


/**
 * Implemented by exceptions that provide a custom HTTP status code when
 * reported as an HTTP response.
 */
internal interface HasHttpResponseStatus {

    /**
     * The status code to be used for the error response.
     *
     * Should be in the 4xx or 5xx range.
     */
    val httpStatus: HttpResponseStatus
}
