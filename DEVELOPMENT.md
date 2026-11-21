# Development Guide

## Project Structure

The primary hook entry point is [MainHook.kt](app/src/main/java/io/github/chsbuffer/revancedxposed/MainHook.kt).

### Patch Organization and Conventions

Patches adhere to a specific structure:

```text
📦your.patches.app.category
 ├ Fingerprints.kt
 └ SomePatch.kt
```

-   **Project-specific patches:** [app/src/main/java/io/github/chsbuffer/revancedxposed](app/src/main/java/io/github/chsbuffer/revancedxposed)
-   **Extension code:** [app/src/main/java/app/revanced/extension](app/src/main/java/app/revanced/extension), copied from upstream [ReVanced Patches](https://github.com/ReVanced/revanced-patches) and edited in place. There is no submodule.

### Example: Patch Implementation (Contoso App)

#### `Fingerprints.kt`
```kotlin
package io.github.chsbuffer.revancedxposed.contoso.misc.unlock.plus

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType

val isPlusUnlockedFingerprint = fingerprint {
    returns("Z")
    strings("genius")
}
```

#### `UnlockPlusPatch.kt`
```kotlin
package io.github.chsbuffer.revancedxposed.contoso.misc.unlock.plus

import static de.robv.android.xposed.XC_MethodReplacement.returnConstant
import io.github.chsbuffer.revancedxposed.contoso.ContosoHook

fun ContosoHook.UnlockPlus() {
    ::isPlusUnlockedFingerprint.hookMethod(returnConstant(true))
}
```

#### `ContosoHook.kt`
```kotlin
package io.github.chsbuffer.revancedxposed.contoso

import android.app.Application
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.chsbuffer.revancedxposed.BaseHook
import io.github.chsbuffer.revancedxposed.contoso.misc.unlock.plus.UnlockPlus

class ContosoHook(app: Application, lpparam: XC_LoadPackage.LoadPackageParam) : BaseHook(
    app, lpparam
) {
    override val hooks = arrayOf(::UnlockPlus)
}
```

### Porting Upstream Patches

The [FingerprintCompat.kt](app/src/main/java/io/github/chsbuffer/revancedxposed/FingerprintCompat.kt) utility assists in translating ReVanced Patcher API calls to DexKit Matchers. While simple fingerprints can often be directly copied, consider the following translation patterns for complex cases:

1.  **Literal Mapping:**
    If an upstream patch defines a literal within the patch file (e.g., `aLiteral = resourceMappings["id", "aLiteral"]`) for use in a fingerprint, convert it to a getter property in the corresponding `Fingerprints.kt` file.

    *From (Upstream):*
    ```kotlin
    // In ***Patch.kt beside the Fingerprints.kt
    // val aLiteral = resourceMappings["id", "aLiteral"]
    fingerprint { literal { aLiteral } }
    ```

    *To (This Project's Fingerprints.kt):*
    ```kotlin
    val aLiteral get() = resourceMappings["id", "aLiteral"] // Defined in Fingerprints.kt
    fingerprint { literal { aLiteral } }
    ```

2.  **Custom Class and Method Matchers:**

    *From (Upstream):*
    ```kotlin
    fingerprint {
        custom { method, classDef ->
            method.name == "onCreate" && classDef.endsWith("/MusicActivity;")
        }
    }
    ```

    *To (This Project):*
    ```kotlin
    fingerprint {
        methodMatcher { name = "onCreate" }
        classMatcher { className(".MusicActivity", StringMatchType.EndsWith) }
    }
    ```

3.  **Instruction-based Method Reference:**

    *From (Upstream):*
    ```kotlin
    fun indexOfTranslationInstruction(method: Method) =
        method.indexOfFirstInstructionReversed {
            getReference<MethodReference>()?.name == "setTranslationY"
        }

    val motionEventFingerprint = fingerprint {
        custom { method, _ ->
            indexOfTranslationInstruction(method) >= 0
        }
    }
    ```

    *To (This Project):*
    ```kotlin
    val motionEventFingerprint = fingerprint {
        methodMatcher { addInvoke { name = "setTranslationY" } }
    }
    ```

4.  **Matching Specific Class Types or Defining Classes:**
    When an upstream `custom` block primarily checks `classDef.type` (the type of the matched class) or `method.definingClass` (the class defining the matched method), this translates to a `classMatcher` in this project. The `classMatcher` directly specifies the target class descriptor.

    *From (Upstream Example):*
    ```kotlin
    // Upstream: Using custom to check classDef.type
    fingerprint {
        custom { _, classDef ->
            classDef.type == "Lcom/example/SomeClass;"
        }
        // other matchers...
    }

    // Upstream: Using custom to check method.definingClass
    fingerprint {
        custom { method, _ ->
            method.definingClass == "Lcom/example/AnotherClass;"
        }
        // other method matchers...
    }
    ```
    *To (This Project's Fingerprints.kt Example):*
    ```kotlin
    // This Project: Using classMatcher for classDef.type
    fingerprint {
        classMatcher { descriptor = "Lcom/example/SomeClass;" }
        // methodMatcher { ... } // if needed for method properties
    }

    // This Project: Using classMatcher for method.definingClass
    fingerprint {
        // Targets methods within Lcom/example/AnotherClass;
        classMatcher { descriptor = "Lcom/example/AnotherClass;" }
        methodMatcher {
            // specific method properties, e.g., name = "targetMethod"
        }
    }
    ```
5.  **Porting Complex `custom` Logic or Chained Lookups with Direct Finders:**
    For intricate upstream fingerprints with complex `custom` logic or chained lookups not easily mapped to standard matchers, this project uses direct finder functions (e.g., `findMethodDirect`, `findClassDirect`) from `FingerprintCompat.kt`. This approach combines DexKit's efficient initial filtering with the power of Kotlin's collection processing for subsequent, more granular refinement.

    *From (Upstream Example with complex `custom` logic):*
    ```kotlin
    internal val complexCustomFingerprint = fingerprint {
        returns("Lcom/example/ReturnType;")
        custom { method, _ ->
            method.name.startsWith("get") &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes.first() == "Lcom/example/ParameterType;" &&
            method.definingClass == "Lcom/example/HostClass;"
            // ... potentially more complex conditions
        }
    }
    ```
    *To (This Project using `findMethodDirect`):*
    ```kotlin
    val complexCustomFingerprint = findMethodDirect {
        // Initial, broader filtering with DexKit matchers
        findMethod {
            matcher {
                returns("Lcom/example/ReturnType;")
                declaredClass { descriptor = "Lcom/example/HostClass;" }
                // Other simple matchers convertible from the original custom block
            }
        }
        // Refine results using Kotlin's collection functions for complex logic
        .filter { methodData -> methodData.name.startsWith("get") }
        .single { methodData -> // Assuming a unique result after all filters
            methodData.paramTypes.size == 1 &&
            methodData.paramTypes.firstOrNull()?.descriptor == "Lcom/example/ParameterType;"
            // ... other Kotlin-based checks
        }
    }
    ```
    This allows leveraging DexKit for initial efficient filtering, then applying precise Kotlin logic to the narrowed-down candidates.

### Extension Modules

As per ReVanced Patcher documentation:
> Instead of involving many abstract changes in one patch or writing entire methods or classes in a patch, you can write code in extensions.

Extension code lives in `app/src/main/java/app/revanced/extension/<appAlias>`, copied from upstream ReVanced Patches (`extensions/<appAlias>/src/main/java/app/revanced/extension/<appAlias>` there). Keep edits small so upstream changes stay easy to port.

Extension classes must load in debug builds too: Java `record`s don't (D8 references an undefined `com.android.tools.r8.RecordTag`), so write a final class. CI enforces this.

## Unit Testing

Refer to [FingerprintsKtTest.kt](app/src/test/java/io/github/chsbuffer/revancedxposed/FingerprintsKtTest.kt) for testing examples.

For running tests, place necessary APKs into the `./app/binaries/` directory. APK filenames should be prefixed with their respective package names (e.g., `com.example.app-1.0.0.apk`).

Run them with `./scripts/test-fingerprints.sh`. DexKit ships `libdexkit.so` for Android only, so on first run the script builds the host library (needs git, cmake, ninja, a C++20 compiler) and caches it. It prints one line per fingerprint: the match, an empty value when nothing matched, or `Skipping` when a `RequireAppVersion` bound excludes the APK.

## Porting to a New Spotify Version

The symptom is a toast or LSPosed log line `Error while apply following Hooks: <names>`. Work host-first: a DexKit query on the PC answers in seconds, a device round-trip costs a build and an install.

1.  **Pull the APK** under test into `app/binaries/` (the code is in `base.apk`):
    ```sh
    adb pull "$(adb shell pm path com.spotify.music | grep base.apk | cut -d: -f2)" app/binaries/com.spotify.music-<version>.apk
    ```
2.  **List the broken fingerprints**: `./scripts/test-fingerprints.sh`. Done when every fingerprint the failing hooks use is listed with an empty match.
3.  **Find the new shape** of each one. Decompile with `jadx --no-res --log-level error -d <out> <apk>` and grep for the strings, class names or callers the old fingerprint used. To check a candidate matcher, add a throwaway test under `app/src/test` that runs `DexKitBridge.create(apk).findMethod { ... }` and prints every result; the matcher is right when it returns exactly one method.
4.  **Fix the fingerprint** and keep the old one as fallback (`runCatching { old }.getOrElse { new }`, or `recoverCatching` for a chain). Bound fingerprints that only exist on one side of the change with `@get:RequireAppVersion(minVersion = ...)` / `maxVersion`, using all four version parts (`9.2.0.0`). Done when `./scripts/test-fingerprints.sh` ends in `BUILD SUCCESSFUL`.
5.  **Verify on device** with a debug build, then exercise the feature the hook targets (see below). Done when the log shows `apply hooks success` and the hook's own log line fires during use.

### Gotchas

-   **The method got inlined.** A hook on a small method never fires because ART inlined it into its callers. Match the wrapper (e.g. by its error string) and hook the larger method it calls; see `homeSectionMapperFingerprint`.
-   **The code path moved.** A fingerprint can still match a method the app no longer calls (9.2 builds the home from per-section mapping, not `HomeStructure.getSections`). If the hook installs but its log line never fires, find the live caller in the decompiled code.
-   **Behaviour moved inline.** When the logic you replaced with a constant is inlined into a bigger method, hook that method and fake the inner call with `scopedHook` instead; see `FixThirdPartyLaunchersWidgets`.
-   **A helper class failed to load.** `NoClassDefFoundError` is an `Error`, so `catch (Exception)` in extension code lets it through and the hook dies silently. Check the LSPosed log.

### Debugging on Device

-   **Install a debug build**: `./gradlew assembleUniversalDebug -PPACKAGE_NAME_SEED=<n>`, then `adb install -r app/build/outputs/apk/universal/debug/app-universal-debug.apk`. The package name derives from the seed, so reuse one seed to update in place; a new package must be enabled and scoped in LSPosed Manager. Debug builds skip the DexKit cache and turn on logging.
-   **Logcat, tag `revanced`**: fingerprint matches (`<name> Matches: ...`, `Fingerprint <name> Not Found`) and extension logs. Debug builds only: release strips `Logger` through ProGuard.
-   **LSPosed log** (`/data/adb/lspd/log/modules_*.log`, root): module loading, `Error while apply following Hooks`, and exceptions thrown inside hook callbacks, which never reach logcat.
-   **Restart the app** to reload hooks: `adb shell am force-stop com.spotify.music`, then launch it again.
