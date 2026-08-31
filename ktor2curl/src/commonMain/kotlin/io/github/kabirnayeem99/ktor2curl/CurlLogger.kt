package io.github.kabirnayeem99.ktor2curl

interface CurlLogger {
    suspend fun log(curl: String)

    companion object {
        /** Convenience [CurlLogger] that writes each rendered command to stdout via [println]. */
        fun console(): CurlLogger =
            object : CurlLogger {
                override suspend fun log(curl: String) = println(curl)
            }
    }
}
