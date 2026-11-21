#!/usr/bin/env bash
# Run the fingerprint test (FingerprintsKtTest) against the APKs in app/binaries/.
# Builds the host libdexkit.so once (DexKit ships it for Android only) and caches it.
# Needs: git, cmake, ninja, a C++20 compiler, a JDK (JAVA_HOME or the one on PATH).
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"

version=$(sed -n 's/^dexkit = "\(.*\)"/\1/p' gradle/libs.versions.toml)
cache="${XDG_CACHE_HOME:-$HOME/.cache}/revancedxposed/dexkit-$version"

if [[ ! -f "$cache/libdexkit.so" ]]; then
    echo "Building host libdexkit.so $version into $cache"
    src=$(mktemp -d)
    trap 'rm -rf "$src"' EXIT
    git -c advice.detachedHead=false clone -q --depth 1 --recurse-submodules -b "$version" https://github.com/LuckyPray/DexKit "$src"
    cmake -S "$src/dexkit/src/main/cpp" -B "$src/build" -G Ninja \
        -DCMAKE_BUILD_TYPE=Release -DDISABLE_DEXKIT_LOG=ON >/dev/null
    ninja -C "$src/build" >/dev/null
    mkdir -p "$cache"
    cp "$src/build/libdexkit.so" "$cache/"
fi

if ! compgen -G "app/binaries/*.apk" >/dev/null; then
    echo "No APK in app/binaries/. Pull one from a device, named <package>-<version>.apk:" >&2
    echo '  adb pull "$(adb shell pm path com.spotify.music | grep base.apk | cut -d: -f2)" app/binaries/com.spotify.music-<version>.apk' >&2
    exit 1
fi

# Print one line per fingerprint (match, empty = not found, or the version skip) plus the verdict.
LD_LIBRARY_PATH="$cache${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
    ./gradlew testUniversalDebugUnitTest --rerun -i "$@" 2>&1 \
    | grep -E '^    [A-Z][A-Za-z]+: |^    Skipping:|^    org\.luckypray|FAILED$|tests completed|^BUILD|^e: '
