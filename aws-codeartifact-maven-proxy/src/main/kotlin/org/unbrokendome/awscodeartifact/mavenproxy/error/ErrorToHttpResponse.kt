package org.unbrokendome.awscodeartifact.mavenproxy.error

import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.*
import io.netty.util.AsciiString
import java.nio.CharBuffer


private val TEXT_PLAIN_CHARSET_UTF8 = AsciiString.cached("text/plain;charset=UTF-8")


/**
 * Constructs an HTTP response for an error.
 *
 * - If the exception implements [HasHttpResponseStatus] then that status code will be used,
 *   otherwise `500 Internal Server Error`.
 * - If the exception implements [HasHttpHeaders] then those headers will be added to the response.
 * - The body of the response will be the exception message as plaintext, or repeat the status code
 *   reason (e.g. "Not Found") if no message is set on the exception.
 *
 * @param alloc a [ByteBufAllocator] to be used for the response content
 * @param version the HTTP version
 * @return a [FullHttpResponse] describing the error
 */
internal fun Throwable.toHttpResponse(
    alloc: ByteBufAllocator,
    version: HttpVersion = HttpVersion.HTTP_1_1
): FullHttpResponse {

    val httpStatus = if (this is HasHttpResponseStatus) this.httpStatus else HttpResponseStatus.INTERNAL_SERVER_ERROR
    val message = this.message ?: httpStatus.reasonPhrase()
    val contentBuf = ByteBufUtil.encodeString(alloc, CharBuffer.wrap(message), Charsets.UTF_8)

    val httpHeaders = DefaultHttpHeaders()
    if (this is HasHttpHeaders) {
        httpHeaders.add(this.httpHeaders)
    }
    httpHeaders.add(HttpHeaderNames.CONTENT_TYPE, TEXT_PLAIN_CHARSET_UTF8)
        .add(HttpHeaderNames.CONTENT_LENGTH, contentBuf.readableBytes())

    return DefaultFullHttpResponse(
        version, httpStatus, contentBuf, httpHeaders, EmptyHttpHeaders.INSTANCE
    )
}
