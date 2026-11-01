package it.fast4x.opentv.data.remote

/**
 * Parser JSON minimo, scritto a mano nello stesso stile di [M3UParser] e
 * [XmltvParser]: il modulo core non ha dipendenze JSON (il modulo Android
 * avrebbe org.json integrato, il desktop JVM no) e l'unico consumo finora
 * era XML. Il "Rivedi" La7 introduce sorgenti JSON: payload flight di
 * Next.js (pagine SSR) e oggetti guidatv.
 *
 * È un parser completo (oggetti, array, stringhe con escape, numeri,
 * true/false/null) ma volutamente minimale: nessun binding, nessun
 * streaming, nessuna opzione. Profondità annidata limitata per difesa
 * (input ostili o payload corrotti non devono fare saltare lo stack).
 */
sealed interface JsonValue {

    /** Oggetto JSON: membri in ordine di dichiarazione, accesso per chiave. */
    data class Obj(val members: Map<String, JsonValue>) : JsonValue

    /** Array JSON: elementi in ordine. */
    data class Arr(val items: List<JsonValue>) : JsonValue

    /** Stringa JSON (escape già risolti). */
    data class Str(val value: String) : JsonValue

    /** Numero JSON conservato come Double (gli interi grossi servono come Long: [asLong]). */
    data class Num(val value: Double) : JsonValue

    /** Booleano JSON. */
    data class Bool(val value: Boolean) : JsonValue

    /** null JSON. */
    data object Null : JsonValue
}

/** Profondità massima di annidamento accettata (difesa da payload ostili). */
private const val MAX_JSON_DEPTH = 64

/**
 * Parso [text] come documento JSON. Solleva [IllegalArgumentException]
 * con un messaggio breve in caso di sintassi invalida: i chiamanti di
 * [MiniJson] lavorano sempre dentro runCatching.
 */
fun parseJson(text: String): JsonValue {
    val parser = MiniJsonParser(text)
    val value = parser.parseValue(0)
    parser.skipWhitespace()
    if (!parser.atEnd()) throw IllegalArgumentException("Contenuto inatteso dopo il valore JSON")
    return value
}

/**
 * Motore del parser: un indice su stringa, ricorsione discesiva. I numeri
 * vengono letti con toDoubleOrNull (niente eccezioni su input parziali).
 */
private class MiniJsonParser(private val text: String) {

    var pos = 0
        private set

    fun atEnd(): Boolean = pos >= text.length

    fun skipWhitespace() {
        while (pos < text.length && text[pos].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) pos++
    }

    fun parseValue(depth: Int): JsonValue {
        if (depth > MAX_JSON_DEPTH) throw IllegalArgumentException("JSON troppo annidato")
        skipWhitespace()
        if (atEnd()) throw IllegalArgumentException("JSON troncato")
        return when (val c = text[pos]) {
            '{' -> parseObject(depth)
            '[' -> parseArray(depth)
            '"' -> JsonValue.Str(parseString())
            't' -> parseLiteral("true", JsonValue.Bool(true))
            'f' -> parseLiteral("false", JsonValue.Bool(false))
            'n' -> parseLiteral("null", JsonValue.Null)
            else -> {
                if (c == '-' || c in '0'..'9') parseNumber() else
                    throw IllegalArgumentException("Carattere JSON inatteso: '$c'")
            }
        }
    }

    private fun parseObject(depth: Int): JsonValue {
        pos++ // '{'
        val members = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (!atEnd() && text[pos] == '}') {
            pos++
            return JsonValue.Obj(members)
        }
        while (true) {
            skipWhitespace()
            if (atEnd() || text[pos] != '"') throw IllegalArgumentException("Chiave oggetto attesa")
            val key = parseString()
            skipWhitespace()
            if (atEnd() || text[pos] != ':') throw IllegalArgumentException("':' atteso dopo la chiave")
            pos++
            members[key] = parseValue(depth + 1)
            skipWhitespace()
            when {
                atEnd() -> throw IllegalArgumentException("Oggetto JSON troncato")
                text[pos] == ',' -> pos++
                text[pos] == '}' -> {
                    pos++
                    return JsonValue.Obj(members)
                }

                else -> throw IllegalArgumentException("',' o '}' attesi nell'oggetto")
            }
        }
    }

    private fun parseArray(depth: Int): JsonValue {
        pos++ // '['
        val items = ArrayList<JsonValue>()
        skipWhitespace()
        if (!atEnd() && text[pos] == ']') {
            pos++
            return JsonValue.Arr(items)
        }
        while (true) {
            items += parseValue(depth + 1)
            skipWhitespace()
            when {
                atEnd() -> throw IllegalArgumentException("Array JSON troncato")
                text[pos] == ',' -> pos++
                text[pos] == ']' -> {
                    pos++
                    return JsonValue.Arr(items)
                }

                else -> throw IllegalArgumentException("',' o ']' attesi nell'array")
            }
        }
    }

    /**
     * Legge una stringa JSON partendo dal '"' di apertura; risolve gli
     * escape standard (\" \\ \/ \b \f \n \r \t \uXXXX). Una sequenza
     * \u surrogate è gestita da appendCodePoint con le coppie valide;
     * le sequenze surrogate orfane restano come caratteri (tollerante,
     * sono metadati di guide TV).
     */
    fun parseString(): String {
        pos++ // '"'
        val sb = StringBuilder()
        while (true) {
            if (atEnd()) throw IllegalArgumentException("Stringa JSON troncata")
            when (val c = text[pos]) {
                '"' -> {
                    pos++
                    return sb.toString()
                }

                '\\' -> {
                    pos++
                    if (atEnd()) throw IllegalArgumentException("Escape JSON troncato")
                    when (val e = text[pos]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 >= text.length) throw IllegalArgumentException("Escape \\u troncato")
                            val hex = text.substring(pos + 1, pos + 5)
                            val code = hex.toIntOrNull(16)
                                ?: throw IllegalArgumentException("Escape \\u invalido: $hex")
                            sb.appendCodePoint(code)
                            pos += 4
                        }

                        else -> throw IllegalArgumentException("Escape JSON sconosciuto: \\$e")
                    }
                    pos++
                }

                else -> {
                    sb.append(c)
                    pos++
                }
            }
        }
    }

    private fun parseNumber(): JsonValue {
        val start = pos
        if (!atEnd() && text[pos] == '-') pos++
        while (!atEnd() && (text[pos] in '0'..'9' || text[pos] == '.' ||
                text[pos] == 'e' || text[pos] == 'E' || text[pos] == '+' || text[pos] == '-')
        ) pos++
        val raw = text.substring(start, pos)
        val value = raw.toDoubleOrNull() ?: throw IllegalArgumentException("Numero JSON invalido: $raw")
        return JsonValue.Num(value)
    }

    private fun parseLiteral(literal: String, value: JsonValue): JsonValue {
        if (!text.startsWith(literal, pos)) throw IllegalArgumentException("Letterale JSON invalido")
        pos += literal.length
        return value
    }
}

// ---------------------------------------------------------------------------
// Accessori di lettura: gli usi tipici del "Rivedi" sono navigazioni brevi
// (obj -> array -> obj) con campi stringa/numerici; questi helper rendono il
// codice dei chiamanti piatto e null-safe.
// ---------------------------------------------------------------------------

/** Membri se [this] è un oggetto, altrimenti null. */
fun JsonValue?.asObj(): Map<String, JsonValue>? = (this as? JsonValue.Obj)?.members

/** Elementi se [this] è un array, altrimenti null. */
fun JsonValue?.asArr(): List<JsonValue>? = (this as? JsonValue.Arr)?.items

/** Testo se [this] è una stringa, altrimenti null. */
fun JsonValue?.asString(): String? = (this as? JsonValue.Str)?.value

/** Booleano se [this] è bool, altrimenti null. */
fun JsonValue?.asBool(): Boolean? = (this as? JsonValue.Bool)?.value

/**
 * Valore numerico come Long: accetta sia i numeri interi (la guida scrive
 * "timestamp":"1792908000" come stringa, ma altri campi arrivano numerici)
 * sia stringhe numeriche, restituendo null in caso contrario.
 */
fun JsonValue?.asLong(): Long? = when (this) {
    is JsonValue.Num -> value.toLong()
    is JsonValue.Str -> value.trim().toLongOrNull()
    else -> null
}

/** Membro di un oggetto per chiave (null se l'oggetto manca o non contiene la chiave). */
operator fun JsonValue?.get(key: String): JsonValue? = this?.asObj()?.get(key)
