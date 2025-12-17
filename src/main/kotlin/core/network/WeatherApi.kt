package core.network


import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Extension function to fetch forecast information for given latitude and longitude
suspend fun HttpClient.getForecast(latitude: Double, longitude: Double): List<String> {
    // Build the URI using provided latitude and longitude
    val uri = "v1/forecast?latitude=$latitude&longitude=$longitude&hourly=temperature_2m"
    // Request the points data from the API
    val forecast = this.get(uri).body<ForecastResponse>()

    // Map each forecast period to a formatted string
    return forecast.hourly.time.mapIndexed { index, time ->
        """
            ${time}:
            Temperature: ${forecast.hourly.temperature2M[index]} °C
        """.trimIndent()
    }
}

@Serializable
data class ForecastResponse (
    val latitude: Double,
    val longitude: Double,

    @SerialName("generationtime_ms")
    val generationtimeMS: Double,

    @SerialName("utc_offset_seconds")
    val utcOffsetSeconds: Long,

    val timezone: String,

    @SerialName("timezone_abbreviation")
    val timezoneAbbreviation: String,

    val elevation: Double,

    @SerialName("hourly_units")
    val hourlyUnits: HourlyUnits,

    val hourly: Hourly
)

@Serializable
data class Hourly (
    val time: List<String>,

    @SerialName("temperature_2m")
    val temperature2M: List<Double>
)

@Serializable
data class HourlyUnits (
    val time: String,

    @SerialName("temperature_2m")
    val temperature2M: String
)