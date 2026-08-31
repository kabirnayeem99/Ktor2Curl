import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    id("com.vanniktech.maven.publish") version "0.33.0"
}

kotlin {
    androidTarget {
        tasks.withType<KotlinJvmCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
            }
        }
    }

    // JVM target so non-Android consumers (the :sample console app, server-side Ktor) can use the
    // plugin directly, and so commonTest also runs on the fast `jvmTest` task.
    jvm()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "ktor2curl"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
        }
    }
}

android {
    namespace = "io.github.kabirnayeem99.ktor2curl"
    compileSdk = 35
    defaultConfig {
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
dependencies {
    implementation(libs.kotlinx.coroutines.core)
}

// Kover only instruments the JVM-backed source sets (commonMain + jvmMain) for a KMP module, which
// is exactly what jvmTest already exercises — see the "fastest test gate" note in CLAUDE.md. The
// `total` report variant merges the plain jvm() variant with the Android variant so both count
// toward one figure. Run `./gradlew :ktor2curl:koverHtmlReportTotal` for a browsable gap report.
kover {
    reports {
        total {
            verify {
                rule {
                    minBound(100)
                }
            }
        }
    }
}

// Module.md supplies the module/package overview shown on the generated docs' front page
// (and on javadoc.io, once a release with real Dokka output ships) instead of Dokka's blank default.
dokka {
    dokkaSourceSets.configureEach {
        includes.from("Module.md")
    }
}

mavenPublishing {
    coordinates(
        groupId = "io.github.kabirnayeem99",
        artifactId = "ktor2curl",
        version = "3.0.1",
    )

    pom {
        name.set("ktor2curl")
        description.set(
            "Ktor2Curl is a Ktor client plugin that seamlessly converts HTTP requests into cURL commands for debugging and logging.",
        )
        inceptionYear.set("2024")
        url.set("https://github.com/kabirnayeem99/Ktor2Curl")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("kabirnayeem99")
                name.set("Naimul Kabir")
                email.set("kabirnayeem.99@gmail.com")
            }
        }

        scm {
            url.set("https://github.com/kabirnayeem99/Ktor2Curl")
        }
    }

    publishToMavenCentral()
    // Configure publishing to Maven Central
//    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    // Enable GPG signing for all publications
    signAllPublications()
}
