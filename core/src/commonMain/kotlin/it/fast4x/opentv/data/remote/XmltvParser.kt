package it.fast4x.opentv.data.remote

import it.fast4x.opentv.data.model.Programme
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parser per file EPG in formato XMLTV.
 *
 * Estrae solo i programmi che cadono dentro una finestra temporale
 * (per contenere l'uso di memoria su guide di grandi dimensioni)
 * e restituisce una mappa `channelId -> programmi ordinati per inizio`.
 */
object XmltvParser {

    data class Result(
        val channelNames: Map<String, String>,
        /**
         * fix35: TUTTI i display-name di ogni canale (il primo dichiarato è
         * il nome principale, i seguenti sono alias/nomi alternativi).
         * Serve all'[it.fast4x.opentv.data.repository.EpgRepository] per
         * costruire l'indice dei nomi che recupera i canali la cui guida
         * non combacia per id.
         */
        val channelAliases: Map<String, List<String>>,
        val programmes: Map<String, List<Programme>>,
        val programmeCount: Int,
    )

    private val timeFormats = listOf(
        "yyyyMMddHHmmss Z",
        "yyyyMMddHHmm Z",
        "yyyyMMddHHmmssZ",
        "yyyyMMddHHmmZ",
        "yyyyMMddHHmmss",
        "yyyyMMddHHmm",
    )

    fun parse(reader: Reader, windowStart: Long, windowEnd: Long): Result {
        // XmlPullParserFactory al posto di android.util.Xml: l'API XmlPull
        // esiste sia sulla piattaforma Android (implementazione KxmlParser
        // preinstallata) sia su JVM desktop (via kxml2), così il parser è
        // identico su entrambe le piattaforme.
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        // fix34: modalità "relaxed" di kxml2/KxmlParser — tollera i difetti
        // tipici delle guide di terze parti (tag non bilanciati, attributi
        // senza valore) invece di abortire l'intera guida. Non tutte le
        // implementazioni la espongono: fallisce silenziosamente.
        try {
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true)
        } catch (_: Exception) {
            // Implementazione senza feature relaxed: si prosegue com'era.
        }
        parser.setInput(reader)

        val channelNames = HashMap<String, String>()
        val channelAliases = HashMap<String, MutableList<String>>()
        val programmes = HashMap<String, MutableList<Programme>>()
        var total = 0

        var insideChannel = false
        var currentChannelId: String? = null
        var progChannel: String? = null
        var progStart = 0L
        var progStop = 0L
        var progTitle: String? = null
        var progDesc: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> {
                        insideChannel = true
                        currentChannelId = parser.getAttributeValue(null, "id")
                    }

                    "display-name" -> {
                        val id = if (insideChannel) currentChannelId else progChannel
                        if (id != null) {
                            val name = parser.nextText().trim()
                            if (name.isNotEmpty()) {
                                channelNames[id] = name
                                // fix35: alias completi (una guida può dichiarare
                                // più nomi per lo stesso canale, es. "Sky Uno" e
                                // "Sky Uno HD"): tutti e soli quelli del canale.
                                if (insideChannel) {
                                    channelAliases.getOrPut(id) { ArrayList() }.add(name)
                                }
                            }
                        }
                    }

                    "programme" -> {
                        progChannel = parser.getAttributeValue(null, "channel")
                        progStart = parseTime(parser.getAttributeValue(null, "start"))
                        progStop = parseTime(parser.getAttributeValue(null, "stop"))
                        progTitle = null
                        progDesc = null
                    }

                    "title" -> if (progChannel != null) {
                        progTitle = parser.nextText().trim().ifBlank { null }
                    }

                    "desc" -> if (progChannel != null) {
                        progDesc = parser.nextText().trim().ifBlank { null }
                    }
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "channel" -> {
                        insideChannel = false
                        currentChannelId = null
                    }

                    "programme" -> {
                        val channel = progChannel
                        val title = progTitle
                        if (channel != null && title != null && progStart > 0L &&
                            progStop > windowStart && progStart < windowEnd
                        ) {
                            programmes.getOrPut(channel) { ArrayList() } += Programme(
                                channelId = channel,
                                start = progStart,
                                stop = progStop,
                                title = title,
                                description = progDesc,
                            )
                            total++
                        }
                        progChannel = null
                        progTitle = null
                        progDesc = null
                    }
                }
            }
            event = parser.next()
        }

        return Result(
            channelNames = channelNames,
            channelAliases = channelAliases,
            programmes = programmes.mapValues { (_, list) -> list.sortedBy { it.start } },
            programmeCount = total,
        )
    }

    /**
     * Converte il timestamp XMLTV (`yyyyMMddHHmmss ±zzzz`) in epoch millis.
     * Se non è dichiarato un offset, il tempo è interpretato nel timezone locale.
     */
    private fun parseTime(raw: String?): Long {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return 0L
        for (pattern in timeFormats) {
            try {
                val format = SimpleDateFormat(pattern, Locale.ROOT)
                if ('Z' !in pattern) {
                    format.timeZone = TimeZone.getDefault()
                } else {
                    format.timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = format.parse(value)
                if (date != null) return date.time
            } catch (_: Exception) {
                // Prova il formato successivo
            }
        }
        return 0L
    }
}
