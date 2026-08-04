package com.lhacenmed.khatmah.feature.quran.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahEntity
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahRepository
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahSessionEntity
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.data.MushafPrint
import com.lhacenmed.khatmah.feature.quran.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.db.MushafDb
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderMeta
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for the Quran tab: where reading stopped ([resume] — the hero card) and the active
 * khatmah ([khatmah] — the bottom strip). The two are independent: the tab reads the mushaf
 * freely, the khatmah wird is a side track reached from the strip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuranHomeViewModel(
    private val context: Context,
    private val repo:    KhatmahRepository,
) : ViewModel() {

    /** The last-read position, resolved for display. Defaults to al-Fatiha before the first read. */
    data class Resume(
        val suraName: String,
        val page:     Int,
        val juz:      Int,
        val ayaText:  String,
        val riwaya:   Riwaya,
    )

    sealed class KhatmahState {
        object Loading : KhatmahState()
        object None    : KhatmahState()
        data class Done(val totalDays: Int) : KhatmahState()
        data class Active(
            val session:   KhatmahSessionEntity,
            val khatmah:   KhatmahEntity,
            val juz:       Int,
            val readCount: Int,
        ) : KhatmahState()
    }

    private val _khatmah = MutableStateFlow<KhatmahState>(KhatmahState.Loading)
    val khatmah: StateFlow<KhatmahState> = _khatmah.asStateFlow()

    /**
     * Re-resolved whenever the print changes or the reader saves a page, so coming back from the
     * reader already shows the new position — no refresh pass on return.
     */
    val resume: StateFlow<Resume?> =
        combine(MushafPrefs.selected, ReaderProgress.lastSaved) { print, _ -> print }
            .mapLatest { loadResume(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Flipped to true by [markSplashReady] once Compose has committed a frame with real content.
     * Read by MainActivity's `setKeepOnScreenCondition`. Volatile so the main-thread condition
     * check always sees the latest value.
     */
    @Volatile var splashReady: Boolean = false
        private set

    init {
        viewModelScope.launch {
            repo.activeKhatmahFlow()
                .flatMapLatest { khatmah ->
                    if (khatmah == null) return@flatMapLatest flowOf(KhatmahState.None)
                    combine(
                        repo.currentSession(khatmah.id),
                        repo.readCount(khatmah.id),
                    ) { session, readCount ->
                        if (session == null) {
                            KhatmahState.Done(totalDays = khatmah.totalDays)
                        } else {
                            KhatmahState.Active(
                                session   = session,
                                khatmah   = khatmah,
                                juz       = repo.sessionJuz(
                                    startSura = session.startSura,
                                    startAya  = session.startAya,
                                    riwayaKey = khatmah.riwaya,
                                ),
                                readCount = readCount,
                            )
                        }
                    }
                }
                .collect { _khatmah.value = it }
        }
    }

    /**
     * Called from a `SideEffect` in the tab once both the resume card and the khatmah strip have
     * real content to draw. Safe to call multiple times.
     */
    fun markSplashReady() {
        if (!splashReady) splashReady = true
    }

    /**
     * Resolves the stored anchor of [print] into displayable sura / page / juz / aya text. The
     * mushaf lookups are best-effort: the card must still resolve (and release the splash) on a
     * DB that isn't seeded yet, just without the sura name and verse.
     */
    private suspend fun loadResume(print: MushafPrint): Resume = withContext(Dispatchers.IO) {
        val anchor = ReaderProgress.read(context, print.id)
        val sura   = anchor?.sura ?: 1
        val aya    = anchor?.aya ?: 1
        // (sura name, verse text) — the two mushaf lookups the card needs.
        val meta   = runCatching {
            val dao = MushafDb.get(context).dao()
            dao.surahName(print.riwaya.dbKey, sura).orEmpty() to
                    dao.verse(print.riwaya.dbKey, sura, aya)?.text.orEmpty()
        }.getOrDefault("" to "")
        Resume(
            suraName = meta.first.removePrefix("سورة ").trim(),
            page     = anchor?.page ?: 1,
            juz      = ReaderMeta.juzForVerse(sura, aya),
            ayaText  = meta.second,
            riwaya   = print.riwaya,
        )
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QuranHomeViewModel(context, KhatmahRepository(context)) as T
    }
}
