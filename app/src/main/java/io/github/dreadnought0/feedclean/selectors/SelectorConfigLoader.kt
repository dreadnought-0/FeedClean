package io.github.dreadnought0.feedclean.selectors

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.InputStream

/** The selector file could not be read at all (not JSON, wrong top-level shape). */
class SelectorConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Loads selector files. Pure JVM; see [loadBundledSelectors] for the Android asset helper.
 *
 * File shape:
 * ```
 * { "schema_version": 1, "package": "…", "verified_app_versions": ["…"],
 *   "locales": { "en": { "<key>": [ {strategy}, … ]  |  [ "label", … ] } } }
 * ```
 * Forward compatible and forgiving: unknown fields are ignored, keys starting with `_` are comments, a malformed
 * key or strategy is dropped with a warning (see [SelectorConfig.warnings]). `//` comments and trailing commas
 * are accepted so hand-edited override files load. Only a file that isn't JSON at all throws.
 */
object SelectorConfigLoader {
    /** Asset path of the bundled config. */
    const val ASSET_PATH = "selectors/tiktok.json"

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        allowComments = true
        allowTrailingComma = true
    }

    /** Parses a selector file from [input] (UTF-8). Closes the stream. */
    @Throws(SelectorConfigException::class)
    fun load(input: InputStream): SelectorConfig = input.bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }

    /** Parses a selector file from its JSON text. */
    @Throws(SelectorConfigException::class)
    fun parse(text: String): SelectorConfig {
        val file = try {
            json.decodeFromString(SelectorFileDto.serializer(), text)
        } catch (e: SerializationException) {
            throw SelectorConfigException("Selector file is not valid: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw SelectorConfigException("Selector file is not valid: ${e.message}", e)
        }
        val warnings = mutableListOf<String>()
        if (file.schemaVersion > SelectorConfig.SUPPORTED_SCHEMA_VERSION) {
            warnings += "schema_version ${file.schemaVersion} is newer than supported " +
                "${SelectorConfig.SUPPORTED_SCHEMA_VERSION}; unknown fields are ignored"
        }
        if (file.packageName.isBlank()) warnings += "package is missing; windows will not be filtered by package"
        val locales = file.locales.mapValues { (tag, obj) -> parseLocale(tag, obj, warnings) }
        if (locales.isEmpty()) warnings += "no locales defined; nothing will match"
        return SelectorConfig(file.schemaVersion, file.packageName, file.verifiedAppVersions, locales, warnings)
    }

    private fun parseLocale(tag: String, obj: JsonObject, warnings: MutableList<String>): LocaleSelectors {
        val strategies = linkedMapOf<String, List<SelectorStrategy>>()
        val labels = linkedMapOf<String, List<String>>()
        for ((key, element) in obj) {
            if (key.startsWith("_")) continue
            val array = element as? JsonArray
            if (array == null) {
                warnings += "$tag/$key: expected a list; ignored"
                continue
            }
            when {
                array.isEmpty() -> strategies[key] = emptyList()
                array.all { it is JsonPrimitive && it.isString } ->
                    labels[key] = array.map { (it as JsonPrimitive).content }
                else -> strategies[key] = array.mapIndexedNotNull { i, item -> decodeStrategy(tag, key, i, item, warnings) }
            }
        }
        return LocaleSelectors(tag, strategies, labels, warnings)
    }

    private fun decodeStrategy(
        tag: String,
        key: String,
        index: Int,
        item: JsonElement,
        warnings: MutableList<String>,
    ): SelectorStrategy? {
        if (item !is JsonObject) {
            warnings += "$tag/$key[$index]: expected an object; ignored"
            return null
        }
        return try {
            json.decodeFromJsonElement(SelectorStrategy.serializer(), item)
        } catch (e: SerializationException) {
            warnings += "$tag/$key[$index]: ${e.message?.lineSequence()?.first()}; ignored"
            null
        } catch (e: IllegalArgumentException) {
            warnings += "$tag/$key[$index]: ${e.message?.lineSequence()?.first()}; ignored"
            null
        }
    }

    @Serializable
    private data class SelectorFileDto(
        @SerialName("schema_version") val schemaVersion: Int = 0,
        @SerialName("package") val packageName: String = "",
        @SerialName("verified_app_versions") val verifiedAppVersions: List<String> = emptyList(),
        val locales: Map<String, JsonObject> = emptyMap(),
    )
}
