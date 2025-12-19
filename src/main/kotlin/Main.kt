import core.data.ReminderStore
import core.tools.addAndroidTool
import core.tools.addReminderTools
import core.tools.addSaveToFileTool
import core.tools.addWeatherTool
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered


//sonar, sonar-pro, sonar-reasoning, yandexgpt-lite
suspend fun main(args: Array<String>) {
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

    addWeatherTool(server)

    addReminderTools(server, ReminderStore())

    addSaveToFileTool(server)

    addAndroidTool(server)

    val transport = StdioServerTransport(
        System.`in`.asInput(),
        System.out.asSink().buffered(),
    )

    runBlocking {
        val session = server.createSession(transport)
        val sessionId = session.sessionId   // или аналогичное свойство/метод в твоей версии SDK [web:22]

        // запускаем планировщик В ЭТОМ ЖЕ runBlocking, после createSession
        //checkDueReminders(server, store, sessionId)
        //startScheduler(server, store, sessionId)

        val done = Job()
        session.onClose { done.complete() }
        done.join()
    }
}






