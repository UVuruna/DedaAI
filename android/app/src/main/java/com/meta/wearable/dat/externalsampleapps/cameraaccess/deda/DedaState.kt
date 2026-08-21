package com.meta.wearable.dat.externalsampleapps.cameraaccess.deda

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Deda's top-level mode. See PLAN.md, Phase 3.
 *
 *  OFF      — nothing listens, nothing costs anything.
 *  STANDBY  — the wake-word detector runs (Phase 3b); Gemini is NOT connected.
 *  TALKING  — a live Gemini session is open (Phase 3c).
 */
enum class DedaMode { OFF, STANDBY, TALKING }

object DedaState {
    private val _mode = MutableStateFlow(DedaMode.OFF)
    val mode: StateFlow<DedaMode> = _mode

    /**
     * Bumped on every entry into TALKING. Anything that fires on a delay —
     * a command's hand-over to a phone call, say — must carry the generation
     * it was scheduled in, or it will act on the NEXT conversation instead
     * (the same class of race DedaController's wakeGeneration guards).
     */
    @Volatile var talkGeneration: Int = 0
        private set

    fun set(mode: DedaMode) {
        if (mode == DedaMode.TALKING && _mode.value != DedaMode.TALKING) {
            talkGeneration++
        }
        _mode.value = mode
    }

    fun isOn(): Boolean = _mode.value != DedaMode.OFF
}
