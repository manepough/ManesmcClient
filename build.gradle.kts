plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace  = "com.project.manes"
    compileSdk = 35

    defaultConfig {
        applicationId   = "com.project.manes"
        minSdk          = 28
        targetSdk       = 35
        versionCode     = 1
        versionName     = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled   = false
            isShrinkResources = false
        }
        debug {
            isDebuggable = true
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.11" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {
    // ── Compose BOM ──────────────────────────────────────────────────────────
    val bom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ── Bedrock protocol + RakNet (same stack as LuminaClient) ───────────────
    implementation("org.cloudburstmc.protocol:bedrock-connection:3.0.0.Beta5-SNAPSHOT")
    implementation("org.cloudburstmc.netty:netty-transport-raknet:1.0.0.CR3-SNAPSHOT")
    implementation("io.netty:netty-all:4.1.109.Final")

    // ── JSON ─────────────────────────────────────────────────────────────────
    implementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
