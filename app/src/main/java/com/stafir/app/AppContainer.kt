package com.stafir.app

import android.content.Context
import com.stafir.app.indexing.IndexScheduler
import com.stafir.app.quality.QualityStore
import com.stafir.core.db.StafirDatabase
import com.stafir.core.geo.ItalianNames
import com.stafir.core.geo.OfflineGeocoder
import com.stafir.core.media.MediaStoreSync
import com.stafir.domain.ItalianQueryParser
import com.stafir.domain.PlaceDirectory
import com.stafir.domain.PlaceFilter
import com.stafir.domain.PlaceLevel

/** Contenitore delle dipendenze (semplice, senza librerie di iniezione). */
class AppContainer(val context: Context) {
    val settings = AppSettings(context)
    val database: StafirDatabase by lazy { StafirDatabase.create(context) }
    val mediaSync: MediaStoreSync by lazy { MediaStoreSync(context, database) }
    val geocoder: OfflineGeocoder by lazy { OfflineGeocoder(context) }
    val scheduler: IndexScheduler by lazy { IndexScheduler(context, settings) }
    val quality: QualityStore by lazy { QualityStore(context) }

    /** Costruisce il parser con i luoghi presenti nell'archivio. */
    suspend fun buildParser(): ItalianQueryParser {
        val dao = database.mediaDao()
        val places = dao.countries().map { PlaceFilter(PlaceLevel.COUNTRY, it.value) } +
            dao.regions().map { PlaceFilter(PlaceLevel.REGION, it.value) } +
            dao.cities().map { PlaceFilter(PlaceLevel.CITY, it.value) }
        val known = places.associateBy { it.value }
        val aliases = ItalianNames.searchAliases.mapNotNull { (alias, italian) ->
            known[italian]?.let { alias to it }
        }.toMap()
        return ItalianQueryParser(PlaceDirectory.of(places, aliases))
    }
}
