package com.massomangom.courantdouala.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.massomangom.courantdouala.BuildConfig
import com.massomangom.courantdouala.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Monétisation AdMob : bannière discrète + interstitiel plafonné après un signalement.
 * Le consentement (UMP) est demandé avant toute pub, comme l'exige Google.
 */
object Ads {
    private val started = AtomicBoolean(false)
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private var interstitial: InterstitialAd? = null

    /** Intervalle minimal entre deux interstitiels, et nb de signalements "offerts" au début. */
    private const val INTERSTITIAL_GAP_MS = 5 * 60_000L
    private const val FREE_REPORTS = 2

    fun gatherConsentAndStart(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (info.canRequestAds()) start(activity)
                }
            },
            { if (info.canRequestAds()) start(activity) },
        )
        if (info.canRequestAds()) start(activity)
    }

    private fun start(activity: Activity) {
        if (!started.compareAndSet(false, true)) return
        val app = activity.applicationContext
        Thread {
            MobileAds.initialize(app) {
                _ready.value = true
                activity.runOnUiThread { loadInterstitial(app) }
            }
        }.start()
    }

    private fun loadInterstitial(context: Context) {
        InterstitialAd.load(
            context, BuildConfig.INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { interstitial = null }
            },
        )
    }

    /** Appelé après un signalement réussi : transition naturelle, conforme aux règles AdMob. */
    fun maybeShowInterstitial(activity: Activity, prefs: Prefs) {
        val ad = interstitial ?: return
        val now = System.currentTimeMillis()
        if (prefs.reportsCount <= FREE_REPORTS) return
        if (now - prefs.lastInterstitialAt < INTERSTITIAL_GAP_MS) return
        prefs.lastInterstitialAt = now
        interstitial = null
        ad.show(activity)
        loadInterstitial(activity.applicationContext)
    }
}

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val ready by Ads.ready.collectAsState()
    if (!ready) return
    val widthDp = LocalConfiguration.current.screenWidthDp
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                adUnitId = BuildConfig.BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
