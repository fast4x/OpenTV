plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
}

// Modulo shared di logica (Kotlin Multiplatform):
// - androidTarget: consumato dall'app Android;
// - jvm: consumato dall'app desktop (Compose Multiplatform).
// Tutti i target sono JVM, quindi commonMain può usare API Java/JVM
// (java.io.File, OkHttp, coroutines Dispatchers.IO) su entrambe.
kotlin {
    androidTarget()

    jvm()

    // JVM 17 uniforme su tutti i target, come l'app Android.
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okhttp)
            // DataStore multiplatform (preferences-core): API identica su
            // Android e desktop, file DataStore iniettato dal chiamante.
            implementation(libs.androidx.datastore.preferences.core)
            // XmlPull per XmltvParser: su Android l'implementazione è nella
            // piattaforma (android.jar). In commonMain usiamo compileOnly per
            // non includere kxml2 nel pacchetto Android (evita conflitti R8 con org.xmlpull).
            compileOnly(libs.kxml2)
        }
        jvmMain.dependencies {
            // Su JVM desktop org.xmlpull non è nel JDK standard: serve kxml2 a runtime.
            implementation(libs.kxml2)
        }
    }
}

android {
    namespace = "it.fast4x.opentv.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
