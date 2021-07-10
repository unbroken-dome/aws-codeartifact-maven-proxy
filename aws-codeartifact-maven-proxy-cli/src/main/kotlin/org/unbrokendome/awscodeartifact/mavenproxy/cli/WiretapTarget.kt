package org.unbrokendome.awscodeartifact.mavenproxy.cli

import org.unbrokendome.awscodeartifact.mavenproxy.wiretap.WiretapLoggerNames
import java.util.*


internal enum class WiretapTarget(val cliName: String, val loggerNames: List<String>) {

    SSL("ssl", listOf(WiretapLoggerNames.BackendSsl)),
    HTTP("http", listOf(WiretapLoggerNames.FrontendHttp, WiretapLoggerNames.BackendHttp));


    companion object {

        fun parse(input: String): WiretapTarget {
            val trimmed = input.trim()
            return values().find { it.cliName == trimmed }
                ?: throw IllegalArgumentException("Invalid wiretap target name: $trimmed")
        }


        fun parse(inputs: List<String>): Set<WiretapTarget> {

            if (inputs.size == 1 && inputs[0] == "all") {
                return EnumSet.allOf(WiretapTarget::class.java)
            }

            try {
                return inputs.mapTo(EnumSet.noneOf(WiretapTarget::class.java), this::parse)

            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Invalid wiretap targets: must be a comma-separated list of [" +
                            "${values().joinToString(", ") { it.cliName }}] or the string \"all\"")
            }
        }
    }
}
