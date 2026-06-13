plugins {
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.kotlinJvm).apply(false)
    alias(libs.plugins.ktlint)
}

// Apply ktlint to every project so `ktlintCheck` lints all Kotlin sources (library + sample modules)
// plus the root/build `*.gradle.kts` script files in one pass.
allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
