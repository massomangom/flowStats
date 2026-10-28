package com.massomangom.courantdouala.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.massomangom.courantdouala.data.DemoRepository
import com.massomangom.courantdouala.data.QuartierStatus
import com.massomangom.courantdouala.data.quartierById
import com.massomangom.courantdouala.logic.HOUR
import com.massomangom.courantdouala.logic.MINUTE
import com.massomangom.courantdouala.logic.applyReport
import com.massomangom.courantdouala.ui.AppNavBar
import com.massomangom.courantdouala.ui.CityScreen
import com.massomangom.courantdouala.ui.CourantTheme
import com.massomangom.courantdouala.ui.HomeScreen
import com.massomangom.courantdouala.ui.Night
import com.massomangom.courantdouala.ui.QuartierPicker
import com.massomangom.courantdouala.ui.SettingsScreen
import com.massomangom.courantdouala.ui.UiState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rend les vrais écrans de l'appli en PNG (1200×2340) pour la fiche Play Store. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h780dp-xxhdpi", application = android.app.Application::class)
class StoreScreenshots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val state: UiState by lazy {
        val city = runBlocking { DemoRepository().fetchAll() }.toMutableMap()
        // Makepe : coupure de courant en cours depuis 2 h 15, confirmée par 12 voisins.
        var power = applyReport(null, true, now - 20 * HOUR)
        power = applyReport(power, false, now - 9 * HOUR)
        power = applyReport(power, true, now - 6 * HOUR)
        power = applyReport(power, false, now - (2 * HOUR + 15 * MINUTE)).copy(last = now - 3 * MINUTE, n = 12)
        val water = applyReport(null, true, now - 5 * HOUR).copy(last = now - 25 * MINUTE, n = 4)
        city["makepe"] = QuartierStatus(power, water)
        UiState(quartier = quartierById("makepe"), city = city, now = now, isLive = true)
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        rule.setContent { CourantTheme { Box(Modifier.fillMaxSize().background(Night)) { content() } } }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        val dir = File(System.getProperty("screenshotsDir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Tabbed(tab: Int, content: @Composable () -> Unit) {
        Scaffold(containerColor = Night, bottomBar = { AppNavBar(tab) {} }) { pad ->
            Box(Modifier.padding(pad)) { content() }
        }
    }

    @Test fun home() = shoot("1-mon-quartier") { Tabbed(0) { HomeScreen(state, {}, { _, _ -> }, {}) } }

    @Test fun city() = shoot("2-douala-en-direct") { Tabbed(1) { CityScreen(state, {}, {}) } }

    @Test fun picker() = shoot("3-choix-quartier") { QuartierPicker(null, {}, null) }

    @Test fun settings() = shoot("4-reglages") {
        Tabbed(2) { SettingsScreen(state, {}, {}, {}, {}, {}, null) }
    }
}
