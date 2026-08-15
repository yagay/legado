package io.legado.app.enhance.review

import io.legado.app.data.entities.rule.ReviewRule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Runtime-only bridge for passing review contexts into ReviewDetailDialog without mutating ReadBook. */
internal object ReviewDialogSessionStore {

    data class Session(
        val context: ReviewContext,
        val rule: ReviewRule? = null,
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    fun put(context: ReviewContext, rule: ReviewRule? = null): String {
        val id = UUID.randomUUID().toString()
        sessions[id] = Session(context, rule)
        return id
    }

    fun get(id: String?): Session? {
        if (id.isNullOrBlank()) return null
        return sessions[id]
    }

    fun remove(id: String?) {
        if (id.isNullOrBlank()) return
        sessions.remove(id)
    }
}
