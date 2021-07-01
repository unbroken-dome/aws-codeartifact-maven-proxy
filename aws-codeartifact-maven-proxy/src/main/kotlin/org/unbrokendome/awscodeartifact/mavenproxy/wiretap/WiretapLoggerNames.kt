package org.unbrokendome.awscodeartifact.mavenproxy.wiretap

object WiretapLoggerNames {

    const val Prefix = "org.unbrokendome.awscodeartifact.mavenproxy.wiretap"

    const val FrontendRaw = "$Prefix.frontend-raw"
    const val FrontendHttp = "$Prefix.frontend-http"

    const val BackendSsl = "$Prefix.backend-ssl"
    const val BackendRaw = "$Prefix.backend-raw"
    const val BackendHttp = "$Prefix.backend-http"
}
