# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.1] - 2026-06-13

### Fixed

- **Serializable data-class bodies now render as JSON instead of `toString()`.**
  The plugin previously hooked `onRequest`, which fires *before* the request
  pipeline's Transform phase where `ContentNegotiation` serializes the body. At
  that point `request.body` was still the raw object, so a `@Serializable` body
  rendered as its Kotlin `toString()` (e.g. `BrowseBody(context=Context(...))`)
  rather than the JSON actually sent on the wire. The plugin now intercepts the
  send pipeline (`HttpSendPipeline.Monitoring`), after serialization, so the
  rendered `curl` shows the real serialized body.
- **`Content-Type` is no longer dropped for serialized bodies.**
  `ContentNegotiation` moves `Content-Type` off `request.headers` and onto the
  produced `TextContent`. The generator now falls back to the body's content type
  when no header is present, so `curl` includes `-H 'Content-Type: application/json'`
  as Ktor sends it. The fallback is scoped to `TextContent` — multipart keeps
  managing its own `Content-Type` via curl's `-F` (an explicit header with a stale
  boundary would break the request).

### Changed

- Capturing the request after serialization means the rendered `curl` now also
  reflects Ktor's default headers (e.g. `Accept: */*`, and `Accept: application/json`
  added by `ContentNegotiation`), matching what is actually transmitted.

### Internal

- `scripts/ci-local.sh`: fixed task word-splitting so it runs correctly under `zsh`
  (`zsh scripts/ci-local.sh`), not only via the `/bin/sh` shebang.
- Added test-only dependencies (`ktor-client-content-negotiation`,
  `ktor-serialization-kotlinx-json`) and the Kotlin serialization plugin to cover
  the data-class body path end-to-end with a real `ContentNegotiation` + `MockEngine`.

## [2.0.0] - 2025

### Added

- Initial 2.x release: comprehensive `curl` generation — header handling,
  masking/exclusion, multipart (`-F`) rendering, body truncation, multi-shell
  quoting (POSIX / PowerShell / Windows CMD), and the `KtorToCurl` Ktor client
  plugin. KMP targets: `android`, `jvm`, `iosX64`, `iosArm64`, `iosSimulatorArm64`.

[2.0.1]: https://github.com/kabirnayeem99/Ktor2Curl/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/kabirnayeem99/Ktor2Curl/releases/tag/v2.0.0
