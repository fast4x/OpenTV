package it.fast4x.opentv.data.remote

/**
 * Estrattore del payload "flight" (React Server Components) incorporato
 * nelle pagine Next.js renderizzate server-side, come le pagine della guida
 * TV di la7play.it usate dal "Rivedi" ([it.fast4x.opentv.data.repository.La7CatchupRepository]).
 *
 * Il payload vive in una serie di chiamate `self.__next_f.push([1,"…"])`
 * alla fine dell'HTML: ogni chiamata porta un FRAMMENTO dell'intero payload
 * come stringa JavaScript (con escape JSON). I frammenti vanno concatenati
 * in ordine PRIMA di cercare qualunque cosa: l'oggetto guida può attraversare
 * il confine di due frammenti.
 */
object RscFlight {

    /**
     * Prefisso letterale di un push flight: tutto ciò che segue, fino alla
     * prossima '"' NON preceduta da backslash, è il frammento grezzo del
     * payload (escape JS ancora presenti). L'iniziale `[1,` seleziona i
     * push di testo (i chunk del payload) e ignora gli altri ([0,"$…"]).
     */
    private const val PUSH_PREFIX = "self.__next_f.push([1,\""

    /**
     * Ricompone il payload flight completo della pagina: concatena (in ordine
     * di apparizione) tutti i frammenti `self.__next_f.push([1,"…"])` dopo
     * aver risolto gli escape di stringa. Le pagine senza payload (404,
     * errori) restituiscono una stringa vuota: è il segnale che la sorgente
     * non è nel formato atteso.
     *
     * VOLUTAMENTE SENZA REGEX: il motore regex della JVM desktop (java.util.
     * regex) ricorsione a ogni iterazione di un gruppo quantificato, e un
     * frammento flight da centinaia di KB (la guida incorpora ~15 giorni di
     * programmazione) supera lo stack del thread con StackOverflowError
     * (crash reale su desktop, 11/2026). Android usa un'implementazione
     * regex diversa (ICU, non ricorsiva) e non crashava, ma il parser deve
     * avere lo stesso comportamento su tutte le piattaforme: la scansione
     * qui sotto è lineare, iterativa e senza ricorsione.
     */
    fun payloadOf(html: String): String =
        buildString(capacity = html.length / 2) {
            var i = html.indexOf(PUSH_PREFIX)
            while (i >= 0) {
                val start = i + PUSH_PREFIX.length
                val end = rawStringEnd(html, start)
                if (end < 0) break // stringa mai chiusa: HTML troncato, niente altri frammenti
                append(unescape(html.substring(start, end)))
                i = html.indexOf(PUSH_PREFIX, end + 1)
            }
        }

    /**
     * Indice del '"' che chiude la stringa JS aperta a [start] (primo
     * carattere del contenuto): un '\' consuma anche il carattere evaso che
     * segue (\" \\ \u… non chiudono la stringa), un '"' nudo la chiude.
     * -1 se la stringa non si chiude mai (HTML troncato).
     */
    private fun rawStringEnd(text: String, start: Int): Int {
        var i = start
        val n = text.length
        while (i < n) {
            when (text[i]) {
                '\\' -> i++ // salta il carattere evaso (l'i++ finale salta anche '\')
                '"' -> return i
            }
            i++
        }
        return -1
    }

    /**
     * Risolve gli escape di una stringa JavaScript/JSON grezza (senza i
     * delimitatori): \" \\ \/ \b \f \n \r \t \uXXXX. Le sequenze ignote
     * restano letterali (tolleranza per payload futuri): meglio un carattere
     * sbagliato in un campo descrittivo che un fallimento dell'intera guida.
     */
    fun unescape(raw: String): String {
        if ('\\' !in raw) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        val n = raw.length
        while (i < n) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= n) {
                sb.append(c)
                i++
                continue
            }
            when (val e = raw[i + 1]) {
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                '/' -> sb.append('/')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'u' -> {
                    if (i + 5 < n) {
                        val code = raw.substring(i + 2, i + 6).toIntOrNull(16)
                        if (code != null) {
                            sb.appendCodePoint(code)
                            i += 4
                        } else {
                            sb.append(e)
                        }
                    } else {
                        sb.append(e)
                    }
                }

                else -> sb.append(e)
            }
            i += 2
        }
        return sb.toString()
    }

    /**
     * Estrae l'oggetto JSON bilanciato che si apre alla posizione [openAt]
     * (che deve puntare al carattere '{'): bilancia le graffe fino alla
     * chiusura, consapevole delle stringhe (le graffe dentro i valori,
     * tipiche di sinossi e titoli, non contano). Restituisce il TESTO
     * dell'oggetto, pronto per [parseJson]; null se [openAt] non punta a
     * '{' o l'oggetto non si chiude (payload troncato).
     */
    fun objectAt(payload: String, openAt: Int): String? {
        if (openAt < 0 || openAt >= payload.length || payload[openAt] != '{') return null

        var depth = 0
        var inString = false
        var i = openAt
        val n = payload.length
        while (i < n) {
            val c = payload[i]
            if (inString) {
                when (c) {
                    '\\' -> i++ // salta il carattere evaso (\" non chiude la stringa)
                    '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return payload.substring(openAt, i + 1)
                    }
                }
            }
            i++
        }
        return null
    }

    /**
     * Posizioni di tutte le occorrenze di [marker] nel payload, in ordine.
     * Usate con [objectAt] (il marker termina con '{') per estrarre ogni
     * candidato e accettare il primo che parsa come la struttura cercata:
     * il payload flight può contenere più blocchi con la stessa chiave
     * (dati del layout, SEO, cache) e il controllo semantico a valle è più
     * affidabile di qualunque euristica sulla posizione.
     */
    fun occurrences(payload: String, marker: String): List<Int> {
        val positions = ArrayList<Int>()
        var index = payload.indexOf(marker)
        while (index >= 0) {
            positions += index
            index = payload.indexOf(marker, index + marker.length)
        }
        return positions
    }
}
