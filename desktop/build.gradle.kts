import java.io.File
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.desktop)
}

// App desktop OpenTV (Windows/Linux/macOS) basata su Compose Multiplatform.
// Riutilizza tutta la logica del modulo :core (playlist, preferiti, EPG,
// diagnosi sorgenti) e usa libVLC (via vlcj) come motore di riproduzione.
kotlin {
    jvm("desktop") {
        // JVM 21 uniforme con :core e :app: il default KMP sarebbe 1.8 e
        // andrebbe in conflitto con :core, che compila a 21.
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":core"))
                implementation(libs.vlcj)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.okhttp)

                implementation(compose.ui)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "it.fast4x.opentv.desktop.MainKt"

        nativeDistributions {
            // STEP OT-3 (workflow release, scelta "specchia RiPlay"): formati
            // estesi da Msi/Exe/Deb a TUTTI i formati nativi: jpackage non fa
            // cross-building, quindi ogni formato esce solo dal runner del suo
            // sistema (MSI/EXE su windows-latest, DEB/RPM su Linux, DMG solo su
            // macOS) -> matrix build-desktop nel workflow.
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg)
            packageName = "OpenTV"

            // STEP OT-2 (workflow release): jpackage esige una versione
            // NUMERICA (MSI vuole major.minor.build, max 3 componenti): il
            // workflow passa -Popentv.packageVersion=v1.2.3-rc.1 e qui viene
            // ritagliata la parte numerica (v1.0.0-rc.1 -> "1.0.0", esattamente
            // come fa il proforma RiPlay con riplayPackageVersionForHost).
            // Senza property resta "1.0.0" (build locali invariate).
            val opentvPackageVersion: String =
                (findProperty("opentv.packageVersion") as? String)
                    ?.let { Regex("[0-9]+(\\.[0-9]+)*").find(it)?.value }
                    ?.split(".")
                    ?.take(3)
                    ?.joinToString(".")
                    ?.takeIf { it.isNotBlank() }
                    ?: "1.0.0"
            packageVersion = opentvPackageVersion
            vendor = "OpenTV"

            // Runtime completo nel pacchetto: evita "module not found" a
            // runtime con coroutines/okhttp/datastore/okio nelle app installate.
            includeAllModules = true

            // Cartella risorse incluse nel pacchetto (sottocartelle common/
            // windows/ ecc.): il task bundleVlc (fondo file) ci copia
            // opzionalmente l'installazione VLC locale in windows/vlc.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bundle opzionale di VLC dentro l'app pacchettizzata:
//
//   .\gradlew.bat :desktop:packageDistributionForCurrentOS -Popentv.bundleVlc=true
//
// Copia libvlc.dll + libvlccore.dll + plugins\ dell'installazione VLC locale
// dentro le risorse Windows dell'immagine; a runtime VlcLocator la trova in
// <app>/resources/vlc (System.getProperty("compose.application.resources.dir")).
// Senza questo flag il pacchetto richiede VLC gia' installato sul PC di
// destinazione (oppure OPENTV_VLC_PATH impostata dall'utente).
// ---------------------------------------------------------------------------
if ((findProperty("opentv.bundleVlc") as? String) == "true") {
    tasks.register<Copy>("bundleVlc") {
        group = "opentv"
        description =
            "Copia l'installazione VLC locale nelle risorse dell'app pacchettizzata (-Popentv.bundleVlc=true)"
        val candidates = listOfNotNull(
            providers.environmentVariable("OPENTV_VLC_PATH").orNull,
            "C:\\Program Files\\VideoLAN\\VLC",
            "C:\\Program Files (x86)\\VideoLAN\\VLC",
        )
        val vlcDir = candidates.firstOrNull { File(it, "libvlc.dll").isFile }
            ?: error(
                "VLC non trovato per il bundle: installa VLC 64 bit oppure imposta " +
                    "OPENTV_VLC_PATH alla cartella che contiene libvlc.dll",
            )
        from(vlcDir) {
            exclude("uninstall.exe")
        }
        into(layout.projectDirectory.dir("resources/windows/vlc"))
    }

    // Il task di prepare delle risorse compose attende il bundle copiato.
    tasks.matching { it.name.startsWith("prepareAppResources") }.configureEach {
        dependsOn("bundleVlc")
    }
}
