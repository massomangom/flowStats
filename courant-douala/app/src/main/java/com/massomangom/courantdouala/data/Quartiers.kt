package com.massomangom.courantdouala.data

/**
 * Quartiers de Douala suivis par l'appli.
 *
 * `shard` répartit les quartiers sur [SHARD_COUNT] documents Firestore : l'écran
 * "Douala" lit toute la ville en SHARD_COUNT lectures seulement (quota gratuit).
 * Pour ajouter un quartier : nouvel id unique (minuscules, sans espace), n'importe
 * quel shard. Ne jamais renommer ni supprimer un id existant (données perdues).
 */
data class Quartier(val id: String, val name: String, val shard: Int)

const val SHARD_COUNT = 4

private val NAMES = listOf(
    "akwa" to "Akwa",
    "akwanord" to "Akwa Nord",
    "angeraphael" to "Ange Raphaël",
    "bali" to "Bali",
    "bassa" to "Bassa",
    "beedi" to "Beedi",
    "bepanda" to "Bépanda",
    "bessengue" to "Bessengue",
    "bilongue" to "Bilonguè",
    "bojongo" to "Bojongo",
    "bonaberi" to "Bonabéri",
    "bonadibong" to "Bonadibong",
    "bonamoussadi" to "Bonamoussadi",
    "bonanjo" to "Bonanjo",
    "bonapriso" to "Bonapriso",
    "bonassama" to "Bonassama",
    "bonendale" to "Bonendalé",
    "brazzaville" to "Brazzaville",
    "citecicam" to "Cité Cicam",
    "citepalmiers" to "Cité des Palmiers",
    "citesic" to "Cité SIC",
    "dakar" to "Dakar",
    "deido" to "Deïdo",
    "denver" to "Denver",
    "grandhangar" to "Grand Hangar",
    "japoma" to "Japoma",
    "kotto" to "Kotto",
    "lendi" to "Lendi",
    "logbaba" to "Logbaba",
    "logbessou" to "Logbessou",
    "logpom" to "Logpom",
    "madagascar" to "Madagascar",
    "makepe" to "Makepe",
    "mambanda" to "Mambanda",
    "mboppi" to "Mboppi",
    "ndobo" to "Ndobo",
    "ndogbong" to "Ndogbong",
    "ndogpassi" to "Ndogpassi",
    "ndogsimbi" to "Ndogsimbi",
    "ndokoti" to "Ndokoti",
    "newbell" to "New-Bell",
    "ngodi" to "Ngodi",
    "nkololoun" to "Nkololoun",
    "nkongmondo" to "Nkongmondo",
    "nyalla" to "Nyalla",
    "oyack" to "Oyack",
    "pk8" to "PK8",
    "pk10" to "PK10",
    "pk12" to "PK12",
    "pk14" to "PK14",
    "sodiko" to "Sodiko",
    "village" to "Village",
    "yassa" to "Yassa",
    "youpwe" to "Youpwé",
)

val QUARTIERS: List<Quartier> = NAMES.mapIndexed { i, (id, name) -> Quartier(id, name, i % SHARD_COUNT) }

private val BY_ID = QUARTIERS.associateBy { it.id }

fun quartierById(id: String?): Quartier? = id?.let { BY_ID[it] }
