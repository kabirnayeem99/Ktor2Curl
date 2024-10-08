package io.github.kabirnayeem99.ktor2curl

class KtorToCurlConfig {
    var converter: CurlLogger = object : CurlLogger {
        override fun log(curl: String) = Unit
    }
    var excludedHeaders: Set<String> = emptySet()
    var maskedHeaders: Set<String> = emptySet()
}