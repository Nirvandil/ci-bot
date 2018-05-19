package cf.nirvandil.cibot.model

data class BuildResult(private val buildNumber: Int, private val successful: Boolean, private val changes: Changes) {
    override fun toString(): String {
        return if (successful) {
            buildString {
                append("Сборка номер $buildNumber завершилась успешно. ✅ \n")
                when {
                    !changes.change.isEmpty() -> {
                        append("===============================================\n").append("Список изменений:\n")
                        changes.change.forEach { append(it.author).append(":\n").append(it.comment).append("\n") }
                        append("===============================================\n")
                    }
                }
            }
        } else "К сожалению, сборка номер $buildNumber завершилась неудачно. ⛔️"
    }
}

data class Changes(val change: List<Change>)

data class Change(val comment: String, val commitUrl: String, val author: String)