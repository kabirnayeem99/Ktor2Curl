package io.github.kabirnayeem99.ktor2curl

interface CurlLogger {
    suspend fun log(curl: String)
}
