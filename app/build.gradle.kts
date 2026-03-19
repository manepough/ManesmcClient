plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.project.manes"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.project.manes"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes { debug { isDebuggable = true } }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources {
            excludes += setOf("META-INF/INDEX.LIST","META-INF/io.netty.versions.properties","META-INF/AL2.0","META-INF/LGPL2.1")
        }
    }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.cloudburstmc.protocol:bedrock-connection:3.0.0.Beta5-SNAPSHOT")
    implementation("org.cloudburstmc.netty:netty-transport-raknet:1.0.0.CR3-SNAPSHOT")
    implementation("io.netty:netty-all:4.1.97.Final")
    implementation("org.json:json:20231013")
}
