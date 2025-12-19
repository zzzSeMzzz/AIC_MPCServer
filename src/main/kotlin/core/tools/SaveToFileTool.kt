package core.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption


fun addSaveToFileTool(server: Server) {
    server.addTool(
        name = "save_to_file",
        description = "Save given text content into a file on disk",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("path") {
                    put("type", "string")
                    put("description", "Relative file path to save to")
                }
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "Text content to write")
                }
                // можно добавить optional append: bool, если понадобится
            },
            required = listOf("path", "content")
        )
    ) { request ->
        val args = request.arguments ?: JsonObject(emptyMap())

        val pathStr = args["path"]?.jsonPrimitive?.content
            ?: error("path is required")
        val content = args["content"]?.jsonPrimitive?.content
            ?: error("content is required")

        // базовая защита: сохраняем только в папку data/ и запрещаем выход выше
        val baseDir = Path.of("data").toAbsolutePath()
        Files.createDirectories(baseDir)
        val target = baseDir.resolve(pathStr).normalize()

        require(target.startsWith(baseDir)) {
            "Path escape is not allowed"
        }

        Files.createDirectories(target.parent)
        Files.writeString(
            target,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )

        CallToolResult(
            content = listOf(
                TextContent(
                    text = "Saved to ${target.toAbsolutePath()}"
                )
            )
        )
    }
}