package core.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*

fun addAndroidTool(server: Server) {
    server.addTool(
        name = "run_android_flow",
        description = "Start AVD emulator, install APK via adb, and launch main activity",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("avd_name") {
                    put("type", "string")
                }
                putJsonObject("apk_path") {
                    put("type", "string")
                    put("description", "Absolute or relative path to APK on host")
                }
                putJsonObject("package_name") {
                    put("type", "string")
                }
                putJsonObject("activity_name") {
                    put("type", "string")
                    put("description", "Full activity name, e.g. .MainActivity or com.example.app.MainActivity")
                }
            },
            required = listOf("avd_name", "apk_path", "package_name", "activity_name")
        )
    ) { request ->
        fun runCmd(cmd: String): String {
            val process = ProcessBuilder(
                if (System.getProperty("os.name").startsWith("Windows"))
                    listOf("cmd", "/c", cmd)
                else
                    listOf("sh", "-c", cmd)
            ).redirectErrorStream(true).start()
            val out = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            return ">>> $cmd\nexit=$code\n$out\n"
        }

        val args = request.arguments ?: JsonObject(emptyMap())
        val avd = args["avd_name"]!!.jsonPrimitive.content
        val apk = args["apk_path"]!!.jsonPrimitive.content
        val pkg = args["package_name"]!!.jsonPrimitive.content
        val act = args["activity_name"]!!.jsonPrimitive.content

        val sb = StringBuilder()

        // 1. Запустить эмулятор (в фоне)
        sb.append(runCmd("emulator -avd $avd &"))

        // 2. Ждём устройство
        sb.append(runCmd("adb wait-for-device"))

        // 3. Ждём boot_completed
        sb.append(runCmd("""
        while [ "`adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r'`" != "1" ]; do
          echo "waiting for boot...";
          sleep 2;
        done
    """.trimIndent()))

        // 4. Установить APK
        sb.append(runCmd("adb install -r \"$apk\""))

        // 5. Запустить Activity
        val component = if (act.startsWith(".")) "$pkg$act" else act
        sb.append(runCmd("adb shell am start -n $pkg/$component"))

        CallToolResult(
            content = listOf(
                TextContent(text = sb.toString())
            )
        )
    }

}