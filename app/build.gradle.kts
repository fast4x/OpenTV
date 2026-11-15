plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// STEP OT-2 (workflow release): versione iniettabile dalla CI.
// Il workflow .github/workflows/build-release.yml passa:
//   ./gradlew :app:assembleRelease -Popentv.versionName=1.2.3-rc.1
// (tag v1.2.3-rc.1 senza "v" iniziale). Senza property resta "1.0.0"
// (build locali invariate). versionCode resta FISSO a 1: scelta esplicita
// (nessun versionCode incrementale iniettato dalla CI; alzare manualmente
// quando si vuole supportare l'aggiornamento sopra installazioni precedenti).
// ---------------------------------------------------------------------------
val opentvVersionName: String =
    (findProperty("opentv.versionName") as? String)?.takeIf { it.isNotBlank() } ?: "1.0.1"

android {
    namespace = "it.fast4x.opentv"
    compileSdk = 37

    defaultConfig {
        applicationId = "it.fast4x.opentv"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = opentvVersionName
        vectorDrawables { useSupportLibrary = true }
    }

    // ---------------------------------------------------------------------
    // STEP OT-1 (workflow release): firma release con la chiave CONDIVISA
    // degli altri progetti, registrata come secret GitHub. Le 4 env vengono
    // impostate dal workflow build-release.yml (decommentate del base64:
    // KEYSTORE_BASE64 -> file .jks in RUNNER_TEMP):
    //   SIGNING_STORE_FILE, SIGNING_STORE_PASSWORD,
    //   SIGNING_KEY_ALIAS,  SIGNING_KEY_PASSWORD
    // In locale, senza env, la configurazione resta vuota: nessun errore,
    // assembleRelease produce semplicemente un APK unsigned.
    // ---------------------------------------------------------------------
    signingConfigs {
        create("release") {
            val storeFileEnv = System.getenv("SIGNING_STORE_FILE")
            val storePasswordEnv = System.getenv("SIGNING_STORE_PASSWORD")
            val keyAliasEnv = System.getenv("SIGNING_KEY_ALIAS")
            val keyPasswordEnv = System.getenv("SIGNING_KEY_PASSWORD")
            if (storeFileEnv != null && storePasswordEnv != null &&
                keyAliasEnv != null && keyPasswordEnv != null
            ) {
                storeFile = file(storeFileEnv)
                storePassword = storePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Firma SOLO quando le env di firma sono presenti (CI release);
            // in locale il buildType resta senza signingConfig (unsigned).
            if (System.getenv("SIGNING_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    dependenciesInfo {
        // Rimuove il blocco "Dependency metadata" dal APK Signing Block
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // Logica condivisa Kotlin Multiplatform (parser, repository, preferiti, EPG)
    implementation(project(":core"))

    // Core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)

    // Immagini (la rete — OkHttp/DataStore — arriva dal modulo :core)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
