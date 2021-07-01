package org.unbrokendome.awscodeartifact.mavenproxy.netty.util

import java.net.URI


/**
 * Retrieves the host and port in the form `host:port` from an URI.
 *
 * If the port is not set, returns just the hostname.
 *
 * @receiver the URI
 * @return the host and port
 */
internal fun URI.hostAndPort(): String =
    if (port == -1) host else "$host:$port"
