package org.unbrokendome.awscodeartifact.mavenproxy.cli

import joptsimple.OptionParser
import joptsimple.util.InetAddressConverter
import java.io.OutputStream
import java.net.InetAddress
import java.time.Duration
import java.util.*


internal data class CliOptions(
    val showHelp: Boolean,
    val bindAddress: InetAddress,
    val port: Int,
    val logging: Logging,
    val tokenTtl: Duration?,
    val endpointCacheTtl: Duration?,
    val eagerInit: Boolean,
) {
    data class Logging(
        val debug: Boolean,
        val awsDebug: Boolean,
        val wiretapTargets: Set<WiretapTarget>
    ) {

        val isSimpleLogging: Boolean
            get() = !debug && !awsDebug && wiretapTargets.isEmpty()
    }


    fun printHelpOn(output: OutputStream) {
        parser.printHelpOn(output)
    }


    companion object {

        private val parser = OptionParser()

        private val bindOption = parser
            .acceptsAll(listOf("bind", "b"), "Host name or IP address to listen on")
            .withRequiredArg().withValuesConvertedBy(InetAddressConverter())
            .defaultsTo(InetAddress.getLoopbackAddress())

        private val portOption = parser
            .acceptsAll(listOf("port", "p"), "HTTP port to listen on")
            .withRequiredArg().ofType(Int::class.java)
            .defaultsTo(0)

        private val debugOption = parser
            .accepts("debug", "Enable DEBUG-level logging")

        private val awsDebugOption = parser
            .accepts("aws-debug", "Enable DEBUG-level logging for AWS SDK clients")

        private val tokenTtlOption = parser
            .acceptsAll(listOf("token-ttl", "t"), "Time-to-live for CodeArtifact authorization tokens")
            .withRequiredArg().withValuesConvertedBy(DurationValueConverter)

        private val endpointTtlOption = parser
            .accepts("endpoint-ttl", "Cache TTL for CodeArtifact repository endpoints")
            .withRequiredArg().withValuesConvertedBy(DurationValueConverter)

        private val eagerInitOption = parser
            .accepts("eager-init", "Initialize eagerly on startup (not lazily on first request)")

        private val wiretapOption = parser
            .accepts(
                "wiretap", "Traffic to wire-tap (monitor) in logs. Must be \"all\" (default if" +
                        " no argument is given) or a comma-separated list of targets \"raw\", \"http\", \"ssl\""
            )
            .withOptionalArg()
            .withValuesSeparatedBy(',')

        private val helpOption = parser
            .acceptsAll(listOf("help", "h"), "Show this help message and exit")
            .forHelp()


        fun parse(args: Array<String>): CliOptions {

            val parsedOptions = parser.parse(*args)

            return CliOptions(
                showHelp = parsedOptions.has(helpOption),
                bindAddress = parsedOptions.valueOf(bindOption),
                port = parsedOptions.valueOf(portOption),
                logging = Logging(
                    debug = parsedOptions.has(debugOption),
                    awsDebug = parsedOptions.has(awsDebugOption),
                    wiretapTargets = if (parsedOptions.has(wiretapOption)) {
                        if (parsedOptions.hasArgument(wiretapOption)) {
                            WiretapTarget.parse(parsedOptions.valuesOf(wiretapOption))
                        } else {
                            EnumSet.allOf(WiretapTarget::class.java)
                        }
                    } else emptySet()
                ),
                tokenTtl = parsedOptions.valueOf(tokenTtlOption),
                endpointCacheTtl = parsedOptions.valueOf(endpointTtlOption),
                eagerInit = parsedOptions.has(eagerInitOption)
            )
        }
    }
}

