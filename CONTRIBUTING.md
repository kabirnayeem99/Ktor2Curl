# Contributing

1. Fork and clone:
   ```bash
   git clone https://github.com/YOUR_USERNAME/Ktor2Curl.git
   cd Ktor2Curl
   git remote add upstream https://github.com/kabirnayeem99/Ktor2Curl.git
   ```
2. Branch: `git checkout -b feature/your-idea`
3. Install git hooks (one-time per clone): `git config core.hooksPath .githooks`
4. Add tests under `ktor2curl/src/commonTest/` for any behavior change.
5. Run tests:
   ```bash
   ./gradlew :ktor2curl:jvmTest --console=plain               # fast, JVM only
   ./gradlew :ktor2curl:jvmTest :ktor2curl:testDebugUnitTest :ktor2curl:iosSimulatorArm64Test  # full gate
   ```
6. Format before committing: `./gradlew ktlintFormat` (the pre-commit hook also runs this, but does
   not auto-stage the result, so review and `git add` any reformatted files).
7. Open a PR with a clear description of what changed and why.

See [`docs/API.md`](docs/API.md) for a full reference of every `CurlOptions` /
`KtorToCurlConfig` field, and [`CLAUDE.md`](CLAUDE.md) for the project's internal layout and build
notes.
