Risorse incluse nell'app desktop pacchettizzata (appResourcesRootDir).

Il task Gradle "bundleVlc" (attivato con -Popentv.bundleVlc=true) copia qui
windows/vlc/ con le librerie libVLC dell'installazione locale di VLC
(libvlc.dll + libvlccore.dll + plugins). A runtime VlcLocator cerca il bundle
in <app>/resources/vlc.

Questo file NON viene usato dall'applicazione.
