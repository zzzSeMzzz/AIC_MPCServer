package core.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json


// Create an HTTP client with a default request configuration and JSON content negotiation

object WeatherClient {

    private const val BASE_URL = "https://api.open-meteo.com/"

    val httpClient = HttpClient {
        defaultRequest {
            url(BASE_URL)
            headers {
                append("Accept", "application/geo+json")
                append("User-Agent", "WeatherApiClient/1.0")
            }
            contentType(ContentType.Application.Json)
        }
        // Install content negotiation plugin for JSON serialization/deserialization
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    allowSpecialFloatingPointValues = true
                },
            )
        }

        /*install(Logging) {
            logger = Logger.DEFAULT // Выводит логи в stdout
            level = LogLevel.ALL    // Логировать всё: запросы, заголовки, тела
        }*/
    }
}