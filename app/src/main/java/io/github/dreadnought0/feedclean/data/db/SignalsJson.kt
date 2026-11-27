package io.github.dreadnought0.feedclean.data.db

import io.github.dreadnought0.feedclean.detect.Signals
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * JSON codec for `sightings.signals_json`.
 *
 * Unknown keys are ignored (rows written by a newer app version still load) and defaults are always encoded
 * (rows are self-describing for the full export).
 */
object SignalsJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(signals: Signals): String = json.encodeToString(Signals.serializer(), signals)

    /** Decodes [text]; a malformed or non-object value yields an empty [Signals] rather than a crash. */
    fun decode(text: String): Signals = try {
        json.decodeFromString(Signals.serializer(), text)
    } catch (_: SerializationException) {
        Signals()
    } catch (_: IllegalArgumentException) {
        Signals()
    }
}
