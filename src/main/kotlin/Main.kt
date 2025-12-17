
import core.data.ReminderStore
import core.network.WeatherClient
import core.network.getForecast
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import java.time.Instant


//sonar, sonar-pro, sonar-reasoning, yandexgpt-lite
suspend fun main(args: Array<String>) {
    val store = ReminderStore()

    val server = Server(
        Implementation(
            name = "SemWeatherMcpServerKt", // Tool name is "weather"
            version = "1.0.0", // Version of the implementation
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
                logging = ServerCapabilities.Logging
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
        //println("latitude: $latitude, longitude: $longitude")
        if (latitude == null || longitude == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'latitude' and 'longitude' parameters are required.")),
            )
        }

        val forecast = WeatherClient.httpClient.getForecast(latitude, longitude)

        CallToolResult(content = forecast.map { TextContent(it) })
    }


    // add_reminder
    server.addTool(
        name = "add_reminder",
        description = "Add reminder with due time (ISO-8601 string)",
        inputSchema = ToolSchema(buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("text") { put("type", "string") }
                putJsonObject("due_at") {
                    put("type", "string")
                    put("description", "ISO-8601 datetime, UTC")
                }
            }
            putJsonArray("required") { add("text"); add("due_at") }
        }
        )
    ) { request ->
        val args = request.arguments ?: JsonObject(emptyMap())
        val text = args["text"]?.jsonPrimitive?.content
            ?: error("text is required")
        val dueAtStr = args["due_at"]?.jsonPrimitive?.content
            ?: error("due_at is required")
        val dueAt = Instant.parse(dueAtStr)

        store.add(text, dueAt)

        CallToolResult(
            content = listOf(
                TextContent(
                    text = "Reminder added: \"$text\" at $dueAtStr"
                )
            )
        )
    }

    // list_reminders
    server.addTool(
        name = "list_reminders",
        description = "List all reminders",
        inputSchema = ToolSchema(buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { }
        })
    ) {
        val items = store.list()
        val text = if (items.isEmpty()) {
            "No reminders"
        } else {
            items.joinToString("\n") { r ->
                "${r.id} | [${if (r.done) "x" else " "}] ${r.text} (due ${r.dueAt})"
            }
        }
        CallToolResult(
            content = listOf(TextContent(text = text))
        )
    }

    // complete_reminder
    server.addTool(
        name = "complete_reminder",
        description = "Mark reminder as done by id",
        inputSchema = ToolSchema(buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("id") { put("type", "integer") }
            }
            putJsonArray("required") { add("id") }
        }
        )
    ) { request ->
        val args = request.arguments ?: JsonObject(emptyMap())
        val id = args["id"]?.jsonPrimitive?.long
            ?: error("id is required")
        store.complete(id)

        CallToolResult(
            content = listOf(
                TextContent(text = "Reminder $id completed")
            )
        )
    }


    val transport = StdioServerTransport(
        System.`in`.asInput(),
        System.out.asSink().buffered(),
    )

    runBlocking {
        val session = server.createSession(transport)
        val sessionId = session.sessionId   // или аналогичное свойство/метод в твоей версии SDK [web:22]

        // запускаем планировщик В ЭТОМ ЖЕ runBlocking, после createSession
        checkDueReminders(server, store, sessionId)
        startScheduler(server, store, sessionId)

        val done = Job()
        session.onClose { done.complete() }
        done.join()
    }
}

private const val CHECK_INTERVAL_SECONDS = 30L

suspend fun checkDueReminders(server: Server, store: ReminderStore, sessionId: String) {
    val due = store.dueOrOverdue(Instant.now())
    // println("due: ${due.size}")
    if (due.isEmpty()) return
    val summary = buildString {
        append("Просроченные/текущие задачи:\n")
        due.forEach { r -> append("${r.id}: ${r.text} (due ${r.dueAt})\n") }
    }
    // стандартный notification уровня INFO [web:59]

    val params = LoggingMessageNotificationParams(
        level = LoggingLevel.Info,
        logger = "reminder",
        data = buildJsonObject {
            put("message", summary)
        }
    )


    server.sendLoggingMessage(
        sessionId = sessionId,
        notification = LoggingMessageNotification(params)
    )
}

suspend fun startScheduler(server: Server, store: ReminderStore, sessionId: String) = coroutineScope {
    launch {
        while (isActive) {
            delay(CHECK_INTERVAL_SECONDS * 1000)
            checkDueReminders(server, store, sessionId)
        }
    }
}


