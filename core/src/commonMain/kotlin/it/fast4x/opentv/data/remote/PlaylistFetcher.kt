package it.fast4x.opentv.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/** Errore HTTP con codice di stato, per generare messaggi leggibili all'utente. */
class HttpException(val code: Int) : IOException("HTTP $code")

/**
 * Client HTTP per il download di playlist M3U e guide XMLTV.
 * Supporta gzip e zip (anche annidati, individuati dal magic number),
 * redirect e timeout generosi pensati per file EPG di grandi dimensioni.
 *
 * ⚠️ OkHttp execute() è BLOCCANTE: la funzione è sospensibile e sposta il
 * lavoro su Dispatchers.IO, così non può mai finire sul main thread
 * (evita NetworkOnMainThreadException a prescindere dal chiamante).
 */
object PlaylistFetcher {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) OpenTV/1.0"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Client dedicato alle guide TV (fix34): i file XMLTV di terze parti
     * arrivano a centinaia di MB da server lenti e col timeout da 90s della
     * playlist il download cade spesso a metà ("Read timed out"), mostrato
     * all'utente come un errore della guida che non lo è. Il client condiviso
     * resta invariato per playlist e probe.
     */
    private val guideClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Scarica la risorsa e restituisce il contenuto testuale (UTF-8),
     * decomprimendolo automaticamente se il server/caché lo serve in gzip.
     * Il chiamante viene sospeso senza bloccare il thread su cui gira.
     */
    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code)
            val body = response.body ?: throw IOException("Risposta vuota dal server")
            val bytes = body.bytes()

            // Magic number gzip: 0x1F 0x8B
            val decompressed = if (bytes.size >= 2 &&
                bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()
            ) {
                GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
            } else {
                bytes
            }
            String(decompressed, Charsets.UTF_8)
        }
    }

    /**
     * Apre la risorsa come stream pronto da leggere, decomprimendo
     * automaticamente i payload gzip riconosciuti dal magic number (il
     * peek non consuma lo stream reale). A differenza di [fetchText] NON
     * carica il file in memoria come String: pensato per le guide XMLTV
     * per paese di iptv-epg.org (decine di MB decompressi) che vengono
     * parsate in streaming. Lo stream restituito va chiuso dal chiamante
     * (la chiusura rilascia anche la risposta HTTP).
     */
    suspend fun openStream(url: String): InputStream = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw HttpException(code)
        }
        val body = response.body ?: run {
            response.close()
            throw IOException("Risposta vuota dal server")
        }

        // Magic number gzip: 0x1F 0x8B (letto in peek, senza consumare).
        val isGzip = try {
            val peek = body.source().peek()
            peek.readByte() == 0x1F.toByte() && peek.readByte() == 0x8B.toByte()
        } catch (_: EOFException) {
            false
        }

        val raw = body.byteStream()
        if (isGzip) GZIPInputStream(raw, 8192) else raw
    }

    /**
     * Apre la GUIDA TV all'URL indicato (fix34, usato da [it.fast4x.opentv.data.repository.EpgRepository]).
     *
     * Rispetto a [openStream] (che resta per i chiamanti esistenti) aggiunge
     * la tolleranza necessaria alle guide di terze parti, che spesso NON
     * arrivano come XML nudo:
     *  - archivi ZIP (magic "PK") al posto del gzip: viene aperta la prima
     *    entry non-directory, preferendo le .xml/.xmltv;
     *  - contenitori annidati (es. .zip dentro .gz o viceversa): fino a 3
     *    livelli di decompressione, riconosciuti dal magic number;
     *  - read timeout dedicato a 180s (le guide grandi da server lenti
     *    superavano i 90s e il download moriva a metà).
     *
     * La decodifica testuale (BOM, encoding dichiarato, entità HTML) è a
     * valle, in [XmltvText.readerFor]: qui si lavora solo a livello byte.
     * Lo stream restituito va chiuso dal chiamante.
     */
    suspend fun openGuideStream(url: String): InputStream = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        val response = guideClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw HttpException(code)
        }
        val body = response.body ?: run {
            response.close()
            throw IOException("Risposta vuota dal server")
        }
        autoDecompress(body.byteStream(), maxLayers = 3)
    }

    /**
     * Consuma [input] riconoscendo e rimuovendo a strati i contenitori gzip
     * e zip (magic number letto con mark/reset su un BufferedInputStream, che
     * non consuma nulla in mancanza di match). Restituisce lo stream del
     * contenuto effettivo; la chiusura ricade a cascata sugli strati aperti.
     */
    private fun autoDecompress(input: InputStream, maxLayers: Int): InputStream {
        var stream: InputStream = BufferedInputStream(input, 8192)
        var layers = 0
        while (layers < maxLayers) {
            val (b0, b1) = peekMagic(stream) ?: break
            stream = when {
                b0 == 0x1F.toByte() && b1 == 0x8B.toByte() ->
                    BufferedInputStream(GZIPInputStream(stream, 8192), 8192)

                b0 == 'P'.code.toByte() && b1 == 'K'.code.toByte() ->
                    BufferedInputStream(openFirstXmlEntry(stream), 8192)

                else -> return stream
            }
            layers++
        }
        return stream
    }

    /** Legge 2 byte con mark/reset; null se lo stream finisce prima. */
    private fun peekMagic(stream: InputStream): Pair<Byte, Byte>? {
        stream.mark(2)
        val b0 = stream.read()
        val b1 = stream.read()
        stream.reset()
        if (b0 < 0 || b1 < 0) return null
        return b0.toByte() to b1.toByte()
    }

    /**
     * Apre lo zip e si posiziona sulla prima entry XML riconoscibile
     * (.xml/.xmltv/.gz): le entry precedenti (cartelle, .txt, .nfo) vengono
     * saltate. Non esiste un fallback "prima entry qualunque": uno stream
     * ZipInputStream non si riavvolge, quindi se nessuna entry ha un nome
     * XML si esce con un messaggio esplicito (i packer di guide EPG nominano
     * sempre correttamente l'XML).
     */
    private fun openFirstXmlEntry(stream: InputStream): InputStream {
        val zip = ZipInputStream(stream, Charsets.ISO_8859_1)
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val name = entry.name.substringAfterLast('/').lowercase()
                val isXml = name.endsWith(".xml") || name.endsWith(".xmltv") ||
                    name.endsWith(".gz")
                if (isXml) return zip
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        throw IOException("L'archivio ZIP della guida non contiene un file XML riconoscibile (.xml/.xmltv/.gz)")
    }
}
