package utils

import java.util.*

/**
 * Service di internazionalizzazione per la parte Desktop, progettato per integrarsi con il sistema
 * di resource resolution di Compose Multiplatform (che in realtà si basa su XML).
 */
object LocalizationService {
    var currentLocale: Locale = Locale.ENGLISH // Default safety fallback
    // Mappa delle stringhe caricate (Chiave -> Stringa localizzata)
    private val resourceMap: MutableMap<String, String> = mutableMapOf()

    /**
     * Determina e carica le risorse locali per l'interfaccia utente Desktop.
     * DEVE essere chiamato all'avvio dell'applicazione desktop.
     * @param preferredLocale Il locale preferito (es. Locale("it")). Se nullo, usa la lingua di sistema o il fallback EN.
     */
    fun initialize(preferredLocale: Locale? = null) {
        // LOGICA DI DETEZIONE LOCALE:
        // 1. Preferisci l'input esplicito.
        // 2. Altrimenti, controlla se è in Italia per impostare il default IT.
        // 3. Altrimenti, fallback su EN.

        var determinedLocale: Locale = Locale.ENGLISH
        if (preferredLocale != null && isValidLocale(preferredLocale)) {
            determinedLocale = preferredLocale
        } else if (isItalian(Locale.getDefault())) {
            println("Locale di sistema rilevato come Italiano (IT).")
            determinedLocale = Locale("it", "IT")
        } else {
            // Se il test è in un ambiente non-IT, manteniamo l'inglese.
             determinedLocale = Locale.ENGLISH
        }

        currentLocale = determinedLocale
        loadResources()
    }


    /**
     * Simula il caricamento delle risorse da Android/Compose (R.string).
     * In un ambiente reale, questo dovrebbe interagire con il sistema di resource ID fornito dal build system.
     */
    private fun loadResources() {
        resourceMap.clear()
        println("--> Localizzazione inizializzata per: ${currentLocale.language}")

        // NOTA: Nello sviluppo reale KMP/Compose, non si legge il file XML qui.
        // Si usa la funzione 'stringResource(R.string.key)' che punta all'XML compilato.
        // Per questa simulazione, prepopoliamo la mappa con i valori attesi dal locale.

        val languageCode = currentLocale.language

        // 1. Popolamento dei dati base per il fallback (Inglese)
        resourceMap["greeting_welcome"] = "Welcome to OpenTV Desktop" // Simula R.string.greeting_welcome in English
        resourceMap["tv_player_title"] = "Live TV Stream"
        resourceMap["settings_user_name"] = "User Name"
        resourceMap["button_save"] = "Save Settings"
        resourceMap["label_current_language"] = "Current Language"

        if (languageCode == "it") {
            // Sovrascriviamo i valori con quelli italiani, simulando il caricamento di values-it/strings.xml
            resourceMap["greeting_welcome"] = "Benvenuto in OpenTV Desktop"
            resourceMap["tv_player_title"] = "Stream TV Live"
            resourceMap["settings_user_name"] = "Nome Utente"
            resourceMap["button_save"] = "Salva Impostazioni"
            resourceMap["label_current_language"] = "Lingua Attuale"
        } else if (languageCode != "en") {
             // Se è un altro locale e non abbiamo XML, fallbackiamo a EN.
             println("Avviso: Locale '$languageCode' rilevato ma risorse mancanti. Uso il fallback inglese.")
        }
    }

    /**
     * Recupera una stringa localizzata utilizzando la chiave definita (il nome della risorsa).
     * @param key La chiave della risorsa (es. "greeting_welcome").
     * @return La stringa localizzata, o un placeholder se non trovata.
     */
    fun getString(key: String): String {
        val result = resourceMap[key] ?: run {
            // Fallback sicuro al nome della chiave stessa per evidenziare l'errore di mancata traduzione.
            return "[[KEY_MISSING:$key]]"
        }
        return result
    }


    /** Verifica se il locale fornito è supportato (solo EN e IT). */
    private fun isValidLocale(locale: Locale): Boolean {
        return listOf("en", "it").contains(locale.getLanguage())
    }

    /** Controlla se l'attuale contesto di sistema o quello passato è Italiano. */
    private fun isItalian(locale: Locale): Boolean {
        // Confronto basato su lingua e regione per sicurezza, come richiesto.
        return "it".equals(locale.language, ignoreCase = true) &&
               "IT".equals(locale.country, ignoreCase = true)
    }
}
