package org.unbrokendome.awscodeartifact.mavenproxy.cli

import joptsimple.ValueConversionException
import joptsimple.ValueConverter
import java.time.Duration


internal object DurationValueConverter : ValueConverter<Duration> {

    private val regex = Regex(
        "^\\s*(?:(?:(?<hours>\\d+)h)?(?:(?<minutes>\\d+)m)?(?:(?<seconds>\\d+)s)?|(?<secondsOnly>\\d+))\\s*$"
    )


    override fun valueType(): Class<out Duration> =
        Duration::class.java


    override fun convert(value: String): Duration {
        val matchResult = regex.matchEntire(value)
            ?: throw ValueConversionException(
                "Invalid duration format, must be specified as number of seconds (e.g. 930) " +
                        "or with time units (e.g. 15m30s)"
            )

        val secondsOnly = matchResult.groups["secondsOnly"]?.value?.toLong()
        if (secondsOnly != null) {
            return Duration.ofSeconds(secondsOnly)
        }

        val hours = matchResult.groups["hours"]?.value?.toLong() ?: 0L
        val minutes = matchResult.groups["minutes"]?.value?.toLong() ?: 0L
        val seconds = matchResult.groups["seconds"]?.value?.toLong() ?: 0L
        return Duration.ofSeconds(hours * 3600L + minutes * 60L + seconds)
    }


    override fun valuePattern(): String = "Duration"
}
