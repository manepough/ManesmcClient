plugins {
    id("java-library")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

dependencies {
    // Lombok as annotation processor (avoids circular dependency)
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    
    api(libs.bundles.netty)
    api(libs.expiringmap)
    api(libs.network.common)
}
