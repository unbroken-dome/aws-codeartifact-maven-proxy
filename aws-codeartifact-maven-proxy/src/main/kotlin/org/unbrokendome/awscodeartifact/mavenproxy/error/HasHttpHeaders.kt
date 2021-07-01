package org.unbrokendome.awscodeartifact.mavenproxy.error

import io.netty.handler.codec.http.HttpHeaders


/**
 * Implemented by exceptions that provide additional HTTP headers when
 * reported as an HTTP response.
 */
internal interface HasHttpHeaders {

    /**
     * Additional headers to be included in the HTTP response.
     */
    val httpHeaders: HttpHeaders
}
