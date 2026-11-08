package com.stafir.core.geo

import android.content.Context
import android.util.Log
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

data class Place(val city: String?, val region: String?, val country: String?, val countryCode: String?)

/**
 * Trova città, regione e paese a partire dalle coordinate GPS SENZA internet,
 * usando l'elenco GeoNames delle città (incluso nell'app, ~30.000 città).
 */
class OfflineGeocoder(private val context: Context) {

    private class Data(
        val lat: FloatArray,
        val lon: FloatArray,
        val name: Array<String>,
        val cc: Array<String>,
        val admin: Array<String>,
        val grid: HashMap<Int, IntArray>,
    )

    @Volatile private var data: Data? = null
    @Volatile private var loadFailed = false

    val isAvailable: Boolean get() = ensureLoaded() != null

    private fun cellKey(lat: Double, lon: Double): Int {
        val a = floor(lat).toInt() + 90
        val b = floor(lon).toInt() + 180
        return a * 400 + b
    }

    @Synchronized
    private fun ensureLoaded(): Data? {
        data?.let { return it }
        if (loadFailed) return null
        return try {
            val lat = ArrayList<Float>(40000); val lon = ArrayList<Float>(40000)
            val name = ArrayList<String>(40000); val cc = ArrayList<String>(40000); val admin = ArrayList<String>(40000)
            context.assets.open(ASSET).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('\t')
                    if (p.size >= 5) {
                        name += p[0]; lat += p[1].toFloat(); lon += p[2].toFloat(); cc += p[3]; admin += p[4]
                    }
                }
            }
            val buckets = HashMap<Int, MutableList<Int>>()
            for (i in lat.indices) buckets.getOrPut(cellKey(lat[i].toDouble(), lon[i].toDouble())) { mutableListOf() } += i
            Data(
                lat.toFloatArray(), lon.toFloatArray(), name.toTypedArray(), cc.toTypedArray(), admin.toTypedArray(),
                HashMap(buckets.mapValues { it.value.toIntArray() }),
            ).also { data = it }
        } catch (e: Exception) {
            Log.w("OfflineGeocoder", "Dati geografici non disponibili: ${e.message}")
            loadFailed = true
            null
        }
    }

    fun lookup(latitude: Double, longitude: Double): Place? {
        if (latitude == 0.0 && longitude == 0.0) return null
        val d = ensureLoaded() ?: return null
        var best = -1
        var bestDist = Double.MAX_VALUE
        val cosLat = cos(Math.toRadians(latitude))
        for (radius in 1..3) {
            for (da in -radius..radius) for (db in -radius..radius) {
                if (radius > 1 && kotlin.math.abs(da) < radius && kotlin.math.abs(db) < radius) continue
                val bucket = d.grid[cellKey(latitude + da, longitude + db)] ?: continue
                for (i in bucket) {
                    val dy = d.lat[i] - latitude
                    val dx = (d.lon[i] - longitude) * cosLat
                    val dist = dx * dx + dy * dy
                    if (dist < bestDist) { bestDist = dist; best = i }
                }
            }
            if (best >= 0 && radius >= 1 && sqrt(bestDist) * 111.0 < radius * 80.0) break
        }
        if (best < 0) return null
        val km = sqrt(bestDist) * 111.0
        val countryCode = d.cc[best]
        val country = Locale("", countryCode).getDisplayCountry(Locale.ITALIAN).takeIf { it.isNotBlank() && it != countryCode }
        val region = d.admin[best].takeIf { it.isNotBlank() }?.let { ItalianNames.region(it) }
        // Oltre 40 km dalla città più vicina mostriamo solo regione e paese.
        val city = if (km <= 40.0) ItalianNames.city(d.name[best]) else null
        return Place(city, region, country, countryCode)
    }

    companion object {
        const val ASSET = "geo/cities.tsv"
    }
}

/** Nomi italiani per luoghi che GeoNames riporta in inglese. */
object ItalianNames {
    private val regions = mapOf(
        "Lombardy" to "Lombardia", "Piedmont" to "Piemonte", "Apulia" to "Puglia", "Sardinia" to "Sardegna",
        "Sicily" to "Sicilia", "Tuscany" to "Toscana", "Aosta Valley" to "Valle d'Aosta",
        "Trentino-Alto Adige" to "Trentino-Alto Adige", "Friuli Venezia Giulia" to "Friuli-Venezia Giulia",
        "Emilia-Romagna" to "Emilia-Romagna", "Latium" to "Lazio", "The Marches" to "Marche",
        "Bavaria" to "Baviera", "Catalonia" to "Catalogna", "Andalusia" to "Andalusia", "Brittany" to "Bretagna",
        "Normandy" to "Normandia", "Provence-Alpes-Côte d'Azur" to "Provenza-Alpi-Costa Azzurra",
        "Corsica" to "Corsica", "Canary Islands" to "Canarie", "Balearic Islands" to "Baleari",
        "Crete" to "Creta", "England" to "Inghilterra", "Scotland" to "Scozia", "Wales" to "Galles",
        "Ile-de-France" to "Île-de-France", "Île-de-France" to "Île-de-France",
    )
    private val cities = mapOf(
        "Rome" to "Roma", "Milan" to "Milano", "Naples" to "Napoli", "Turin" to "Torino", "Florence" to "Firenze",
        "Venice" to "Venezia", "Genoa" to "Genova", "Padua" to "Padova", "Syracuse" to "Siracusa", "Mantua" to "Mantova",
        "Leghorn" to "Livorno", "London" to "Londra", "Paris" to "Parigi", "Munich" to "Monaco di Baviera",
        "Lisbon" to "Lisbona", "Athens" to "Atene", "Barcelona" to "Barcellona", "Seville" to "Siviglia",
        "Brussels" to "Bruxelles", "Geneva" to "Ginevra", "Zurich" to "Zurigo", "Moscow" to "Mosca",
        "New York City" to "New York", "Berlin" to "Berlino", "Prague" to "Praga", "Warsaw" to "Varsavia",
        "Copenhagen" to "Copenaghen", "Stockholm" to "Stoccolma", "Edinburgh" to "Edimburgo", "Marseille" to "Marsiglia",
        "Nice" to "Nizza", "Cologne" to "Colonia", "Hamburg" to "Amburgo", "Beijing" to "Pechino", "Cairo" to "Il Cairo",
        "Vienna" to "Vienna", "Lyon" to "Lione", "Bern" to "Berna", "Lucerne" to "Lucerna", "Dubrovnik" to "Ragusa (Dubrovnik)",
        "Split" to "Spalato", "Ljubljana" to "Lubiana", "Nuremberg" to "Norimberga", "The Hague" to "L'Aia",
        "Bucharest" to "Bucarest", "Belgrade" to "Belgrado", "Istanbul" to "Istanbul", "Saint Petersburg" to "San Pietroburgo",
    )

    fun region(name: String): String = regions[name] ?: name
    fun city(name: String): String = cities[name] ?: name

    /** Alias per la ricerca: anche il nome inglese porta al luogo italiano. */
    val searchAliases: Map<String, String> = cities.entries.associate { it.key to it.value } +
        regions.entries.associate { it.key to it.value }
}
