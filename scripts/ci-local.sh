#!/bin/sh
# ci-local.sh — run as much of the GitHub Actions CI as possible on this machine, no Docker.
#
# Mirrors .github/workflows/ci.yml:
#   job "jvm-android" (ubuntu): ktlintCheck  +  :ktor2curl:jvmTest :ktor2curl:testDebugUnitTest
#   job "ios"        (macos) : :ktor2curl:iosSimulatorArm64Test
#
# The iOS job runs ONLY on macOS — the Darwin klib can't be built elsewhere, so it is
# skipped (not failed) on Linux/other hosts, exactly as CI splits it across runners.
# publish.yml is NOT mirrored: it needs Maven Central + GPG secrets and pushes artifacts.
#
# Usage:
#   ./scripts/ci-local.sh            # run everything applicable to this host
#   ./scripts/ci-local.sh --no-ios   # force-skip the iOS job even on macOS (faster)
#   SKIP_IOS=1 ./scripts/ci-local.sh # same, via env var (used by the pre-push hook escape hatch)
set -e

ROOT="$(git rev-parse --show-toplevel)"
GRADLEW="$ROOT/gradlew"

# --- arg / env parsing -------------------------------------------------------
for arg in "$@"; do
    case "$arg" in
        --no-ios) SKIP_IOS=1 ;;
        -h|--help)
            sed -n '2,15p' "$0"
            exit 0
            ;;
        *)
            echo "[ci-local] unknown argument: $arg" >&2
            exit 2
            ;;
    esac
done

# --- decide the iOS job ------------------------------------------------------
RUN_IOS=0
if [ "$(uname)" = "Darwin" ] && [ -z "$SKIP_IOS" ]; then
    RUN_IOS=1
fi

# --- assemble the Gradle task list (single invocation = one daemon, faster) --
# ktlintCheck first so a lint failure aborts before the slower test compile.
TASKS="ktlintCheck :ktor2curl:jvmTest :ktor2curl:testDebugUnitTest"
if [ "$RUN_IOS" = "1" ]; then
    TASKS="$TASKS :ktor2curl:iosSimulatorArm64Test"
else
    if [ "$(uname)" = "Darwin" ]; then
        echo "[ci-local] iOS job skipped (--no-ios / SKIP_IOS set)."
    else
        echo "[ci-local] iOS job skipped — not macOS; CI runs it on its own macos-latest runner."
    fi
fi

echo "[ci-local] host=$(uname)  tasks: $TASKS"
"$GRADLEW" $TASKS --console=plain
echo "[ci-local] CI checks passed locally."
