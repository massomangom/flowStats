package com.massomangom.courantdouala.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.massomangom.courantdouala.MainActivity
import com.massomangom.courantdouala.R
import com.massomangom.courantdouala.data.Prefs
import com.massomangom.courantdouala.data.QuartierStatus
import com.massomangom.courantdouala.data.StatusRepository
import com.massomangom.courantdouala.data.quartierById
import com.massomangom.courantdouala.logic.Utility
import com.massomangom.courantdouala.logic.formatAgo
import com.massomangom.courantdouala.logic.isStale
import java.util.concurrent.TimeUnit

/**
 * Vérifie toutes les 30 min l'état du quartier suivi et notifie les changements.
 * Pas de serveur à maintenir : 1 lecture Firestore par vérification.
 */
class StatusWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val q = quartierById(prefs.quartierId) ?: return Result.success()
        if (!prefs.notificationsEnabled) return Result.success()
        return try {
            val status = StatusRepository.get(applicationContext).fetchShard(q.shard)[q.id]
            Notifier.notifyChanges(applicationContext, prefs, q.id, status)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StatusWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("status", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

object Notifier {
    private const val CHANNEL = "status"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL, context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Marque l'état actuel comme vu (l'utilisateur a l'appli ouverte). */
    fun markSeen(prefs: Prefs, quartierId: String, status: QuartierStatus?) {
        Utility.entries.forEach { u -> status?.get(u)?.let { prefs.setSeenSince(quartierId, u, it.since) } }
    }

    fun notifyChanges(context: Context, prefs: Prefs, quartierId: String, status: QuartierStatus?) {
        val name = quartierById(quartierId)?.name ?: return
        val now = System.currentTimeMillis()
        Utility.entries.forEach { u ->
            val e = status?.get(u) ?: return@forEach
            val seen = prefs.seenSince(quartierId, u)
            prefs.setSeenSince(quartierId, u, e.since)
            if (seen == 0L || e.since <= seen || isStale(e, now)) return@forEach
            val what = if (u == Utility.POWER) "Courant" else "Eau"
            val title = if (e.on) {
                if (u == Utility.POWER) "⚡ Le courant est revenu à $name" else "💧 L'eau est revenue à $name"
            } else {
                if (u == Utility.POWER) "⚡ Coupure de courant à $name" else "💧 Coupure d'eau à $name"
            }
            val text = "$what signalé ${formatAgo(now - e.since)} par vos voisins. Touchez pour confirmer."
            show(context, if (u == Utility.POWER) 1 else 2, title, text)
        }
    }

    private fun show(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
