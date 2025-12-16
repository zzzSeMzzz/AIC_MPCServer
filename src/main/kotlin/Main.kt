
import core.network.WeatherClient
import core.network.getForecast
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*


//sonar, sonar-pro, sonar-reasoning, yandexgpt-lite
suspend fun main(args: Array<String>) {
    val server = Server(
        Implementation(
            name = "SemWeatherMcpServerKt", // Tool name is "weather"
            version = "1.0.0", // Version of the implementation
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true)
            ),
        ),
    )

   /* server.addResource(
        uri = "file:///example.txt",
        name = "Example Resource",
        description = "An example text file",
        mimeType = "text/plain"
    ) { request ->
        ReadResourceResult(
            contents = listOf(
                TextResourceContents(
                    text = "This is the content of the example resource.",
                    uri = request.uri,
                    mimeType = "text/plain"
                )
            )
        )
    }*/

    server.addTool(
        name = "get_forecast",
        description = """
            Прогноз погоды для указанной latitude/longitude
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("latitude") {
                    put("type", "number")
                }
                putJsonObject("longitude") {
                    put("type", "number")
                }
            },
            required = listOf("latitude", "longitude"),
        ),
    ) { request ->
        val latitude = request.arguments?.get("latitude")?.jsonPrimitive?.doubleOrNull
        val longitude = request.arguments?.get("longitude")?.jsonPrimitive?.doubleOrNull
        if (latitude == null || longitude == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'latitude' and 'longitude' parameters are required.")),
            )
        }

        val forecast = WeatherClient.httpClient.getForecast(latitude, longitude)

        CallToolResult(content = forecast.map { TextContent(it) })
    }

    val transport = StdioServerTransport(
        System.`in`.asInput(),
        System.out.asSink().buffered(),
    )

    runBlocking {
        val session = server.createSession(transport)
        val done = Job()
        session.onClose {
            done.complete()
        }
        done.join()
    }
}


