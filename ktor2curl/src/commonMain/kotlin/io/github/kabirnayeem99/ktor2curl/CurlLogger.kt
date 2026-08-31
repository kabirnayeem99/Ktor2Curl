package io.github.kabirnayeem99.ktor2curl

/**
 * Receives each rendered `curl` command. Implement this to decide what happens to it: print it,
 * forward it to a logging framework, ship it to a remote debug dashboard, or write it to a file.
 *
 * Set it as [KtorToCurlConfig.logger] when installing [KtorToCurl]:
 *
 * ```kotlin
 * install(KtorToCurl) {
 *     logger = object : CurlLogger {
 *         override suspend fun log(curl: String) = Log.d("API", curl)
 *     }
 * }
 * ```
 *
 * For plain stdout output, use the built-in [console] instead of implementing this yourself.
 */
interface CurlLogger {
    /** Called once per logged request with the fully rendered, ready-to-paste curl command. */
    suspend fun log(curl: String)

    companion object {
        /** Convenience [CurlLogger] that writes each rendered command to stdout via [println]. */
        fun console(): CurlLogger =
            object : CurlLogger {
                override suspend fun log(curl: String) = println(curl)
            }
    }
}
