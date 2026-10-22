package com.feurstagram.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    /** Root descriptor of the compiled extension classes. */
    const val EXTENSION_PACKAGE = "Lcom/feurstagram/extension"

    /** Path to the compiled extension bundle, shared by every patch. */
    const val EXTENSION = "extensions/extension.mpe"

    /** Instagram build the patch set is developed and verified against. */
    const val INSTAGRAM_VERSION = "447.0.0.55.81"

    val COMPATIBILITY_INSTAGRAM = Compatibility(
        name = "Instagram",
        packageName = "com.instagram.android",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFC483C,
        // INSTAGRAM_VERSION is the exact build the patches were tested on; the
        // version = null target keeps them applying across future Instagram updates.
        targets = listOf(
            AppTarget(version = INSTAGRAM_VERSION),
            AppTarget(version = null),
        ),
    )

    /**
     * Version-agnostic companion for the meta patches (Clone, Debug bridge): they
     * only rename the package / expose the settings store, so they must stay
     * usable on any Instagram build instead of being tied to [INSTAGRAM_VERSION].
     */
    val COMPATIBILITY_INSTAGRAM_ANY = Compatibility(
        name = "Instagram",
        packageName = "com.instagram.android",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFC483C,
        targets = listOf(
            AppTarget(version = null),
        ),
    )
}
