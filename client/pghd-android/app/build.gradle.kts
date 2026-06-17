plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

fun loadEnv(): Map<String, String> {
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) return emptyMap()
    return envFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate {
            val key = it.substringBefore("=").trim()
            val value = it.substringAfter("=").trim().trim('"')
            key to value
        }
}

val env = loadEnv()
fun envString(name: String, defaultValue: String = ""): String =
    env[name] ?: System.getenv(name) ?: defaultValue

android {
    namespace = "com.hackastic.decmed"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.hackastic.decmed"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PRE_BASE_URL", "\"${envString("PRE_BASE_URL")}\"")
        buildConfigField("String", "IOTA_RPC_URL", "\"${envString("IOTA_RPC_URL")}\"")
        buildConfigField("String", "GAS_STATION_BASE_URL", "\"${envString("GAS_STATION_BASE_URL")}\"")
        buildConfigField("String", "GAS_STATION_TOKEN", "\"${envString("GAS_STATION_TOKEN", "token")}\"")
        buildConfigField("String", "DECMED_PACKAGE_ID", "\"${envString("DECMED_PACKAGE_ID")}\"")
        buildConfigField("String", "DECMED_ADDRESS_ID_OBJECT_ID", "\"${envString("DECMED_ADDRESS_ID_OBJECT_ID")}\"")
        buildConfigField("long", "DECMED_ADDRESS_ID_OBJECT_VERSION", envString("DECMED_ADDRESS_ID_OBJECT_VERSION", "0"))
        buildConfigField("String", "DECMED_HOSPITAL_ID_METADATA_OBJECT_ID", "\"${envString("DECMED_HOSPITAL_ID_METADATA_OBJECT_ID")}\"")
        buildConfigField("long", "DECMED_HOSPITAL_ID_METADATA_OBJECT_VERSION", envString("DECMED_HOSPITAL_ID_METADATA_OBJECT_VERSION", "0"))
        buildConfigField("String", "DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_ID", "\"${envString("DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_ID")}\"")
        buildConfigField("long", "DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_VERSION", envString("DECMED_HOSPITAL_PERSONNEL_ID_ACCOUNT_OBJECT_VERSION", "0"))
        buildConfigField("String", "DECMED_PATIENT_ID_ACCOUNT_OBJECT_ID", "\"${envString("DECMED_PATIENT_ID_ACCOUNT_OBJECT_ID")}\"")
        buildConfigField("long", "DECMED_PATIENT_ID_ACCOUNT_OBJECT_VERSION", envString("DECMED_PATIENT_ID_ACCOUNT_OBJECT_VERSION", "0"))
        buildConfigField("String", "DECMED_HASH_SALT", "\"${envString("DECMED_HASH_SALT", "169224A2BE2B267684F93A9CE38080D359BD774741FD3AE738D09B657A1A8104")}\"")
        buildConfigField("long", "IOTA_GAS_BUDGET", envString("IOTA_GAS_BUDGET", "10000000"))
        buildConfigField("long", "IOTA_GAS_RESERVE_NANOS", envString("IOTA_GAS_RESERVE_NANOS", "2000000000"))
        buildConfigField("long", "IOTA_GAS_RESERVE_SECONDS", envString("IOTA_GAS_RESERVE_SECONDS", "10"))
        buildConfigField("long", "PGHD_BATCH_INTERVAL_MINUTES", envString("PGHD_BATCH_INTERVAL_MINUTES", "15"))
        buildConfigField("long", "PGHD_EARLY_TRIGGER_BYTES", envString("PGHD_EARLY_TRIGGER_BYTES", "10485760"))
        buildConfigField("long", "PGHD_DEFAULT_SYNC_DAYS", envString("PGHD_DEFAULT_SYNC_DAYS", "30"))
        buildConfigField("long", "PGHD_HISTORY_SYNC_DAYS", envString("PGHD_HISTORY_SYNC_DAYS", "365"))
        buildConfigField("int", "PGHD_SENSOR_BATCH_SIZE", envString("PGHD_SENSOR_BATCH_SIZE", "100"))
        buildConfigField("int", "PGHD_DEFAULT_SENSOR_INTERVAL_MS", envString("PGHD_DEFAULT_SENSOR_INTERVAL_MS", "60000"))
        buildConfigField("String", "PGHD_SENSOR_INTERVAL_OPTIONS_MS", "\"${envString("PGHD_SENSOR_INTERVAL_OPTIONS_MS", "60000,900000")}\"")
        buildConfigField("String", "PGHD_DEFAULT_TEST_VECTOR_MNEMONIC", "\"${envString("PGHD_DEFAULT_TEST_VECTOR_MNEMONIC")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
        }
    }

}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("rustls:rustls-platform-verifier:latest.release")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation, ViewModel, DataStore
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.material.icons.extended)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Health Connect
    implementation(libs.health.connect.client)

    // QR decoding for hospital personnel access grants
    implementation(libs.zxing.core)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Background work
    implementation(libs.work.runtime.ktx)
}
