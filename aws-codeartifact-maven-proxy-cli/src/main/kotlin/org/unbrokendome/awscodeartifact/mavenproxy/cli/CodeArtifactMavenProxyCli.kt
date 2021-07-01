package org.unbrokendome.awscodeartifact.mavenproxy.cli

import org.unbrokendome.awscodeartifact.mavenproxy.CodeArtifactMavenProxyServer
import kotlin.concurrent.thread
import kotlin.system.exitProcess


object CodeArtifactMavenProxyCli {


    @JvmStatic
    fun main(args: Array<String>) {

        val cliOptions = try {
            CliOptions.parse(args)
        } catch (ex: Exception) {
            System.err.println(ex.message)
            exitProcess(1)
        }

        if (cliOptions.showHelp) {
            cliOptions.printHelpOn(System.out)
            return
        }

        LoggingConfigurer().configure(cliOptions.logging)

        val serverOptions = buildServerOptions(cliOptions)

        val server = CodeArtifactMavenProxyServer.startSync(serverOptions)

        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            server.stopSync()
        })

        server.join()
    }


    private fun buildServerOptions(cliOptions: CliOptions): CodeArtifactMavenProxyServer.Options {

        return CodeArtifactMavenProxyServer.Options(
            bindAddress = cliOptions.bindAddress,
            port = cliOptions.port,
            eagerInitAwsClient = cliOptions.eagerInit,
            tokenTtl = cliOptions.tokenTtl,
            endpointCacheTtl = cliOptions.endpointCacheTtl
        )
    }
}
