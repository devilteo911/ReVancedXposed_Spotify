package io.github.chsbuffer.revancedxposed.spotify.misc.widgets

import android.content.pm.PackageManager
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.scopedHook
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook

fun SpotifyHook.FixThirdPartyLaunchersWidgets() {
    ::canBindAppWidgetPermissionFingerprint.memberOrNull?.let {
        XposedBridge.hookMethod(it, returnConstant(true))
        return
    }

    ::widgetProvidersMediaAccessFlagFingerprint.hookMethod(returnConstant(true))
    ::mediaProviderOpenFileFingerprint.hookMethod(
        scopedHook(
            XposedHelpers.findMethodExact(
                "android.app.ApplicationPackageManager",
                classLoader,
                "checkPermission",
                String::class.java,
                String::class.java
            )
        ) {
            before { param ->
                if (param.args[0] == BIND_APPWIDGET) param.result = PackageManager.PERMISSION_GRANTED
            }
        })
}
