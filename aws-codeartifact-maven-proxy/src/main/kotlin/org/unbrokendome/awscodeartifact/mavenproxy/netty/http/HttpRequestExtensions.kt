package org.unbrokendome.awscodeartifact.mavenproxy.netty.http

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.*


/**
 * Makes a copy of an HTTP request.
 *
 * The returned request will have the same [uri][HttpRequest.uri] and [method][HttpRequest.method]
 * as the original request. The [headers][HttpRequest.headers] will be an independent, writable
 * [HttpHeaders] instance pre-populated with the headers from the original request.
 *
 * If the receiver [HttpRequest] is a [FullHttpRequest], then the returned copy will also
 * be a [FullHttpRequest] with the same [content][FullHttpRequest.content].
 *
 * @receiver the request to be cloned
 * @param retainContent if `true`, calls [ByteBuf.retain] the [content][FullHttpRequest.content]
 *        buffer of a [FullHttpRequest] when cloning it. Default is `false`
 * @return the cloned request
 */
internal fun HttpRequest.clone(retainContent: Boolean = false): HttpRequest {

    val headers = DefaultHttpHeaders()
        .add(headers())

    return if (this is FullHttpRequest) {

        val content = content()
        if (retainContent) {
            content.retain()
        }

        DefaultFullHttpRequest(
            protocolVersion(), method(), uri(), content, headers, trailingHeaders()
        )

    } else {
        DefaultHttpRequest(protocolVersion(), method(), uri(), headers)
    }
}


/**
 * Constructs a short string representation of a request, consisting only of the
 * request method and path.
 *
 * @receiver the request
 * @return the short string representation
 */
internal fun HttpRequest.toShortString() =
    "${method()} ${uri()}"
