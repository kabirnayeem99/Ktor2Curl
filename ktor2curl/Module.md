# Module ktor2curl

Ktor client plugin that renders outgoing HTTP requests as runnable `curl` commands, for logging
and debugging.

Start with [io.github.kabirnayeem99.ktor2curl.KtorToCurl] to install the plugin, or
[io.github.kabirnayeem99.ktor2curl.toCurl] for a one-off render without installing anything.
[io.github.kabirnayeem99.ktor2curl.KtorToCurlConfig] lists every configuration option (masking,
excluded headers, shell dialect, truncation, timeouts).

See the [project README](https://github.com/kabirnayeem99/Ktor2Curl) for install instructions and
usage examples.

# Package io.github.kabirnayeem99.ktor2curl

Public API: the [io.github.kabirnayeem99.ktor2curl.KtorToCurl] plugin, its
[io.github.kabirnayeem99.ktor2curl.KtorToCurlConfig] configuration block, the
[io.github.kabirnayeem99.ktor2curl.CurlLogger] sink interface, and the
[io.github.kabirnayeem99.ktor2curl.toCurl] extension function for use without the plugin.
