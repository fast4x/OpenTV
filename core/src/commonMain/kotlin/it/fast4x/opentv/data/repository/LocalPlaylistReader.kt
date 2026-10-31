package it.fast4x.opentv.data.repository

/**
 * Accesso alla playlist locale importata, astratto dalla piattaforma.
 *
 * L'id sorgente è una stringa opaca persistita nelle preferenze
 * (`localPlaylistUri`): su Android è l'URI SAF scelto col selettore di
 * sistema (letto via ContentResolver), su desktop è il percorso del file
 * (letto con java.io). Ritorna null se la sorgente non è leggibile: il
 * repository prosegue con la catena URL/cache standard.
 */
fun interface LocalPlaylistReader {

    /** Testo della playlist dalla sorgente indicata, o null se illeggibile. */
    suspend fun read(sourceId: String): String?
}
