package com.massomangom.courantdouala.data

import android.content.Context
import com.massomangom.courantdouala.logic.Utility

/** Préférences locales de l'appareil (aucune donnée personnelle envoyée). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("courant", Context.MODE_PRIVATE)

    var quartierId: String?
        get() = sp.getString("quartier", null)
        set(v) = sp.edit().putString("quartier", v).apply()

    var notificationsEnabled: Boolean
        get() = sp.getBoolean("notif", true)
        set(v) = sp.edit().putBoolean("notif", v).apply()

    var reportsCount: Int
        get() = sp.getInt("reports", 0)
        set(v) = sp.edit().putInt("reports", v).apply()

    var lastInterstitialAt: Long
        get() = sp.getLong("inter_at", 0)
        set(v) = sp.edit().putLong("inter_at", v).apply()

    var reviewAsked: Boolean
        get() = sp.getBoolean("review_asked", false)
        set(v) = sp.edit().putBoolean("review_asked", v).apply()

    fun lastReportAt(q: String, u: Utility): Long = sp.getLong("rep_at_${q}_${u.key}", 0)
    fun lastReportOn(q: String, u: Utility): Boolean? =
        if (sp.contains("rep_on_${q}_${u.key}")) sp.getBoolean("rep_on_${q}_${u.key}", false) else null

    fun saveReport(q: String, u: Utility, on: Boolean, at: Long) {
        sp.edit().putLong("rep_at_${q}_${u.key}", at).putBoolean("rep_on_${q}_${u.key}", on).apply()
    }

    /** Dernier changement d'état déjà vu (pour ne notifier que les nouveautés). */
    fun seenSince(q: String, u: Utility): Long = sp.getLong("seen_${q}_${u.key}", 0)
    fun setSeenSince(q: String, u: Utility, since: Long) {
        sp.edit().putLong("seen_${q}_${u.key}", since).apply()
    }
}
