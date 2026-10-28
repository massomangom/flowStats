package com.massomangom.courantdouala.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.massomangom.courantdouala.logic.DAY
import com.massomangom.courantdouala.logic.Entry
import com.massomangom.courantdouala.logic.HOUR
import com.massomangom.courantdouala.logic.MINUTE
import com.massomangom.courantdouala.logic.Utility
import com.massomangom.courantdouala.logic.applyReport
import com.massomangom.courantdouala.logic.entryFromMap
import com.massomangom.courantdouala.logic.toMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

data class QuartierStatus(val power: Entry?, val water: Entry?) {
    fun get(u: Utility): Entry? = if (u == Utility.POWER) power else water
}

interface StatusRepository {
    /** true si les données sont partagées entre tous les utilisateurs (Firebase configuré). */
    val isLive: Boolean
    suspend fun fetchShard(shard: Int): Map<String, QuartierStatus>
    suspend fun report(q: Quartier, u: Utility, on: Boolean): Entry

    suspend fun fetchAll(): Map<String, QuartierStatus> = coroutineScope {
        (0 until SHARD_COUNT).map { async { fetchShard(it) } }.awaitAll()
            .fold(emptyMap()) { acc, m -> acc + m }
    }

    companion object {
        @Volatile private var instance: StatusRepository? = null

        fun get(context: Context): StatusRepository = instance ?: synchronized(this) {
            instance ?: (
                if (FirebaseApp.getApps(context).isNotEmpty()) FirestoreRepository() else DemoRepository()
                ).also { instance = it }
        }
    }
}

private fun parseShard(q: Any?): Map<String, QuartierStatus> {
    if (q !is Map<*, *>) return emptyMap()
    return q.entries.mapNotNull { (k, v) ->
        val id = k as? String ?: return@mapNotNull null
        val m = v as? Map<*, *> ?: return@mapNotNull null
        id to QuartierStatus(entryFromMap(m[Utility.POWER.key]), entryFromMap(m[Utility.WATER.key]))
    }.toMap()
}

/**
 * Stockage Firestore : un document par shard, `shards/{n}`, de la forme
 * { q: { <quartierId>: { p: Entry, w: Entry } } }.
 * Lecture de toute la ville = SHARD_COUNT lectures. Voir firebase/firestore.rules.
 */
class FirestoreRepository : StatusRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    override val isLive = true

    private fun ref(shard: Int) = db.collection("shards").document(shard.toString())

    override suspend fun fetchShard(shard: Int): Map<String, QuartierStatus> =
        parseShard(ref(shard).get().await().get("q"))

    override suspend fun report(q: Quartier, u: Utility, on: Boolean): Entry {
        if (auth.currentUser == null) auth.signInAnonymously().await()
        val ref = ref(q.shard)
        return db.runTransaction { tx ->
            val current = tx.get(ref).get("q") as? Map<*, *>
            val old = entryFromMap((current?.get(q.id) as? Map<*, *>)?.get(u.key))
            val updated = applyReport(old, on, System.currentTimeMillis())
            tx.set(ref, mapOf("q" to mapOf(q.id to mapOf(u.key to updated.toMap()))), SetOptions.merge())
            updated
        }.await()
    }
}

/** Mode démo (sans Firebase) : données locales simulées, utile pour tester l'appli. */
class DemoRepository : StatusRepository {
    override val isLive = false
    private val data = mutableMapOf<String, QuartierStatus>()

    init {
        val now = System.currentTimeMillis()
        val rnd = Random(237)
        QUARTIERS.forEach { q ->
            fun fake(offChance: Double): Entry? {
                if (rnd.nextDouble() < 0.25) return null
                val off = rnd.nextDouble() < offChance
                val since = now - rnd.nextLong(10 * MINUTE, 9 * HOUR)
                var e = applyReport(null, !off, now - DAY + rnd.nextLong(HOUR))
                e = applyReport(e, off, since - rnd.nextLong(HOUR, 5 * HOUR))
                e = applyReport(e, !off, since)
                return e.copy(last = now - rnd.nextLong(MINUTE, 50 * MINUTE), n = rnd.nextInt(1, 14))
            }
            data[q.id] = QuartierStatus(fake(0.35), fake(0.2))
        }
    }

    override suspend fun fetchShard(shard: Int): Map<String, QuartierStatus> = synchronized(data) {
        QUARTIERS.filter { it.shard == shard }.mapNotNull { q -> data[q.id]?.let { q.id to it } }.toMap()
    }

    override suspend fun report(q: Quartier, u: Utility, on: Boolean): Entry = synchronized(data) {
        val cur = data[q.id] ?: QuartierStatus(null, null)
        val updated = applyReport(cur.get(u), on, System.currentTimeMillis())
        data[q.id] = if (u == Utility.POWER) cur.copy(power = updated) else cur.copy(water = updated)
        updated
    }
}
