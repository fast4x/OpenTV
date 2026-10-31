package it.fast4x.opentv.data.remote

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Preparazione TESTUALE della guida XMLTV (fix34): le guide di terze parti
 * non sono sempre XML UTF-8 pulito e il parser a pull (kxml2/KxmlParser) è
 * rigoroso — basta un'entità HTML non definita (`&nbsp;`, `&egrave;`) o una
 * dichiarazione di encoding diversa da quella usata per leggere lo stream
 * per chiudere l'intero caricamento con XmlPullParserException. Qui si
 * normalizzano i casi reali prima che il parser veda il testo:
 *
 *  - BOM UTF-8/UTF-16 rimosso a livello byte (alcuni parser lo scambiano per
 *    contenuto prima della dichiarazione);
 *  - encoding preso dalla dichiarazione XML (`encoding="iso-8859-1"` ecc.),
 *    non più UTF-8 forzato: con UTF-16 il testo forzato non è "brutto", è
 *    semplicemente illeggibile per il parser;
 *  - entità HTML nominate convertite in entità numeriche equivalenti (le
 *    numeriche sono XML valido e passano sempre) e le sconosciute eliminate:
 *    perde il singolo carattere, non tutta la guida.
 */
object XmltvText {

    /** Byte massimi ispezionati per trovare il BOM e la dichiarazione XML. */
    private const val HEAD_BYTES = 512

    private val ENCODING_REGEX = Regex(
        // Stringa con escape (non raw """): il contenuto termina con un
        // apice e un letterale raw che finisce con '"' confonde lo strip
        // degli script di verifica (falso sbilanciamento di parentesi).
        pattern = "encoding\\s*=\\s*[\"']([A-Za-z0-9._\\-]+)[\"']",
        options = setOf(RegexOption.IGNORE_CASE),
    )

    /**
     * Avvolge lo stream (già decompresso da PlaylistFetcher.autoDecompress)
     * nel Reader corretto: BOM e `encoding="..."` della dichiarazione
     * determinano il charset; in assenza di indizi vale UTF-8, il default
     * dello standard XML.
     */
    fun readerFor(input: InputStream): Reader {
        val pushback = PushbackInputStream(input, HEAD_BYTES)
        val head = readHead(pushback)

        var charset = StandardCharsets.UTF_8
        var bomBytes = 0

        when {
            head.size >= 3 && head[0] == 0xEF.toByte() &&
                head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte() -> {
                charset = StandardCharsets.UTF_8
                bomBytes = 3
            }

            head.size >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte() -> {
                charset = StandardCharsets.UTF_16LE
                bomBytes = 2
            }

            head.size >= 2 && head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte() -> {
                charset = StandardCharsets.UTF_16BE
                bomBytes = 2
            }

            else -> {
                // La dichiarazione va letta byte-per-byte (il file potrebbe
                // non essere UTF-8, quindi non decodifichiamo la testa col
                // charset finale ma con ISO-8859-1, che non altera i byte).
                val headText = String(head, Charsets.ISO_8859_1)
                val declared = ENCODING_REGEX.find(headText)?.groupValues?.get(1)
                if (declared != null) {
                    charset = runCatching { Charset.forName(declared) }
                        .getOrDefault(StandardCharsets.UTF_8)
                }
            }
        }

        if (bomBytes > 0) {
            // Riportiamo sullo stream solo i byte DOPO il BOM.
            pushback.unread(head, bomBytes, head.size - bomBytes)
        } else {
            pushback.unread(head)
        }
        return HtmlEntitySanitizingReader(InputStreamReader(pushback, charset))
    }

    /** Legge la testa dello stream (fino a [HEAD_BYTES] byte, loop completo). */
    private fun readHead(input: PushbackInputStream): ByteArray {
        val buffer = ByteArray(HEAD_BYTES)
        var total = 0
        while (total < HEAD_BYTES) {
            val n = input.read(buffer, total, HEAD_BYTES - total)
            if (n < 0) break
            total += n
        }
        return buffer.copyOf(total)
    }
}

/**
 * Reader filtro che neutralizza le entità HTML non-XML in STREAMING (fix34).
 *
 * kxml2 accetta le 5 entità predefinite XML (`&amp;` `&lt;` `&gt;` `&quot;`
 * `&apos;`) e quelle numeriche (`&#160;`/`&#xA0;`), ma su un'entità nominata
 * come `&nbsp;` lancia XmlPullParserException e la guida va in errore — è
 * una delle cause più comuni con guide di terze parti. Qui le entità
 * nominate note vengono riscritte in forma numerica equivalente mentre il
 * testo scorre (memoria costante, niente file intero in RAM) e le nominate
 * sconosciute vengono rimosse: meglio un carattere perso che la guida
 * intera persa.
 *
 * Le entità possono essere spezzate tra due chunk di lettura (`...&n` | `bsp;
 * ...`): il buffer trattiene la coda a partire da `&` finché l'entità non è
 * completa o supera il limite di lunghezza, così non si spezza mai una
 * sostituzione a metà.
 *
 * Estende [Reader] direttamente (NON FilterReader): il read() a carattere
 * singolo di FilterReader delega al sorgente bypassando il filtro, mentre
 * Reader.read() ricostruisce il carattere dal read(cbuf, off, len) qui
 * sotto, quindi passa SEMPRE dal sanitizzatore.
 */
class HtmlEntitySanitizingReader(
    private val source: Reader,
) : Reader() {

    private companion object {

        /** Oltre questa lunghezza `&...` non è un'entità: si emette com'è. */
        const val MAX_ENTITY_LENGTH = 24

        const val CHUNK = 8192

        /**
         * Entità HTML nominate (oltre alle 5 predefinite XML, gestite dal
         * parser) → equivalente numerico. Mappate le più frequenti nei file
         * XMLTV reali: latin-1, punteggiatura tipografica, simboli.
         */
        val NAMED_ENTITIES: Map<String, String> = mapOf(
            "nbsp" to "&#160;", "iexcl" to "&#161;", "cent" to "&#162;",
            "pound" to "&#163;", "curren" to "&#164;", "yen" to "&#165;",
            "brvbar" to "&#166;", "sect" to "&#167;", "uml" to "&#168;",
            "copy" to "&#169;", "ordf" to "&#170;", "laquo" to "&#171;",
            "not" to "&#172;", "shy" to "&#173;", "reg" to "&#174;",
            "macr" to "&#175;", "deg" to "&#176;", "plusmn" to "&#177;",
            "sup2" to "&#178;", "sup3" to "&#179;", "acute" to "&#180;",
            "micro" to "&#181;", "para" to "&#182;", "middot" to "&#183;",
            "cedil" to "&#184;", "sup1" to "&#185;", "ordm" to "&#186;",
            "raquo" to "&#187;", "frac14" to "&#188;", "frac12" to "&#189;",
            "frac34" to "&#190;", "iquest" to "&#191;",
            "Agrave" to "&#192;", "Aacute" to "&#193;", "Acirc" to "&#194;",
            "Atilde" to "&#195;", "Auml" to "&#196;", "Aring" to "&#197;",
            "AElig" to "&#198;", "Ccedil" to "&#199;", "Egrave" to "&#200;",
            "Eacute" to "&#201;", "Ecirc" to "&#202;", "Euml" to "&#203;",
            "Igrave" to "&#204;", "Iacute" to "&#205;", "Icirc" to "&#206;",
            "Iuml" to "&#207;", "ETH" to "&#208;", "Ntilde" to "&#209;",
            "Ograve" to "&#210;", "Oacute" to "&#211;", "Ocirc" to "&#212;",
            "Otilde" to "&#213;", "Ouml" to "&#214;", "times" to "&#215;",
            "Oslash" to "&#216;", "Ugrave" to "&#217;", "Uacute" to "&#218;",
            "Ucirc" to "&#219;", "Uuml" to "&#220;", "Yacute" to "&#221;",
            "THORN" to "&#222;", "szlig" to "&#223;",
            "agrave" to "&#224;", "aacute" to "&#225;", "acirc" to "&#226;",
            "atilde" to "&#227;", "auml" to "&#228;", "aring" to "&#229;",
            "aelig" to "&#230;", "ccedil" to "&#231;", "egrave" to "&#232;",
            "eacute" to "&#233;", "ecirc" to "&#234;", "euml" to "&#235;",
            "igrave" to "&#236;", "iacute" to "&#237;", "icirc" to "&#238;",
            "iuml" to "&#239;", "eth" to "&#240;", "ntilde" to "&#241;",
            "ograve" to "&#242;", "oacute" to "&#243;", "ocirc" to "&#244;",
            "otilde" to "&#245;", "ouml" to "&#246;", "divide" to "&#247;",
            "oslash" to "&#248;", "ugrave" to "&#249;", "uacute" to "&#250;",
            "ucirc" to "&#251;", "uuml" to "&#252;", "yacute" to "&#253;",
            "thorn" to "&#254;", "yuml" to "&#255;",
            "OElig" to "&#338;", "oelig" to "&#339;", "Scaron" to "&#352;",
            "scaron" to "&#353;", "Yuml" to "&#376;", "fnof" to "&#402;",
            "circ" to "&#710;", "tilde" to "&#732;", "ensp" to "&#8194;",
            "emsp" to "&#8195;", "thinsp" to "&#8201;", "zwnj" to "&#8204;",
            "zwj" to "&#8205;", "lrm" to "&#8206;", "rlm" to "&#8207;",
            "ndash" to "&#8211;", "mdash" to "&#8212;", "lsquo" to "&#8216;",
            "rsquo" to "&#8217;", "sbquo" to "&#8218;", "ldquo" to "&#8220;",
            "rdquo" to "&#8221;", "bdquo" to "&#8222;", "dagger" to "&#8224;",
            "Dagger" to "&#8225;", "bull" to "&#8226;", "hellip" to "&#8230;",
            "permil" to "&#8240;", "prime" to "&#8242;", "Prime" to "&#8243;",
            "lsaquo" to "&#8249;", "rsaquo" to "&#8250;", "oline" to "&#8254;",
            "frasl" to "&#8260;", "euro" to "&#8364;", "trade" to "&#8482;",
        )

        /** Entità numeriche già valide per XML: passano senza ritocchi.
         *
         * Stringa con escape (non raw """): un letterale raw seguito da `)`
         * fa mangiare al lettore di stringa degli script di verifica un
         * carattere oltre la chiusura (falso sbilanciamento di parentesi).
         */
        private val NUMERIC_ENTITY = Regex("#(?:[0-9]+|[xX][0-9A-Fa-f]+)")
    }

    private val buffer = StringBuilder()
    private var pos = 0
    private var sourceEof = false

    /** Numero di caratteri emettibili senza toccare un'entità sospesa. */
    private var safeEnd = 0

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        // Riempie finché c'è almeno 1 carattere emettibile o il sorgente è
        // finito (a EOF si emette tutto, entità sospese incluse: è testo
        // residuo, non ci saranno più dati per completarle).
        while (safeEnd <= pos && !sourceEof) {
            if (fill() == -1 && sourceEof) break
        }
        val available = if (sourceEof) buffer.length - pos else safeEnd - pos
        if (available <= 0) return if (sourceEof && buffer.length - pos <= 0) -1 else 0
        val count = minOf(len, available)
        buffer.getChars(pos, pos + count, cbuf, off)
        pos += count
        compact()
        return count
    }

    override fun ready(): Boolean = source.ready() || buffer.length - pos > 0

    override fun close() {
        source.close()
    }

    /** Legge un chunk dal sorgente; -1 a EOF (e processa l'ultima coda). */
    private fun fill(): Int {
        val chunk = CharArray(CHUNK)
        val n = try {
            source.read(chunk)
        } catch (error: IOException) {
            sourceEof = true
            throw error
        }
        if (n < 0) {
            sourceEof = true
            rescan()
            return -1
        }
        buffer.append(chunk, 0, n)
        // Il primo BOM UTF-8 già filtrato a livello byte potrebbe arrivare
        // qui come carattere U+FEFF: lo eliminiamo dalla testa del flusso.
        if (pos == 0 && buffer.isNotEmpty() && buffer[0] == '\uFEFF') {
            buffer.deleteCharAt(0)
        }
        rescan()
        return n
    }

    /**
     * Scansiona il buffer da [pos] e sostituisce le entità complete; si
     * ferma su un'eventuale entità incompleta (la coda resta in buffer e
     * [safeEnd] la protegge dall'emissione fino a risoluzione).
     */
    private fun rescan() {
        var i = pos
        while (i < buffer.length) {
            val amp = buffer.indexOf('&', i)
            if (amp < 0) {
                safeEnd = buffer.length
                return
            }
            val semi = buffer.indexOf(';', amp + 1)
            if (semi < 0 || semi - amp > MAX_ENTITY_LENGTH) {
                if (!sourceEof && semi < 0) {
                    // Potenziale entità spezzata tra i chunk: attendiamo
                    // altri dati (limite [MAX_ENTITY_LENGTH] a garanzia di
                    // progresso: oltre, si emette come testo letterale).
                    safeEnd = amp
                    return
                }
                // Nessun ';' entro limite (o EOF): non è un'entità.
                i = amp + 1
                continue
            }
            val name = buffer.substring(amp + 1, semi)
            val replacement = NAMED_ENTITIES[name]
            when {
                replacement != null -> {
                    buffer.replace(amp, semi + 1, replacement)
                    i = amp + replacement.length
                }

                name.startsWith("#") && NUMERIC_ENTITY.matches(name) -> {
                    i = semi + 1
                }

                else -> {
                    // Entità nominata sconosciuta: rimossa (XML non valido
                    // per il parser; tenere il testo grezzo lo farà piantare).
                    buffer.replace(amp, semi + 1, "")
                    i = amp
                }
            }
        }
        safeEnd = buffer.length
    }

    /** Scarta la parte consumata del buffer per limitare la crescita. */
    private fun compact() {
        if (pos > CHUNK) {
            buffer.delete(0, pos)
            safeEnd -= pos
            pos = 0
        }
    }
}
