package org.unbrokendome.awscodeartifact.mavenproxy.error


/**
 * Gets the root cause of a [Throwable].
 *
 * @receiver the Throwable
 * @return the Throwable's root cause
 */
internal fun Throwable.rootCause(): Throwable {
    var cause: Throwable = this

    while (true) {
        val nextCause = cause.cause
        if (nextCause == null || nextCause === cause) {
            return cause
        }
        cause = nextCause
    }
}
