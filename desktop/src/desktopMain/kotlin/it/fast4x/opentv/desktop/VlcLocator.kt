package it.fast4x.opentv.desktop

import java.io.File

/**
 * Individuazione delle librerie native libVLC per il desktop.
 *
 * vlcj non contiene libVLC: sono le librerie installate da VLC Media Player
 * (libvlc.dll / libvlc.so / libvlc.dylib). La discovery di default di vlcj su
 * Windows legge SOLO la chiave di registro HKLM\SOFTWARE\VideoLAN\VLC: non
 * trova quindi né le installazioni "per utente" in %LOCALAPPDATA%, né le
 * cartelle portable, né un VLC non installato ma scaricato a parte.
 *
 * Ordine di risoluzione (primo trovato vince):
 *  1. proprietà di sistema `-Dopentv.vlc.path=<cartella di VLC>`
 *  2. variabile d'ambiente `OPENTV_VLC_PATH`
 *  3. app pacchettizzata: `<app.dir>/vlc` oppure `<resources>/vlc`
 *     (bundle libVLC incluso via Gradle, task bundleVlc)
 *  4. percorsi di installazione noti per piattaforma
 *
 * La directory trovata viene aggiunta a `jna.library.path` PRIMA del primo
 * caricamento di vlcj: JNA carica libvlc e vlcj (JnaLibraryPathDirectoryProvider)
 * imposta anche VLC_PLUGIN_PATH per i plugin/codec di VLC.
 */
internal object VlcLocator {

    /**
     * Prepara `jna.library.path` con la directory libVLC trovata, se esiste.
     * Deve essere chiamato prima di creare il primo componente vlcj.
     * @return la directory risolta, o null se non trovata (la discovery di
     * default di vlcj può comunque riuscire, es. registro HKLM).
     */
    fun prepareNativeLibraryPath(): String? {
        val dir = resolveVlcDirectory() ?: return null
        val current = System.getProperty("jna.library.path")
        when {
            current.isNullOrBlank() -> System.setProperty("jna.library.path", dir)
            !current.split(File.pathSeparator).contains(dir) ->
                System.setProperty("jna.library.path", "$current${File.pathSeparator}$dir")
        }
        return dir
    }

    /** Messaggio d'aiuto da mostrare all'utente quando libVLC non è caricabile. */
    fun installHint(): String = when {
        isWindows ->
            "Impossibile caricare le librerie VLC (libvlc.dll). Installa VLC Media " +
                "Player a 64 bit da https://www.videolan.org e riavvia OpenTV. Se VLC " +
                "è già installato in una cartella personalizzata, imposta la variabile " +
                "d'ambiente OPENTV_VLC_PATH alla cartella che contiene libvlc.dll " +
                "(esempio: C:\\Program Files\\VideoLAN\\VLC)."
        isMac ->
            "Impossibile caricare le librerie VLC (libvlc.dylib). Installa VLC in " +
                "/Applications da https://www.videolan.org e riavvia OpenTV, oppure " +
                "imposta la variabile d'ambiente OPENTV_VLC_PATH a " +
                "/Applications/VLC.app/Contents/MacOS."
        else ->
            "Impossibile caricare le librerie VLC (libvlc.so). Installa VLC o le " +
                "librerie libvlc (esempio: sudo apt install vlc) e riavvia OpenTV; in " +
                "alternativa imposta la variabile d'ambiente OPENTV_VLC_PATH alla " +
                "cartella che contiene libvlc.so."
    }

    private fun resolveVlcDirectory(): String? {
        // 1. -Dopentv.vlc.path=...
        System.getProperty("opentv.vlc.path")?.let { path ->
            val dir = File(path)
            if (dir.isDirectory) return dir.absolutePath
        }

        // 2. OPENTV_VLC_PATH
        System.getenv("OPENTV_VLC_PATH")?.let { path ->
            val dir = File(path)
            if (dir.isDirectory) return dir.absolutePath
        }

        // 3. Bundle dentro l'app pacchettizzata (jpackage/compose: <app.dir>/vlc)
        System.getProperty("app.dir")?.let { appDir ->
            val dir = File(appDir, "vlc")
            if (dir.isDirectory) return dir.absolutePath
        }

        // 3b. Bundle incluso via appResourcesRootDir (task bundleVlc Gradle):
        // compose copia resources/windows/vlc in <resources>/vlc nell'immagine
        // pacchettizzata e lo rende raggiungibile con questa property.
        System.getProperty("compose.application.resources.dir")?.let { resDir ->
            val dir = File(resDir, "vlc")
            if (dir.isDirectory) return dir.absolutePath
        }

        // 4. Percorsi noti: il primo che contiene la libreria nativa giusta.
        val libName = when {
            isWindows -> "libvlc.dll"
            isMac -> "libvlc.dylib"
            else -> "libvlc.so"
        }
        return wellKnownDirectories()
            .firstOrNull { File(it, libName).exists() }
            ?.let { File(it).absolutePath }
    }

    private fun wellKnownDirectories(): List<String> {
        val localAppData = System.getenv("LOCALAPPDATA")
        return buildList {
            when {
                isWindows -> {
                    // Installazione "per utente" (non visibile al registro HKLM
                    // letto da vlcj): entrambe le convenzioni note.
                    if (localAppData != null) {
                        add("$localAppData\\Programs\\VideoLAN\\VLC")
                        add("$localAppData\\VideoLAN\\VLC")
                    }
                    add("C:\\Program Files\\VideoLAN\\VLC")
                    add("C:\\Program Files (x86)\\VideoLAN\\VLC")
                }
                isMac -> {
                    add("/Applications/VLC.app/Contents/MacOS")
                    val home = System.getProperty("user.home")
                    if (home != null) add("$home/Applications/VLC.app/Contents/MacOS")
                }
                else -> {
                    // Linux: i pacchetti di sistema vengono trovati dal loader
                    // (ldconfig); servono i casi fuori path, come VLC snap.
                    add("/snap/vlc/current/usr/lib")
                    add("/usr/lib/x86_64-linux-gnu")
                    add("/usr/lib64")
                    add("/usr/lib")
                }
            }
        }
    }

    internal val isWindows: Boolean
        get() = System.getProperty("os.name").lowercase().contains("windows")

    internal val isMac: Boolean
        get() = System.getProperty("os.name").lowercase().contains("mac")
}
