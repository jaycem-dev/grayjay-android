#!/usr/bin/env bash
# Builds a Grayjay APK inside the nix environment from shell.nix.
#
# Usage:
#   ./scripts/build-apk.sh                     # unstable flavor, debug build
#   ./scripts/build-apk.sh stable debug        # stable flavor, debug build
#   ./scripts/build-apk.sh unstable release    # unstable flavor, release build (keystore required)
#
# Note: requires an x86_64-linux host (Google ships x86_64-only Linux build tools).
set -euo pipefail
cd "$(dirname "$0")/.."

variant="${1:-unstable}"
build="${2:-debug}"
case "$variant" in stable|unstable|playstore) ;; *) echo "Unknown variant: $variant"; exit 1;; esac
case "$build" in debug|release) ;; *) echo "Unknown build type: $build"; exit 1;; esac

if [ "$(uname -m)" != "x86_64" ]; then
    echo "Error: building requires an x86_64-linux host (Google's build tools are x86_64-only)." >&2
    exit 1
fi

# Re-exec inside the Nix shell unless we are already in one.
if [ -z "${IN_NIX_SHELL:-}" ] || [ -z "${ANDROID_SDK_ROOT:-}" ]; then
    exec nix-shell shell.nix --run "$PWD/scripts/build-apk.sh $variant $build"
fi

# .gitmodules uses URLs relative to the canonical GitLab repository. When the
# fork is cloned from GitHub those relative URLs resolve to GitHub repos that
# do not exist, so point each submodule at the canonical GitLab repo in the
# local git config instead of modifying the tracked .gitmodules file.
canonical_base="https://gitlab.futo.org/videostreaming"
while read -r key _; do
    name=${key#submodule.}
    name=${name%.path}
    url=$(git config -f .gitmodules --get "submodule.$name.url")
    case "$url" in
        ../*) url="$canonical_base/${url#../}" ;;
    esac
    git config --local "submodule.$name.url" "$url"
done < <(git config -f .gitmodules --get-regexp '^submodule\..*\.path$')

git submodule update --init --recursive --depth 1

# The ffmpeg-kit AAR is stored in Git LFS. GitHub does not have the object,
# so fetch it from the canonical repository if we still have the pointer.
aar="app/aar/ffmpeg-kit.aar"
if [ -f "$aar" ] && grep -q '^version https://git-lfs.github.com/spec/v1' "$aar"; then
    if ! command -v git-lfs >/dev/null 2>&1; then
        echo "Error: $aar is a Git LFS pointer but git-lfs is not installed." >&2
        exit 1
    fi
    git lfs install --local >/dev/null
    git lfs fetch --all "$canonical_base/grayjay.git"
    git lfs checkout
fi

task=":app:assemble${variant^}${build^}"
exec ./gradlew "$task" --console=plain
