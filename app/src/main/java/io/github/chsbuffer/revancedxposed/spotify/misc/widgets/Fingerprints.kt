package io.github.chsbuffer.revancedxposed.spotify.misc.widgets

import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.RequireAppVersion
import io.github.chsbuffer.revancedxposed.fingerprint

const val BIND_APPWIDGET = "android.permission.BIND_APPWIDGET"

@get:RequireAppVersion(maxVersion = "9.2.0.0")
val canBindAppWidgetPermissionFingerprint = fingerprint {
    strings(BIND_APPWIDGET)
    opcodes(Opcode.AND_INT_LIT8)
}

// 9.2+: the permission check is inlined into MediaProvider.openFile ...
@get:RequireAppVersion(minVersion = "9.2.0.0")
val mediaProviderOpenFileFingerprint = fingerprint {
    name("openFile")
    strings(BIND_APPWIDGET)
}

// ... and only runs when this remote flag is enabled.
@get:RequireAppVersion(minVersion = "9.2.0.0")
val widgetProvidersMediaAccessFlagFingerprint = fingerprint {
    returns("Z")
    parameters()
    classMatcher { usingStrings("allow_widget_providers_media_provider_access") }
}
