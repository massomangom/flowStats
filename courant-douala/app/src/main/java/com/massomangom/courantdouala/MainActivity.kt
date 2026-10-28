package com.massomangom.courantdouala

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.massomangom.courantdouala.ads.Ads
import com.massomangom.courantdouala.ads.BannerAd
import com.massomangom.courantdouala.logic.State
import com.massomangom.courantdouala.logic.Utility
import com.massomangom.courantdouala.logic.formatDuration
import com.massomangom.courantdouala.logic.stateOf
import com.massomangom.courantdouala.ui.AppNavBar
import com.massomangom.courantdouala.ui.CityScreen
import com.massomangom.courantdouala.ui.CourantTheme
import com.massomangom.courantdouala.ui.HomeScreen
import com.massomangom.courantdouala.ui.MainViewModel
import com.massomangom.courantdouala.ui.Night
import com.massomangom.courantdouala.ui.PRIVACY_TEXT
import com.massomangom.courantdouala.ui.QuartierPicker
import com.massomangom.courantdouala.ui.SettingsScreen
import com.massomangom.courantdouala.ui.UiEvent
import com.massomangom.courantdouala.ui.UiState

private const val STORE_URL = "https://play.google.com/store/apps/details?id=com.massomangom.courantdouala"

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ads.gatherConsentAndStart(this)
        setContent { CourantTheme { App() } }
    }

    override fun onStart() {
        super.onStart()
        vm.startAutoRefresh()
    }

    override fun onStop() {
        super.onStop()
        vm.stopAutoRefresh()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    @androidx.compose.runtime.Composable
    private fun App() {
        val state by vm.state.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableStateOf(0) }
        var picking by rememberSaveable { mutableStateOf(false) }
        var showPrivacy by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            vm.events.collect { ev ->
                when (ev) {
                    is UiEvent.Message -> snackbar.showSnackbar(ev.text)
                    UiEvent.Reported -> afterReport()
                }
            }
        }

        Box(Modifier.fillMaxSize().background(Night).safeDrawingPadding()) {
            if (state.quartier == null || picking) {
                QuartierPicker(
                    current = state.quartier,
                    onPick = {
                        val first = state.quartier == null
                        vm.selectQuartier(it)
                        picking = false
                        tab = 0
                        if (first) askNotificationPermission()
                    },
                    onCancel = if (state.quartier != null) ({ picking = false }) else null,
                )
                return@Box
            }
            Scaffold(
                containerColor = Night,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        BannerAd()
                        AppNavBar(tab) { tab = it }
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when (tab) {
                        0 -> HomeScreen(state, { picking = true }, vm::report) { wa -> share(shareText(state), wa) }
                        1 -> CityScreen(state, vm::refresh) { q -> vm.selectQuartier(q); tab = 0 }
                        else -> SettingsScreen(
                            state,
                            onChangeQuartier = { picking = true },
                            onNotifications = { on -> vm.setNotifications(on); if (on) askNotificationPermission() },
                            onShareApp = { share(inviteText(), whatsapp = true) },
                            onRate = { openStore() },
                            onPrivacy = { showPrivacy = true },
                            onPrivacyChoices = privacyChoicesAction(),
                        )
                    }
                }
            }
        }

        if (showPrivacy) {
            AlertDialog(
                onDismissRequest = { showPrivacy = false },
                confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("OK") } },
                title = { Text("Confidentialité") },
                text = { Text(PRIVACY_TEXT) },
            )
        }
    }

    private fun privacyChoicesAction(): (() -> Unit)? {
        val info = UserMessagingPlatform.getConsentInformation(this)
        if (info.privacyOptionsRequirementStatus != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) return null
        return { UserMessagingPlatform.showPrivacyOptionsForm(this) {} }
    }

    private fun afterReport() {
        val prefs = vm.prefs()
        // Demande d'avis Play Store une seule fois, quand l'utilisateur est engagé (5e signalement).
        if (vm.reportsCount() >= 5 && !prefs.reviewAsked) {
            prefs.reviewAsked = true
            val manager = ReviewManagerFactory.create(this)
            manager.requestReviewFlow().addOnSuccessListener { info -> manager.launchReviewFlow(this, info) }
            return
        }
        Ads.maybeShowInterstitial(this, prefs)
    }

    /** Message viral : l'état du quartier + le lien de l'appli. */
    private fun shareText(state: UiState): String {
        val q = state.quartier ?: return inviteText()
        val s = state.city[q.id]
        val lines = Utility.entries.mapNotNull { u ->
            val e = s?.get(u) ?: return@mapNotNull null
            val emoji = if (u == Utility.POWER) "⚡" else "💧"
            val what = if (u == Utility.POWER) "Courant" else "Eau"
            when (stateOf(e, state.now)) {
                State.OFF -> "$emoji $what COUPÉ${if (u == Utility.WATER) "E" else ""} à ${q.name} depuis ${formatDuration(state.now - e.since)}"
                State.ON -> "$emoji $what disponible à ${q.name}"
                State.UNKNOWN -> null
            }
        }
        if (lines.isEmpty()) return inviteText()
        return lines.joinToString("\n") +
            "\n\nSuivez le courant et l'eau de votre quartier en direct à Douala 👇\n$STORE_URL"
    }

    private fun inviteText() =
        "Tu sais avant tout le monde quand le courant ou l'eau revient dans ton quartier à Douala ⚡💧 " +
            "Appli gratuite : $STORE_URL"

    private fun share(text: String, whatsapp: Boolean) {
        val base = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (whatsapp) {
            try {
                startActivity(Intent(base).setPackage("com.whatsapp"))
                return
            } catch (_: ActivityNotFoundException) {
                // WhatsApp absent : on passe au menu de partage standard.
            }
        }
        startActivity(Intent.createChooser(base, "Partager"))
    }

    private fun openStore() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(STORE_URL)))
        }
    }
}
