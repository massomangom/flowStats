package com.massomangom.courantdouala.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.massomangom.courantdouala.data.Prefs
import com.massomangom.courantdouala.data.Quartier
import com.massomangom.courantdouala.data.QuartierStatus
import com.massomangom.courantdouala.data.StatusRepository
import com.massomangom.courantdouala.data.quartierById
import com.massomangom.courantdouala.logic.Utility
import com.massomangom.courantdouala.logic.cooldownRemaining
import com.massomangom.courantdouala.logic.formatDuration
import com.massomangom.courantdouala.work.Notifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val quartier: Quartier? = null,
    val city: Map<String, QuartierStatus> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val now: Long = System.currentTimeMillis(),
    val notificationsEnabled: Boolean = true,
    val isLive: Boolean = true,
    val sending: Utility? = null,
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    /** Signalement envoyé : occasion d'afficher un interstitiel / demander un avis. */
    data object Reported : UiEvent
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val repo = StatusRepository.get(app)

    private val _state = MutableStateFlow(
        UiState(
            quartier = quartierById(prefs.quartierId),
            notificationsEnabled = prefs.notificationsEnabled,
            isLive = repo.isLive,
        ),
    )
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()
    private var autoRefresh: Job? = null

    /** Rafraîchit toute la ville (4 lectures) puis toutes les 2 min tant que l'appli est visible. */
    fun startAutoRefresh() {
        autoRefresh?.cancel()
        autoRefresh = viewModelScope.launch {
            while (true) {
                refresh()
                delay(120_000)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefresh?.cancel()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val city = repo.fetchAll()
                _state.update { it.copy(city = city, loading = false, now = System.currentTimeMillis()) }
                _state.value.quartier?.let { Notifier.markSeen(prefs, it.id, city[it.id]) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = "Pas de connexion. Les dernières infos restent affichées.")
                }
            }
        }
    }

    fun selectQuartier(q: Quartier) {
        prefs.quartierId = q.id
        _state.update { it.copy(quartier = q) }
        Notifier.markSeen(prefs, q.id, _state.value.city[q.id])
    }

    fun setNotifications(enabled: Boolean) {
        prefs.notificationsEnabled = enabled
        _state.update { it.copy(notificationsEnabled = enabled) }
    }

    fun report(u: Utility, on: Boolean) {
        val q = _state.value.quartier ?: return
        if (_state.value.sending != null) return
        val now = System.currentTimeMillis()
        val wait = cooldownRemaining(prefs.lastReportAt(q.id, u), prefs.lastReportOn(q.id, u), on, now)
        if (wait > 0) {
            _events.tryEmit(UiEvent.Message("Déjà signalé, merci ! Réessayez dans ${formatDuration(wait)}."))
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(sending = u) }
            try {
                val entry = repo.report(q, u, on)
                prefs.saveReport(q.id, u, on, now)
                prefs.setSeenSince(q.id, u, entry.since)
                prefs.reportsCount = prefs.reportsCount + 1
                _state.update { s ->
                    val cur = s.city[q.id] ?: QuartierStatus(null, null)
                    val upd = if (u == Utility.POWER) cur.copy(power = entry) else cur.copy(water = entry)
                    s.copy(city = s.city + (q.id to upd), now = System.currentTimeMillis(), sending = null)
                }
                val label = if (entry.n > 1) "Merci ! Vous êtes ${entry.n} à confirmer." else "Merci ! Vos voisins sont prévenus."
                _events.tryEmit(UiEvent.Message(label))
                _events.tryEmit(UiEvent.Reported)
            } catch (e: Exception) {
                _state.update { it.copy(sending = null) }
                _events.tryEmit(UiEvent.Message("Échec de l'envoi. Vérifiez votre connexion."))
            }
        }
    }

    fun reportsCount() = prefs.reportsCount
    fun prefs() = prefs
}
