plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

application {
    mainClass.set("io.github.kabirnayeem99.ktor2curl.sample.MainKt")
}

dependencies {
    implementation(project(":ktor2curl"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.mock)
    implementation(libs.kotlinx.coroutines.core)
    // Ktor's client logs via SLF4J; provide a no-op binding so the sample's
    // stdout stays clean (otherwise SLF4J prints "no providers found" warnings).
    runtimeOnly(libs.slf4j.nop)
}

kotlin {
    jvmToolchain(21)
}
