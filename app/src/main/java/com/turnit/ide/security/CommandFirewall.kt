package com.turnit.ide.security

sealed class FirewallResult {
    data object Safe : FirewallResult()
    data class Blocked(val reason: String) : FirewallResult()
}

object CommandFirewall {
    private val denylist: List<Pair<Regex, String>> = listOf(
        Regex("""\brm\s+-rf\s+/\b""", RegexOption.IGNORE_CASE) to "rm -rf / is destructive",
        Regex("""\brm\s+-rf\s+\*""", RegexOption.IGNORE_CASE) to "rm -rf * is destructive",
        Regex("""\bmkfs\b""", RegexOption.IGNORE_CASE) to "mkfs can destroy filesystems",
        Regex("""\bdd\b""", RegexOption.IGNORE_CASE) to "dd can overwrite disks",
        Regex("""\bsu\b""", RegexOption.IGNORE_CASE) to "su is not allowed",
        Regex("""\bchmod\s+-R\s+777\b""", RegexOption.IGNORE_CASE) to "chmod -R 777 is unsafe"
    )

    fun analyzeCommand(command: String): FirewallResult {
        val trimmed = command.trim()
        if (trimmed.isBlank()) {
            return FirewallResult.Blocked("Command is empty")
        }
        val match = denylist.firstOrNull { (pattern, _) -> pattern.containsMatchIn(trimmed) }
        return if (match != null) {
            FirewallResult.Blocked(match.second)
        } else {
            FirewallResult.Safe
        }
    }
}
