package io.github.dreadnought0.feedclean.audit

import java.io.Reader
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The data export is not valid JSON (or nests deeper than the scanner allows). */
class ExportParseException(message: String) : Exception(message)

/**
 * Streaming JSON walker for TikTok's "Download your data" file, which can be tens of megabytes (watch
 * history). It reads the document once and only builds a [JsonElement] for the values of object members whose
 * key matches [interestingKey]; everything else is skipped without being kept in memory. Pure Kotlin.
 *
 * @param maxDepth nesting limit (protects the stack against hostile files).
 */
internal class JsonStreamScanner(
    private val reader: Reader,
    private val interestingKey: (String) -> Boolean,
    private val maxDepth: Int = 256,
) {
    private val buffer = CharArray(16 * 1024)
    private var pos = 0
    private var limit = 0

    /**
     * Walks the whole document, calling [onValue] for each interesting member (key, materialized value).
     * Interesting members nested inside an already materialized value are not reported separately.
     *
     * @throws ExportParseException on malformed JSON.
     */
    fun scan(onValue: (key: String, value: JsonElement) -> Unit) {
        skipWhitespace()
        walk(0, onValue)
        skipWhitespace()
        if (peek() != EOF) fail("trailing content")
    }

    // ── walking (skip mode) ──────────────────────────────────────────────────

    private fun walk(depth: Int, onValue: (String, JsonElement) -> Unit) {
        if (depth > maxDepth) fail("nested too deeply")
        when (peek()) {
            '{'.code -> walkObject(depth, onValue)
            '['.code -> walkArray(depth, onValue)
            else -> skipPrimitive()
        }
    }

    private fun walkObject(depth: Int, onValue: (String, JsonElement) -> Unit) {
        expect('{')
        skipWhitespace()
        if (peek() == '}'.code) {
            read()
            return
        }
        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            if (interestingKey(key)) onValue(key, readValue(depth + 1)) else walk(depth + 1, onValue)
            skipWhitespace()
            when (read()) {
                ','.code -> continue
                '}'.code -> return
                else -> fail("expected , or }")
            }
        }
    }

    private fun walkArray(depth: Int, onValue: (String, JsonElement) -> Unit) {
        expect('[')
        skipWhitespace()
        if (peek() == ']'.code) {
            read()
            return
        }
        while (true) {
            skipWhitespace()
            walk(depth + 1, onValue)
            skipWhitespace()
            when (read()) {
                ','.code -> continue
                ']'.code -> return
                else -> fail("expected , or ]")
            }
        }
    }

    private fun skipPrimitive() {
        when (peek()) {
            '"'.code -> skipString()
            EOF -> fail("unexpected end")
            else -> readLiteral()
        }
    }

    private fun skipString() {
        expect('"')
        while (true) {
            when (val c = read()) {
                EOF -> fail("unterminated string")
                '"'.code -> return
                '\\'.code -> if (read() == EOF) fail("unterminated string")
                else -> if (c < 0x20) fail("control character in string")
            }
        }
    }

    // ── materializing ────────────────────────────────────────────────────────

    private fun readValue(depth: Int): JsonElement {
        if (depth > maxDepth) fail("nested too deeply")
        return when (peek()) {
            '{'.code -> readObject(depth)
            '['.code -> readArray(depth)
            '"'.code -> JsonPrimitive(readString())
            EOF -> fail("unexpected end")
            else -> literal(readLiteral())
        }
    }

    private fun readObject(depth: Int): JsonObject {
        expect('{')
        val members = LinkedHashMap<String, JsonElement>()
        skipWhitespace()
        if (peek() == '}'.code) {
            read()
            return JsonObject(members)
        }
        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            members[key] = readValue(depth + 1)
            skipWhitespace()
            when (read()) {
                ','.code -> continue
                '}'.code -> return JsonObject(members)
                else -> fail("expected , or }")
            }
        }
    }

    private fun readArray(depth: Int): JsonArray {
        expect('[')
        val items = ArrayList<JsonElement>()
        skipWhitespace()
        if (peek() == ']'.code) {
            read()
            return JsonArray(items)
        }
        while (true) {
            skipWhitespace()
            items += readValue(depth + 1)
            skipWhitespace()
            when (read()) {
                ','.code -> continue
                ']'.code -> return JsonArray(items)
                else -> fail("expected , or ]")
            }
        }
    }

    private fun readString(): String {
        expect('"')
        val out = StringBuilder()
        while (true) {
            val c = read()
            when {
                c == EOF -> fail("unterminated string")
                c == '"'.code -> return out.toString()
                c == '\\'.code -> out.append(readEscape())
                c < 0x20 -> fail("control character in string")
                else -> out.append(c.toChar())
            }
        }
    }

    private fun readEscape(): Char = when (val c = read()) {
        '"'.code -> '"'
        '\\'.code -> '\\'
        '/'.code -> '/'
        'b'.code -> '\b'
        'f'.code -> '\u000C'
        'n'.code -> '\n'
        'r'.code -> '\r'
        't'.code -> '\t'
        'u'.code -> {
            var value = 0
            repeat(4) {
                val digit = Character.digit(read(), 16)
                if (digit < 0) fail("bad \\u escape")
                value = value * 16 + digit
            }
            value.toChar()
        }
        else -> fail("bad escape ${if (c == EOF) "at end" else c.toChar()}")
    }

    /** Reads a number or `true`/`false`/`null` token. */
    private fun readLiteral(): String {
        val out = StringBuilder()
        while (true) {
            val c = peek()
            if (c == EOF || c.toChar().let { it.isWhitespace() || it == ',' || it == ']' || it == '}' || it == ':' }) break
            out.append(read().toChar())
            if (out.length > 64) fail("literal too long")
        }
        if (out.isEmpty()) fail("expected a value")
        val text = out.toString()
        if (text != "true" && text != "false" && text != "null" && !NUMBER.matches(text)) fail("bad literal")
        return text
    }

    private fun literal(text: String): JsonElement = when (text) {
        "true" -> JsonPrimitive(true)
        "false" -> JsonPrimitive(false)
        "null" -> JsonNull
        else -> text.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(text.toDouble())
    }

    // ── characters ───────────────────────────────────────────────────────────

    private fun skipWhitespace() {
        while (true) {
            val c = peek()
            if (c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code || c == BOM) read() else return
        }
    }

    private fun expect(char: Char) {
        if (read() != char.code) fail("expected $char")
    }

    private fun peek(): Int {
        if (pos >= limit && !fill()) return EOF
        return buffer[pos].code
    }

    private fun read(): Int {
        if (pos >= limit && !fill()) return EOF
        return buffer[pos++].code
    }

    private fun fill(): Boolean {
        limit = reader.read(buffer, 0, buffer.size)
        pos = 0
        if (limit <= 0) {
            limit = 0
            return false
        }
        return true
    }

    private fun fail(message: String): Nothing = throw ExportParseException(message)

    private companion object {
        const val EOF = -1
        const val BOM = 0xFEFF
        val NUMBER = Regex("""-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?""")
    }
}
