# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.1] - 2026-08-31

### Added

- KDoc across the public API: the `KtorToCurl` plugin, `CurlLogger`, `KtorToCurlConfig`, and
  `CurlOptions` all previously had little or no documentation on their main entry points.
- Real Dokka-generated API docs. The javadoc.jar published to Maven Central for `3.0.0` was empty
  (no Dokka configured); this release wires up Dokka so javadoc.io renders actual documentation,
  with `Module.md` as the landing-page overview.
- `docs/API.md`: a runnable example per `KtorToCurlConfig` / `CurlOptions` option.
- `CONTRIBUTING.md`, split out of the README.

### Changed

- README trimmed down, with usage details moved to `docs/API.md`.

## [3.0.0] - 2026-08-31

### Breaking

- **`KtorToCurlConfig.converter` renamed to `logger`, and `CurlLogger.log` is now
  `suspend`.** Existing plugin configs must rename `converter = ...` to
  `logger = ...`, and custom `CurlLogger` implementations must mark `log`
  `suspend` (it may now do async work, e.g. writing to disk or a network sink,
  without blocking the send pipeline).
- Truncated/binary/streaming bodies now render via `--data-binary` instead of
  `-d` for every text body, not only the previously-covered cases — `-d`
  silently strips CR/LF, which would desync a multi-line body from what was
  actually sent; `--data-binary` sends it byte-for-byte.

### Added

- `CurlLogger.console()` — convenience factory for a logger that writes to
  stdout via `println`.
- `CurlOptions.maxMultipartBytes` / `KtorToCurlConfig.maxMultipartBytes` — caps
  how much of a multipart body is materialized for rendering (default 1 MiB);
  larger bodies render as `[multipart body omitted]` instead of risking OOM on
  a multi-megabyte upload.

### Fixed

- **`FormDataContent` (Ktor's `application/x-www-form-urlencoded` API) no
  longer renders as a garbled object reference.** The body-type check matched
  only the concrete `ByteArrayContent` class, not the abstract
  `OutgoingContent.ByteArrayContent` that `FormDataContent` extends directly,
  so it fell through to a raw `toString()`. Widened to catch any
  `OutgoingContent.ByteArrayContent` implementer.
- **Credential masking no longer leaks the raw URL when it fails to re-parse.**
  If `URLBuilder` couldn't re-parse a malformed URL, `maskUrl` fell back to
  returning it completely unmasked — defeating the point of masking exactly
  when it mattered most. It now falls back to regex-based best-effort
  redaction of `user:pass@` credentials and `maskedQueryParams` instead.

### Internal

- Added ~53 new tests across four new files (content-type/API-surface
  coverage, internationalization, adversarial/robustness edge cases, and
  scale/lifecycle behavior), plus Kover coverage tooling.

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

[3.0.1]: https://github.com/kabirnayeem99/Ktor2Curl/compare/v3.0.0...v3.0.1
[3.0.0]: https://github.com/kabirnayeem99/Ktor2Curl/compare/v2.0.1...v3.0.0
[2.0.1]: https://github.com/kabirnayeem99/Ktor2Curl/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/kabirnayeem99/Ktor2Curl/releases/tag/v2.0.0
