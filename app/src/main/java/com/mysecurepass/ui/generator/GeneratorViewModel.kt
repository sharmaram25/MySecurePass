package com.mysecurepass.ui.generator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysecurepass.AppContainer
import com.mysecurepass.core.generator.Generated
import com.mysecurepass.core.generator.GeneratorSettings
import com.mysecurepass.core.generator.Preset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GeneratorViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<GeneratorSettings> = c.settings.generator

    private val _current = MutableStateFlow<Generated?>(null)
    val current: StateFlow<Generated?> = _current.asStateFlow()

    /** Increments on each manual regenerate, drives the refresh icon rotation. */
    private val _spins = MutableStateFlow(0)
    val spins: StateFlow<Int> = _spins.asStateFlow()

    private var job: Job? = null

    init { regenerate() }

    fun update(transform: (GeneratorSettings) -> GeneratorSettings) {
        val next = transform(settings.value)
        if (next == settings.value) return
        c.settings.updateGenerator(next)
        regenerate()
    }

    fun applyPreset(preset: Preset) = update(preset.settings)

    fun spin() { _spins.value++; regenerate() }

    /** Generation runs off the main thread; a newer request cancels an older one (smooth slider drags). */
    fun regenerate() {
        job?.cancel()
        val snapshot = settings.value
        job = viewModelScope.launch(Dispatchers.Default) {
            _current.value = c.generator.generate(snapshot)
        }
    }

    fun copy(): Int {
        val g = _current.value ?: return 0
        val seconds = c.settings.settings.value.clipboardClearSeconds
        c.clipboard.copy(g.value, seconds)
        c.history.add(g.value, g.entropyBits)
        return seconds
    }

    fun handOffToVault() {
        _current.value?.let { c.draftPassword = it.value }
    }

    val systemShowsCopyToast get() = c.clipboard.systemShowsConfirmation
    val history get() = c.history
    fun copyHistory(value: String) = c.clipboard.copy(value, c.settings.settings.value.clipboardClearSeconds)
}
