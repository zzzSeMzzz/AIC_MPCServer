package core.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*

fun addAndroidTool(server: Server) {
    server.addTool(
        name = "deploy_android_app",
        description = "Install an APK on the connected Android emulator/device via adb and start its main activity",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("apk_path") {
                    put("type", "string")
                    put("description", "Absolute or relative path to APK on host")
                }
                putJsonObject("package_name") {
                    put("type", "string")
                    put("description", "Application package name, e.g. com.example.app")
                }
                putJsonObject("activity_name") {
                    put("type", "string")
                    put("description", "Activity name, e.g. .MainActivity or com.example.app.MainActivity")
                }
            },
            required = listOf("apk_path", "package_name", "activity_name")
        )
    ) { request ->
        val args = request.arguments ?: JsonObject(emptyMap())

        val apkPath = args["apk_path"]?.jsonPrimitive?.content
            ?: error("apk_path is required")
        val pkg = args["package_name"]?.jsonPrimitive?.content
            ?: error("package_name is required")
        val act = args["activity_name"]?.jsonPrimitive?.content
            ?: error("activity_name is required")

        val component = if (act.startsWith(".")) "$pkg$act" else act

        fun runCmd(cmd: List<String>): String {
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            return ">>> ${cmd.joinToString(" ")}\nexit=$code\n$out\n"
        }

        val sb = StringBuilder()

        // 1. Проверяем, что эмулятор/устройство подключено
        sb.append(runCmd(listOf("adb", "devices")))

        // 2. Установка APK с перезаписью
        sb.append(runCmd(listOf("adb", "install", "-r", apkPath)))

        // 3. Запуск Activity
        sb.append(runCmd(listOf("adb", "shell", "am", "start", "-n", "$pkg/$component")))

        CallToolResult(
            content = listOf(
                TextContent(text = sb.toString())
            )
        )
    }

}