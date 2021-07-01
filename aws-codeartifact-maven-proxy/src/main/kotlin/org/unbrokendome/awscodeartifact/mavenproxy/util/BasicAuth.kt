package org.unbrokendome.awscodeartifact.mavenproxy.util

import java.util.*


/**
 * Encode a username and password into a value suitable for HTTP Basic authentication.
 *
 * The result will include the "Basic" prefix.
 *
 * @param username the username
 * @param password password
 * @return the HTTP `Authorization` header value for basic authentication
 */
internal fun basicAuthHeaderValue(username: String, password: String): String =
    "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
