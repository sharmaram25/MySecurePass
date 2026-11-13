package com.mysecurepass.core.vault

enum class HealthIssue(val label: String) {
    WEAK("Weak password"),
    REUSED("Reused password"),
    OLD("Not changed in over a year"),
    MISSING_2FA("No two-factor code saved"),
}

data class HealthReport(
    val issues: Map<String, Set<HealthIssue>>,
    val total: Int,
) {
    fun count(issue: HealthIssue) = issues.values.count { issue in it }
    val healthy: Int get() = total - issues.count { it.value.any { i -> i != HealthIssue.MISSING_2FA } }

    /** 0–100. Missing 2FA is informational and does not affect the score. */
    val score: Int get() = if (total == 0) 100 else (healthy * 100) / total
}

object PasswordHealth {
    private const val YEAR_MS = 365L * 24 * 60 * 60 * 1000

    /**
     * @param scoreOf returns a 0–4 strength score for a password (zxcvbn on Android).
     */
    fun analyze(entries: List<VaultEntry>, now: Long, scoreOf: (String) -> Int): HealthReport {
        val withPassword = entries.active().filter { it.type == EntryType.LOGIN && it.password.isNotEmpty() }
        val reuseCounts = withPassword.groupingBy { it.password }.eachCount()
        val issues = HashMap<String, Set<HealthIssue>>()
        for (e in withPassword) {
            val set = buildSet {
                if (scoreOf(e.password) < 3) add(HealthIssue.WEAK)
                if ((reuseCounts[e.password] ?: 0) > 1) add(HealthIssue.REUSED)
                if (now - e.passwordChangedAt > YEAR_MS) add(HealthIssue.OLD)
                if (e.totp.isBlank() && e.urls.isNotEmpty()) add(HealthIssue.MISSING_2FA)
            }
            if (set.isNotEmpty()) issues[e.id] = set
        }
        return HealthReport(issues, withPassword.size)
    }
}
