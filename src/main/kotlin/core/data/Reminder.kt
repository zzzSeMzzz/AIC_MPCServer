package core.data


import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Serializable
data class Reminder(
    val id: Long,
    val text: String,
    val dueAt: String,   // ISO‑строка, чтобы не париться с сериализатором
    val done: Boolean = false,
    val createdAt: String
)


class ReminderStore(
    private val file: Path = Path.of("reminders.json")
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var nextId: Long = 1
    private var cache: MutableList<Reminder> = mutableListOf()

    init {
        if (Files.exists(file)) {
            val content = Files.readString(file)
            if (content.isNotBlank()) {
                cache = json.decodeFromString<List<Reminder>>(content).toMutableList()
                nextId = (cache.maxOfOrNull { it.id } ?: 0L) + 1
            }
        } else {
            persist()
        }
    }

    @Synchronized
    fun add(text: String, dueAt: Instant) {
        val now = Instant.now().toString()
        val reminder = Reminder(
            id = nextId++,
            text = text,
            dueAt = dueAt.toString(),
            createdAt = now
        )
        cache.add(reminder)
        persist()
    }

    @Synchronized
    fun list(): List<Reminder> = cache.toList()

    @Synchronized
    fun complete(id: Long) {
        cache = cache.map {
            if (it.id == id) it.copy(done = true) else it
        }.toMutableList()
        persist()
    }

    @Synchronized
    fun dueOrOverdue(now: Instant): List<Reminder> {
        val nowStr = now.toString()
        return cache.filter { !it.done && it.dueAt <= nowStr }
            .sortedBy { it.dueAt }
    }

    @Synchronized
    private fun persist() {
        val content = json.encodeToString(cache)
        Files.writeString(file, content)
    }
}