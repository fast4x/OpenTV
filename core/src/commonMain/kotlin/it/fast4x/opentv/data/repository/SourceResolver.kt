package it.fast4x.opentv.data.repository

import it.fast4x.opentv.data.model.AppSettings
import it.fast4x.opentv.data.model.SourceProfile

/**
 * Risolve la SORGENTE EFFETTIVA (playlist + guida TV) tenendo conto dei
 * profili di sorgente (feature "profilazione della lista").
 *
 * Modello:
 *  - NESSUN profilo attivo (o profilo mancante/senza playlist): catena
 *    storica, invariata — URL personale (campo Impostazioni) > playlist
 *    curata del paese scelto nei selettori > predefinita per paese del
 *    dispositivo; la guida segue EpgSources.resolve (manuale > paese >
 *    automatica dalla playlist).
 *  - PROFILO ATTIVO VALIDO: playlist e guida del profilo; i campi URL
 *    globali e la preferenza paese sono BYPASSATI (il profilo è autonomo,
 *    trasportabile: nulla di ciò che vale per iptv-org lo tocca). Con la
 *    guida del profilo vuota si prova comunque la derivazione automatica
 *    dalla playlist iptv-org del profilo (EpgSources.autoUrlForPlaylist).
 *
 * La playlist importata da file locale resta la via con priorità massima
 * (comportamento esistente, gestito dal repository): è ortogonale ai
 * profili, e la UI ne fa uscire l'utente quando la sorgente cambia.
 */
object SourceResolver {

    /**
     * Il profilo attivo: deve esistere tra i salvati E avere una playlist
     * (un profilo senza playlist non è una sorgente: lo scartiamo qui così
     * la risoluzione non fallisce mai a valle e la selezione pendente di un
     * profilo corrotto degrada nella catena storica invece di bloccare).
     */
    fun activeProfile(profiles: List<SourceProfile>, activeProfileId: String?): SourceProfile? =
        profiles.firstOrNull { it.id == activeProfileId }
            ?.takeIf { it.playlistUrl.isNotBlank() }

    /**
     * URL playlist imposto dal profilo attivo (null = nessun profilo valido:
     * il repository usa la catena storica). Valore già ripulito.
     */
    fun playlistUrlOf(profile: SourceProfile?): String? =
        profile?.playlistUrl?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * URL guida TV del profilo attivo: quello del profilo, altrimenti la
     * derivazione automatica dalla playlist iptv-org del profilo (stessi
     * tvg-id, stessa regola della sorgente automatica).
     */
    fun epgUrlOf(profile: SourceProfile?): String {
        profile ?: return ""
        val manual = profile.epgUrl.trim()
        if (manual.isNotEmpty()) return manual
        return EpgSources.autoUrlForPlaylist(profile.playlistUrl)
    }

    /**
     * URL guida TV effettivo complessivo: profilo attivo > risoluzione
     * storica (manuale > paese scelto > automatico dalla playlist).
     * Unica fonte di verità per i flussi che osservano le preferenze
     * (HomeViewModel e DesktopHomeState) e per i ricaricamenti puntuali
     * delle Impostazioni: con un profilo attivo la guida non "scappa"
     * mai sulla catena storica.
     *
     * fix35: per il caricamento vero e proprio usare [resolveEpgChain], che
     * considera anche la guida dichiarata dall'header della playlist
     * (x-tvg-url); questa funzione resta il valore PRIMARIO (primo
     * candidato) per compatibilità con i chiamanti esistenti.
     */
    fun resolveEpg(
        settings: AppSettings,
        profiles: List<SourceProfile>,
        activeProfileId: String?,
    ): String {
        val profile = activeProfile(profiles, activeProfileId)
        if (profile != null) return epgUrlOf(profile)
        return EpgSources.resolve(settings.epgUrl, settings.playlistUrl, settings.countryCode)
    }

    /**
     * CATENA dei candidati guida TV (fix35), in ordine di preferenza:
     *
     *  1. GUIDA ESPLICITA (campo guida del profilo attivo, oppure campo
     *     EPG delle impostazioni): la scelta dell'utente vince sempre e
     *     chiude la catena, com'era.
     *  2. GUIDA INCORPORATA NELLA PLAYLIST (header `#EXTM3U x-tvg-url=`,
     *     già selezionata e ordinata da M3UParser.selectEpgUrls in base ai
     *     canali caricati): è la guida dichiarata dalla lista stessa, quella
     *     che "non va specificata a parte". A seguire, come candidato di
     *     MERGE aggiuntivo, la risoluzione storica quando produce un URL
     *     diverso: per le playlist che seguono la convenzione iptv-org dei
     *     tvg-id la guida per paese (iptv-epg.org) recupera i canali che la
     *     guida incorporata non copre; le due guide vengono fuse nel
     *     caricatore.
     *  3. SOLO RISOLUZIONE STORICA: nessuna guida incorporata → manuale >
     *     paese scelto > automatica dalla playlist (comportamento invariato).
     *
     * Con un profilo attivo il contributo "storico" resta l'autoderivazione
     * dalla SUA playlist (epgUrlOf, vuota per le liste non iptv-org): la
     * preferenza paese globale non tocca mai un profilo.
     *
     * Lista vuota = nessuna guida da caricare.
     */
    fun resolveEpgChain(
        settings: AppSettings,
        profiles: List<SourceProfile>,
        activeProfileId: String?,
        embeddedFromPlaylist: List<String> = emptyList(),
    ): List<String> {
        val profile = activeProfile(profiles, activeProfileId)
        if (profile != null) {
            val manual = profile.epgUrl.trim()
            if (manual.isNotEmpty()) return listOf(manual)
            val derived = EpgSources.autoUrlForPlaylist(profile.playlistUrl)
            return embeddedChain(embeddedFromPlaylist, derived)
        }
        val manual = settings.epgUrl.trim()
        if (manual.isNotEmpty()) return listOf(manual)
        val historical = EpgSources.resolve("", settings.playlistUrl, settings.countryCode)
        return embeddedChain(embeddedFromPlaylist, historical)
    }

    /** Incorporata prima, storica in coda come merge (se diversa e non vuota). */
    private fun embeddedChain(embedded: List<String>, historical: String): List<String> {
        val chain = embedded.filter { it.isNotBlank() }.toMutableList()
        val extra = historical.trim()
        if (extra.isNotEmpty() && chain.none { it.equals(extra, ignoreCase = true) }) {
            chain += extra
        }
        return chain
    }
}
