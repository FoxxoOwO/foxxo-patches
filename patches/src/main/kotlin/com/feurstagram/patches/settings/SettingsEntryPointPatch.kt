package com.feurstagram.patches.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val SETTINGS_CLASS = "Lcom/feurstagram/extension/Settings;"

private fun fieldType(instruction: Any?): String? =
    ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.type

private fun hasTabBarIdLiteral(method: com.android.tools.smali.dexlib2.iface.Method): Boolean {
    val instructions = method.implementation?.instructions ?: return false
    for (ins in instructions) {
        if (ins is com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction && ins.narrowLiteral == 0x7f0b3fd3) return true
        if (ins is com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction && ins.wideLiteral == 0x7f0b3fd3L) return true
    }
    return false
}

// The main tab-bar binder is an obfuscated class whose constructor takes the
// tab-bar root View, pulls the tab_bar ViewGroup child out of it and stashes it
// in a field, alongside a sibling View field. We match that shape rather than
// the obfuscated names, which Instagram reshuffles between releases.
internal object TabBarBinderFingerprint : Fingerprint(
    name = "<init>",
    parameters = listOf("Landroid/view/View;"),
    custom = { method, classDef ->
        classDef.type.startsWith("LX/") &&
            (classDef.fields.any { it.type.contains("HomecomingEscapeHatchView") } || hasTabBarIdLiteral(method)) &&
            method.implementation?.instructions?.let { instructions ->
                var hasViewGroupField = false
                var hasViewField = false
                for (instruction in instructions) {
                    if (instruction.opcode == Opcode.IPUT_OBJECT) {
                        when (fieldType(instruction)) {
                            "Landroid/view/ViewGroup;" -> hasViewGroupField = true
                            "Landroid/view/View;" -> hasViewField = true
                        }
                    }
                }
                hasViewGroupField && hasViewField
            } == true
    },
)

/**
 * Opens the Feurstagram settings on a long-press of the Home tab and installs the surface
 * hiders and the update check.
 */
internal fun BytecodePatchContext.installSettingsEntryPoint() {
    TabBarBinderFingerprint.method.apply {
            // The value stored into the ViewGroup field is the tab_bar root; it's
            // a stable handle into the window, from which the watcher attaches the
            // long-press settings listener to the Home tab and installs the hiders.
            val tabBarStore = instructions.first {
                it.opcode == Opcode.IPUT_OBJECT && fieldType(it) == "Landroid/view/ViewGroup;"
            }
            val tabBarRegister = (tabBarStore as TwoRegisterInstruction).registerA

            addInstructions(
                tabBarStore.location.index + 1,
                "invoke-static { v$tabBarRegister }, " +
                    "$SETTINGS_CLASS->installHomeTabWatcher(Landroid/view/ViewGroup;)V",
            )
        }
}
