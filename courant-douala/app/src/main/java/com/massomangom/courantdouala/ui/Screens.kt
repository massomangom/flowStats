package com.massomangom.courantdouala.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.massomangom.courantdouala.data.QUARTIERS
import com.massomangom.courantdouala.data.Quartier
import com.massomangom.courantdouala.data.QuartierStatus
import com.massomangom.courantdouala.logic.Entry
import com.massomangom.courantdouala.logic.State
import com.massomangom.courantdouala.logic.Utility
import com.massomangom.courantdouala.logic.formatAgo
import com.massomangom.courantdouala.logic.formatDuration
import com.massomangom.courantdouala.logic.offDuration
import com.massomangom.courantdouala.logic.stateOf
import java.text.Normalizer

private fun icon(u: Utility): ImageVector = if (u == Utility.POWER) Icons.Filled.Bolt else Icons.Filled.WaterDrop
private fun label(u: Utility) = if (u == Utility.POWER) "Courant" else "Eau"

fun stateLabel(u: Utility, s: State) = when (s) {
    State.ON -> "Disponible"
    State.OFF -> if (u == Utility.POWER) "Coupé" else "Coupée"
    State.UNKNOWN -> "Pas d'info récente"
}

private fun normalize(s: String) =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

@Composable
fun AppNavBar(tab: Int, onTab: (Int) -> Unit) {
    NavigationBar(containerColor = Night) {
        NavigationBarItem(tab == 0, { onTab(0) }, { Icon(Icons.Filled.Home, null) }, label = { Text("Mon quartier") })
        NavigationBarItem(tab == 1, { onTab(1) }, { Icon(Icons.Filled.LocationCity, null) }, label = { Text("Douala") })
        NavigationBarItem(tab == 2, { onTab(2) }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Réglages") })
    }
}

// ---------------------------------------------------------------- Choix du quartier

@Composable
fun QuartierPicker(current: Quartier?, onPick: (Quartier) -> Unit, onCancel: (() -> Unit)?) {
    var query by remember { mutableStateOf("") }
    val list = QUARTIERS.filter { normalize(it.name).contains(normalize(query.trim())) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(28.dp))
        Text("⚡💧", fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text("Dans quel quartier habitez-vous ?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Vous serez prévenu dès que le courant ou l'eau est coupé — ou revient — chez vous.",
            color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Rechercher : Makepe, Bonabéri, PK14…") },
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(list, key = { it.id }) { q ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(q) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(q.name, Modifier.weight(1f), fontSize = 17.sp, fontWeight = if (q == current) FontWeight.Bold else null)
                    if (q == current) Text("Actuel", color = Bolt, fontSize = 13.sp)
                }
            }
            if (list.isEmpty()) item {
                Text(
                    "Quartier introuvable. Choisissez le plus proche de chez vous.",
                    color = Muted, modifier = Modifier.padding(12.dp),
                )
            }
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Annuler") }
        }
    }
}

// ---------------------------------------------------------------- Accueil : mon quartier

@Composable
fun HomeScreen(
    state: UiState,
    onChangeQuartier: () -> Unit,
    onReport: (Utility, Boolean) -> Unit,
    onShare: (whatsapp: Boolean) -> Unit,
) {
    val q = state.quartier ?: return
    val status = state.city[q.id]
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onChangeQuartier)) {
            Column(Modifier.weight(1f)) {
                Text("MON QUARTIER", color = Muted, fontSize = 12.sp, letterSpacing = 1.5.sp)
                Text(q.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Text("Changer", color = Bolt, fontSize = 14.sp)
            Icon(Icons.Filled.ChevronRight, null, tint = Bolt)
        }
        if (!state.isLive) DemoBanner()
        state.error?.let { Text(it, color = Cut, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(14.dp))
        Utility.entries.forEach { u ->
            UtilityCard(u, status?.get(u), state.now, state.sending == u) { on -> onReport(u, on) }
            Spacer(Modifier.height(14.dp))
        }
        Text(
            "Votre signalement prévient instantanément tout le quartier. Plus vous êtes nombreux, plus c'est fiable.",
            color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onShare(true) }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
            ) { Text("Partager sur WhatsApp", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(onClick = { onShare(false) }) { Icon(Icons.Filled.Share, "Partager") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DemoBanner() {
    Text(
        "Mode démo : données fictives (Firebase non configuré).",
        color = Night, fontSize = 12.sp,
        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(Bolt)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun UtilityCard(u: Utility, e: Entry?, now: Long, sending: Boolean, onReport: (Boolean) -> Unit) {
    val s = stateOf(e, now)
    val color = utilityColor(u)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NightCard)
            .border(1.dp, if (s == State.OFF) Cut.copy(alpha = 0.6f) else NightLine, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon(u), null, tint = color) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label(u), color = Muted, fontSize = 14.sp)
                Text(stateLabel(u, s), color = stateColor(s), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            if (sending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        if (e != null && s != State.UNKNOWN) {
            Spacer(Modifier.height(10.dp))
            Text(
                (if (s == State.OFF) "Coupé depuis " else "Revenu depuis ") + formatDuration(now - e.since),
                fontSize = 15.sp,
            )
            Text(
                "Confirmé ${formatAgo(now - e.last)} · ${e.n} voisin${if (e.n > 1) "s" else ""}",
                color = Muted, fontSize = 13.sp,
            )
        }
        val off24 = offDuration(e, now)
        if (off24 >= 15 * 60_000L) {
            Text(
                "Sur 24 h : ${formatDuration(off24)} sans ${if (u == Utility.POWER) "courant" else "eau"}",
                color = color, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onReport(false) }, enabled = !sending, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cut, contentColor = Color.White),
            ) { Text(if (u == Utility.POWER) "Coupé chez moi" else "Coupée chez moi", fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = { onReport(true) }, enabled = !sending, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ok, contentColor = Night),
            ) { Text(if (u == Utility.POWER) "Il y a le courant" else "Il y a l'eau", fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------- Toute la ville

private enum class Filter(val label: String) { ALL("Tous"), NO_POWER("Sans courant"), NO_WATER("Sans eau") }

@Composable
fun CityScreen(state: UiState, onRefresh: () -> Unit, onFollow: (Quartier) -> Unit) {
    var filter by remember { mutableStateOf(Filter.ALL) }
    var query by remember { mutableStateOf("") }
    val now = state.now
    fun st(q: Quartier, u: Utility) = stateOf(state.city[q.id]?.get(u), now)
    val offPower = QUARTIERS.count { st(it, Utility.POWER) == State.OFF }
    val offWater = QUARTIERS.count { st(it, Utility.WATER) == State.OFF }
    val rows = QUARTIERS
        .filter { normalize(it.name).contains(normalize(query.trim())) }
        .filter {
            when (filter) {
                Filter.ALL -> true
                Filter.NO_POWER -> st(it, Utility.POWER) == State.OFF
                Filter.NO_WATER -> st(it, Utility.WATER) == State.OFF
            }
        }
        // Coupures d'abord, puis infos connues, puis le reste.
        .sortedWith(compareBy({ -(score(st(it, Utility.POWER)) + score(st(it, Utility.WATER))) }, { it.name }))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Douala en direct", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else TextButton(onClick = onRefresh) { Text("Actualiser") }
            }
            if (!state.isLive) DemoBanner()
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("$offPower", "quartiers sans courant", Bolt, Modifier.weight(1f))
                StatTile("$offWater", "quartiers sans eau", Drop, Modifier.weight(1f))
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Rechercher un quartier") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
        }
        items(rows, key = { it.id }) { q ->
            CityRow(q, state.city[q.id], now, q == state.quartier) { onFollow(q) }
            HorizontalDivider(color = NightLine)
        }
        if (rows.isEmpty()) item {
            Text("Aucun quartier dans ce filtre pour le moment.", color = Muted, modifier = Modifier.padding(vertical = 20.dp))
        }
    }
}

private fun score(s: State) = when (s) {
    State.OFF -> 10
    State.ON -> 1
    State.UNKNOWN -> 0
}

@Composable
private fun StatTile(value: String, caption: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(NightCard).padding(14.dp)) {
        Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(caption, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun CityRow(q: Quartier, status: QuartierStatus?, now: Long, mine: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(q.name + if (mine) "  ★" else "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val latest = Utility.entries.mapNotNull { u -> status?.get(u)?.takeIf { stateOf(it, now) != State.UNKNOWN } }
                .maxByOrNull { it.last }
            Text(latest?.let { "Mis à jour ${formatAgo(now - it.last)}" } ?: "Aucun signalement récent", color = Muted, fontSize = 12.sp)
        }
        Utility.entries.forEach { u -> StatusPill(u, status?.get(u), now) }
    }
}

@Composable
private fun StatusPill(u: Utility, e: Entry?, now: Long) {
    val s = stateOf(e, now)
    val c = stateColor(s)
    Row(
        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon(u), null, tint = utilityColor(u), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            when (s) {
                State.ON -> "OK"
                State.OFF -> formatDuration(now - (e?.since ?: now)).replace(" ", "")
                State.UNKNOWN -> "?"
            },
            color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------------------------------------------------------------- Réglages

@Composable
fun SettingsScreen(
    state: UiState,
    onChangeQuartier: () -> Unit,
    onNotifications: (Boolean) -> Unit,
    onShareApp: () -> Unit,
    onRate: () -> Unit,
    onPrivacy: () -> Unit,
    onPrivacyChoices: (() -> Unit)?,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Réglages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        SettingRow("Mon quartier", state.quartier?.name ?: "—", onChangeQuartier)
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Alertes coupure / retour", fontSize = 16.sp)
                Text("Vérification automatique toutes les 30 min", color = Muted, fontSize = 13.sp)
            }
            Switch(checked = state.notificationsEnabled, onCheckedChange = onNotifications)
        }
        HorizontalDivider(color = NightLine)
        SettingRow("Inviter des voisins", "Plus on est nombreux, plus c'est fiable", onShareApp)
        SettingRow("Noter l'appli", "Votre avis nous aide énormément", onRate)
        SettingRow("Confidentialité", "Aucun compte, aucune donnée personnelle", onPrivacy)
        if (onPrivacyChoices != null) SettingRow("Choix publicitaires", "Gérer votre consentement", onPrivacyChoices)
        Spacer(Modifier.height(20.dp))
        Text(
            "Courant Douala est une appli communautaire indépendante. Elle n'est affiliée à aucun fournisseur " +
                "d'électricité ou d'eau. Les informations proviennent des signalements des habitants.",
            color = Muted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Muted)
    }
    HorizontalDivider(color = NightLine)
}

const val PRIVACY_TEXT = """Courant Douala ne demande ni nom, ni numéro, ni e-mail, ni localisation GPS.

Ce qui est enregistré :
• Sur votre téléphone : le quartier choisi et vos réglages.
• Sur nos serveurs (Google Firebase) : vos signalements « coupé / revenu » pour un quartier, avec l'heure, associés à un identifiant anonyme aléatoire.

Publicité : l'appli est gratuite grâce à Google AdMob, qui peut utiliser l'identifiant publicitaire de l'appareil. Vous pouvez gérer ce choix dans les réglages Android (Google > Annonces) et, si applicable, via « Choix publicitaires ».

Aucune donnée n'est vendue. Les signalements anonymes sont visibles par tous les utilisateurs."""
