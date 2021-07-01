package org.unbrokendome.awscodeartifact.mavenproxy.cli

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory


internal class LoggingConfigurer {

    companion object {
        private const val SimplePattern = "%msg%n%ex{short}"
        private const val FullPattern =
            "%d{HH:mm:ss.SSS} %highlight{%-5level}{STYLE=Logback} [%t] %logger{1.} -- %msg%n%xEx"
    }


    fun configure(options: CliOptions.Logging) {

        val configuration = createLog4jConfiguration(options)
        val loggerContext = Configurator.initialize(configuration)

        loggerContext.start()
    }


    private fun createLog4jConfiguration(options: CliOptions.Logging): Configuration =
        ConfigurationBuilderFactory.newConfigurationBuilder().run {

            setStatusLevel(Level.ERROR)

            // Construct the appender
            val outputPattern = if (options.isSimpleLogging) SimplePattern else FullPattern
            add(
                newAppender("console", "CONSOLE")
                    .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                    .add(
                        newLayout("PatternLayout")
                            .addAttribute("pattern", outputPattern)
                    )
            )

            // Root logger
            add(
                newRootLogger(Level.ERROR)
                    .add(
                        newAppenderRef("console")
                    )
            )

            // Application logger
            val applicationLogLevel = if (options.debug) Level.DEBUG else Level.INFO
            add(
                newLogger("org.unbrokendome.awscodeartifact.mavenproxy", applicationLogLevel)
            )

            // AWS SDK logger
            val awsSdkLogLevel = if (options.awsDebug) Level.DEBUG else Level.WARN
            add(
                newLogger("software.amazon.awssdk", awsSdkLogLevel)
            )

            // Wiretap loggers
            options.wiretapTargets
                .flatMap { it.loggerNames }
                .forEach { wiretapLoggerName ->
                    add(
                        newLogger(wiretapLoggerName, Level.TRACE)
                    )
                }

            build()
        }
}
