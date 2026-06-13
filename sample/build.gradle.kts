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
}

kotlin {
    jvmToolchain(21)
}
