package core.tools

import core.data.ReminderStore
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant


private const val CHECK_INTERVAL_SECONDS = 30L

fun addReminderTools(server: Server, store: ReminderStore) {
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
}

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