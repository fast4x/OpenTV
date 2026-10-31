package it.fast4x.opentv.data.remote

import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.model.SourceProfile

/**
 * Pacchetto "profilo + canali" (feature export/import a livello di profilo):
 * un profilo di sorgente con i CANALI della sua playlist, in un unico file
 * riassorbibile con l'import generico delle impostazioni.
 *
 * Formato testuale (estensione `.opentv`), progettato per restare LEGGIBILE
 * e per degradare in una playlist M3U qualunque:
 *
 * ```
 * #OPENTV-PROFILE v1
 * #NAME: FreeIPTV Italy
 * #PLAYLIST-URL: https://esempio.it/playlist.m3u8
 * #EPG-URL: https://esempio.it/guida.xml.gz
 * #CHANNELS-BEGIN
 * #EXTM3U x-tvg-url="..."
 * #EXTINF:-1 tvg-id="Rai1.it" ... ,Rai 1
 * https://stream...
 * ```
 *
 * La sezione dopo `#CHANNELS-BEGIN` è una playlist M3U completa prodotta da
 * [M3UWriter] (guida incorporata nell'header compresa): l'import la passa a
 * [M3UParser] senza perdita di metadati e la sorgente locale risultante è
 * identica a quella di un'importazione M3U classica. Le intestazioni prima
 * di `#CHANNELS-BEGIN` sono ignorate da qualunque player M3U standard.
 */
data class ProfileExportBundle(
    /**
     * Il profilo da esportare/ripristinare. In un bundle prodotto da
     * [ProfileCodec.decode] l'id è SEMPRE vuoto: l'assegnazione (o il
     * riutilizzo dell'id di un profilo omonimo da sovrascrivere) spetta
     * alla persistenza, che conosce i profili già presenti.
     */
    val profile: SourceProfile,
    val channels: List<Channel>,

    /**
     * Guida TV incorporata dichiarata dalla playlist del profilo (header
     * `x-tvg-url`, fix35): viene riscritta nel corpo M3U del pacchetto così
     * una re-importazione ricarica la stessa guida senza riconfigurazione.
     */
    val embeddedEpgUrl: String? = null,
)

object ProfileCodec {

    /** Prima riga di ogni pacchetto profilo (seguita dalla versione). */
    const val MARKER = "#OPENTV-PROFILE"

    private const val VERSION_LINE = "$MARKER v1"
    private const val KEY_NAME = "#NAME:"
    private const val KEY_PLAYLIST = "#PLAYLIST-URL:"
    private const val KEY_EPG = "#EPG-URL:"
    private const val CHANNELS_BEGIN = "#CHANNELS-BEGIN"

    /** true se il testo sembra un pacchetto profilo OpenTV. */
    fun isBundle(text: String): Boolean =
        text.trimStart().startsWith(MARKER, ignoreCase = true)

    /** Produci il testo del pacchetto: intestazioni profilo + corpo M3U. */
    fun encode(bundle: ProfileExportBundle): String = buildString {
        appendLine(VERSION_LINE)
        appendLine("$KEY_NAME ${sanitize(bundle.profile.name)}")
        appendLine("$KEY_PLAYLIST ${sanitize(bundle.profile.playlistUrl)}")
        appendLine("$KEY_EPG ${sanitize(bundle.profile.epgUrl)}")
        appendLine(CHANNELS_BEGIN)
        // La guida incorporata viaggia nell'header del corpo M3U: l'import
        // la ritrova senza doverla ripescare dalle intestazioni del profilo
        // (che restano la fonte esplicita quando il profilo dichiara una
        // guida sua: SourceResolver dà a quella la precedenza).
        append(M3UWriter.build(bundle.channels, bundle.embeddedEpgUrl))
    }

    /**
     * Interpreta un pacchetto profilo (null se marker presente ma contenuto
     * non valido: nome mancante o nessun canale nel corpo). Il confronto con
     * [isBundle] decide il tipo di file: un M3U semplice NON passa da qui.
     */
    fun decode(text: String): ProfileExportBundle? {
        if (!isBundle(text)) return null
        var name = ""
        var playlistUrl = ""
        var epgUrl = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith(CHANNELS_BEGIN, ignoreCase = true) -> break
                line.startsWith(KEY_NAME, ignoreCase = true) -> name = valueOf(line, KEY_NAME)
                line.startsWith(KEY_PLAYLIST, ignoreCase = true) ->
                    playlistUrl = valueOf(line, KEY_PLAYLIST)
                line.startsWith(KEY_EPG, ignoreCase = true) -> epgUrl = valueOf(line, KEY_EPG)
            }
        }
        if (name.isBlank()) return null
        val body = m3uBody(text)
        val channels = M3UParser.parse(body)
        if (channels.isEmpty()) return null
        val embedded = runCatching {
            M3UParser.selectEpgUrls(M3UParser.parseEpgUrls(body), channels).firstOrNull()
        }.getOrNull()
        return ProfileExportBundle(
            profile = SourceProfile(
                id = "",
                name = name,
                playlistUrl = playlistUrl,
                epgUrl = epgUrl,
            ),
            channels = channels,
            embeddedEpgUrl = embedded,
        )
    }

    /**
     * Corpo M3U del pacchetto: tutto ciò che segue `#CHANNELS-BEGIN`. È il
     * testo che l'import passa a [PlaylistRepository.importPlaylist] come
     * sorgente locale (con la guida incorporata nell'header, se presente).
     */
    fun m3uBody(text: String): String =
        text.substringAfter(CHANNELS_BEGIN, "").trim()

    /**
     * Nome file suggerito per l'esportazione del profilo (solo caratteri
     * sicuri per i vari file manager): "opentv_profilo_Nome_Profilo.opentv".
     */
    fun suggestedFileName(profileName: String): String =
        "opentv_profilo_" + buildString {
            for (char in profileName) append(if (char.isLetterOrDigit()) char else '_')
        }.take(24) + ".opentv"

    /** Le intestazioni viaggiano una per riga: niente a capo/tab nei valori. */
    private fun sanitize(value: String): String =
        value.replace(Regex("[\\t\\r\\n]"), " ").trim()

    private fun valueOf(line: String, key: String): String =
        line.substringAfter(key).trim()
}
