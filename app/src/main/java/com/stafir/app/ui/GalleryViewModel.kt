package com.stafir.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.work.WorkInfo
import com.stafir.app.StafirApp
import com.stafir.app.indexing.AnalysisWorker
import com.stafir.app.system.DeviceHelper
import com.stafir.app.system.Permissions
import com.stafir.core.db.KindStat
import com.stafir.core.db.MediaEntity
import com.stafir.core.db.StateStat
import com.stafir.core.search.SearchSqlBuilder
import com.stafir.domain.Concept
import com.stafir.domain.DateRange
import com.stafir.domain.ItalianQueryParser
import com.stafir.domain.MediaTypeFilter
import com.stafir.domain.PlaceFilter
import com.stafir.domain.PlaceLevel
import com.stafir.domain.SearchQuery
import com.stafir.domain.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Stato dell'analisi mostrato nella schermata principale. */
data class AnalysisUi(
    val total: Int = 0,
    val analyzed: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0,
    val running: Boolean = false,
    val waitingForCharge: Boolean = false,
    val tooHot: Boolean = false,
    val etaSeconds: Long = -1,
)

/** Cosa mostrare sotto la barra: risultati, frase capita, avvisi. */
data class SearchUi(
    val understood: List<String> = emptyList(),
    val notice: String? = null,
    val resultCount: Int = -1,
)

data class FilterOptions(
    val years: List<String> = emptyList(),
    val places: List<PlaceFilter> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val stafir = app as StafirApp
    private val container = stafir.container
    private val dao get() = container.database.mediaDao()
    val settings = container.settings

    val text = MutableStateFlow("")
    /** Filtri toccati sotto la barra. */
    val chips = MutableStateFlow(SearchQuery())

    private val _search = MutableStateFlow(SearchUi())
    val search: StateFlow<SearchUi> = _search.asStateFlow()

    private val _options = MutableStateFlow(FilterOptions())
    val options: StateFlow<FilterOptions> = _options.asStateFlow()

    private var parser: ItalianQueryParser = ItalianQueryParser()
    private var parserBuiltAt = 0L
    private var parserTick = -1L

    /** Il parser conosce i luoghi dell'archivio: lo ricostruiamo solo ogni tanto (sono query pesanti). */
    private suspend fun currentParser(): ItalianQueryParser {
        val now = System.currentTimeMillis()
        val tick = stafir.syncTick.value
        if (tick != parserTick || now - parserBuiltAt > 60_000) {
            parser = container.buildParser()
            parserBuiltAt = now
            parserTick = tick
        }
        return parser
    }

    /** Ultima ricerca per cui la griglia è stata riportata in cima (per non perdere la posizione). */
    var lastScrolledQuery: SearchQuery? = null
    /** Posizione dell'ultima foto vista nel visualizzatore, per ritrovarla nella griglia. */
    var lastViewedIndex: Int? = null

    private val effectiveQuery: Flow<SearchQuery> =
        combine(text.debounce(250), chips, stafir.syncTick) { t, c, _ -> t to c }
            .mapLatest { (t, c) -> resolve(t, c) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()

    val currentQuery: StateFlow<SearchQuery> =
        effectiveQuery.stateIn(viewModelScope, SharingStarted.Eagerly, SearchQuery())

    val paging: Flow<PagingData<MediaEntity>> = currentQuery
        .flatMapLatest { q ->
            val minConf = settings.current.minConfidence
            Pager(
                PagingConfig(pageSize = 150, initialLoadSize = 300, prefetchDistance = 150, enablePlaceholders = true, maxSize = 3000),
            ) { dao.pagingSource(SearchSqlBuilder.select(q, minConf)) }.flow
        }
        .cachedIn(viewModelScope)

    private suspend fun resolve(t: String, c: SearchQuery): SearchQuery {
        val parsed = currentParser().parse(t)
        var q = parsed.mergedWith(c)
        var notice: String? = parsed.command?.let {
            "Ho capito il comando «${it.verb}» (${it.description}). Arriverà in una prossima fase: intanto ti mostro i file a cui si applicherebbe."
        }
        val minConf = settings.current.minConfidence
        var count = dao.countRaw(SearchSqlBuilder.count(q, minConf))
        if (count == 0 && q.freeText.isNotEmpty()) {
            val words = q.freeText.joinToString(", ") { "«$it»" }
            q = q.copy(freeText = emptyList())
            count = dao.countRaw(SearchSqlBuilder.count(q, minConf))
            notice = "Non ho trovato $words. Se sono nomi di persone, il riconoscimento dei volti arriva nella prossima fase. " +
                "Ecco i risultati senza ${if (parsed.freeText.size > 1) "queste parole" else "questa parola"}."
        }
        _search.value = SearchUi(understood = parsed.explain(), notice = notice, resultCount = count)
        return q
    }

    // ---------- Filtri toccabili ----------

    fun toggleType(type: MediaTypeFilter) = chips.value.let {
        chips.value = it.copy(mediaType = if (it.mediaType == type) MediaTypeFilter.ANY else type)
    }

    fun toggleFavorites() = chips.value.let { chips.value = it.copy(favoritesOnly = !it.favoritesOnly) }

    fun toggleSource(s: Source) = chips.value.let {
        chips.value = it.copy(sources = if (s in it.sources) it.sources - s else it.sources + s)
    }

    fun toggleConcept(c: Concept) = chips.value.let {
        chips.value = it.copy(concepts = if (it.concepts.any { x -> x.id == c.id }) it.concepts.filterNot { x -> x.id == c.id } else it.concepts + c)
    }

    fun togglePlace(p: PlaceFilter) = chips.value.let {
        chips.value = it.copy(places = if (p in it.places) it.places - p else it.places + p)
    }

    fun setYear(year: String?) {
        val zone = ZoneId.systemDefault()
        val range = year?.toIntOrNull()?.let {
            DateRange(
                LocalDate.of(it, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
                LocalDate.of(it + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
                year,
            )
        }
        chips.value = chips.value.copy(dateRange = range)
    }

    fun clearAll() {
        text.value = ""
        chips.value = SearchQuery()
    }

    fun loadFilterOptions() {
        viewModelScope.launch(Dispatchers.IO) {
            val years = dao.years().map { it.value }.filter { it.length == 4 }
            val places = dao.countries().take(10).map { PlaceFilter(PlaceLevel.COUNTRY, it.value) } +
                dao.regions().take(15).map { PlaceFilter(PlaceLevel.REGION, it.value) } +
                dao.cities().take(30).map { PlaceFilter(PlaceLevel.CITY, it.value) }
            _options.value = FilterOptions(years, places)
        }
    }

    // ---------- Selezione multipla ----------

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()
    /** Serve per sapere se un id selezionato è un video (per costruire l'URI). */
    private val selectedKinds = HashMap<Long, Boolean>()

    fun toggleSelection(m: MediaEntity) {
        val s = _selection.value
        _selection.value = if (m.id in s) s - m.id else s + m.id
        selectedKinds[m.id] = m.isVideo
    }

    fun clearSelection() {
        _selection.value = emptySet()
        selectedKinds.clear()
    }

    fun selectedUris() = _selection.value.map {
        com.stafir.core.media.MediaStoreSync.contentUri(it, selectedKinds[it] ?: false)
    }

    // ---------- Stato analisi ----------

    /** Statistiche lette ogni pochi secondi (non a ogni scrittura, per non rallentare l'analisi). */
    private fun <T> polled(periodMs: Long, read: suspend () -> T): Flow<T> = flow {
        while (true) {
            emit(read())
            delay(periodMs)
        }
    }.flowOn(Dispatchers.IO)

    val kindStats: StateFlow<List<KindStat>> = stafir.syncTick
        .mapLatest { withContext(Dispatchers.IO) { dao.kindStats() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val stateStats: Flow<List<StateStat>> = polled(2_000) { dao.stateStats() }
    private val workInfo: Flow<WorkInfo?> = container.scheduler.analysisInfo

    val analysis: StateFlow<AnalysisUi> = combine(stateStats, workInfo, settings.values) { stats, info, s ->
        val analyzed = stats.firstOrNull { it.analysisState == 1 }?.count ?: 0
        val failed = stats.firstOrNull { it.analysisState == -1 }?.count ?: 0
        val pending = stats.firstOrNull { it.analysisState == 0 }?.count ?: 0
        val running = info?.state == WorkInfo.State.RUNNING
        val enqueued = info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.BLOCKED
        val progress = info?.progress
        val rate = settings.analysisRate
        val eta = progress?.getLong(AnalysisWorker.KEY_ETA, -1L)?.takeIf { it >= 0 }
            ?: if (rate > 0) (pending / rate).toLong() else -1L
        AnalysisUi(
            total = analyzed + failed + pending,
            analyzed = analyzed,
            failed = failed,
            pending = pending,
            running = running,
            waitingForCharge = enqueued && s.onlyWhileCharging && pending > 0,
            tooHot = progress?.getString(AnalysisWorker.KEY_STATE) == AnalysisWorker.PAUSED_HOT,
            etaSeconds = eta,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisUi())

    fun analyzeNow() = container.scheduler.scheduleAnalysis(now = true)
    fun stopAnalysis() = container.scheduler.stopAnalysis()

    /** L'analisi sembrava in corso ma si è fermata senza finire: probabilmente l'ha chiusa il sistema. */
    val killedBySystem: StateFlow<Boolean> = workInfo.map { info ->
        settings.analysisRunning &&
            info?.state != WorkInfo.State.RUNNING &&
            System.currentTimeMillis() - settings.analysisHeartbeat > 3 * 60_000
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun dismissKilledBanner() {
        settings.analysisRunning = false
    }

    val partialAccess: Boolean
        get() = Permissions.hasMediaAccess(getApplication()) && !Permissions.hasFullMediaAccess(getApplication())

    val needsBatterySetup: Boolean
        get() = DeviceHelper.isXiaomi &&
            (!DeviceHelper.isIgnoringBatteryOptimizations(getApplication()) || !settings.current.autostartConfirmed)

    fun refresh() = stafir.requestSync(0)

    suspend fun mediaById(id: Long): MediaEntity? = withContext(Dispatchers.IO) { dao.byId(id) }

    suspend fun labelsFor(id: Long) = withContext(Dispatchers.IO) { dao.labelsFor(id) }

    init {
        loadFilterOptions()
        stafir.syncTick.onEach { loadFilterOptions() }.launchIn(viewModelScope)
    }
}
