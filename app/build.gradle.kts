import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use(::load)
    }
}

fun signingValue(propertyName: String, envName: String): String? {
    val fromFile = keystoreProperties.getProperty(propertyName)?.trim().orEmpty()
    if (fromFile.isNotEmpty()) return fromFile
    val fromEnv = System.getenv(envName)?.trim().orEmpty()
    return fromEnv.ifEmpty { null }
}

val releaseStoreFile = signingValue("storeFile", "ERV_RELEASE_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "ERV_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "ERV_RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "ERV_RELEASE_KEY_PASSWORD")
val hasReleaseSigning =
    !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.erv.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.erv.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    buildFeatures {
        buildConfig = false
        compose = true
    }
}

// Android Gradle Plugin does not create the Java plugin's `testClasses` task; some IDE actions still request
// `:app:testClasses`. Wire it to unit test compilation so those builds succeed.
tasks.register("testClasses") {
    group = "verification"
    description = "Compiles debug unit test sources (Java plugin lifecycle parity)."
    dependsOn("compileDebugUnitTestKotlin")
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Jetpack Compose (plan §5.0)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Barcode scan (supplements): CameraX + ML Kit
    val cameraVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // DataStore (user preferences: theme, etc.)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Nostr WebSocket (OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // secp256k1 for Schnorr signing + key derivation (plan §6, NIP-42)
    implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-android:0.15.0")

    // Crypto: ChaCha20-Poly1305 for NIP-44 on all API levels
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")

    // Secure key storage (plan §6.1)
    implementation("androidx.security:security-crypto:1.0.0")

    testImplementation("junit:junit:4.13.2")
}
