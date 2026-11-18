package com.feurstagram.patches.ascent

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_ASCENT

/*
 * Ascent models the subscription as a `gk4` status object, and every premium gate in the UI is
 * `premiumStatus instanceof ck4` (ck4 = "is premium"). The status is produced in three places
 * that have to agree, otherwise the UI flips back to the free state a moment after launch:
 *
 *  - g90.b(Jjc0;)  - the server answer handler. `jc0.a == 0` means "subscribed"; the handler maps
 *                    that to `bk4.a` (premium) or `dk4.a` (free), caches it in `Lbj4;` and pushes
 *                    it into the status StateFlow `g90.i`.
 *  - v00.invokeSuspend - the coroutine lambda that maps the locally known purchase state (`nk2`),
 *                    the "premium features enabled" flag and the focus-session state (`lk4`) into
 *                    the same status. It is used on the very first launch, before the server has
 *                    answered, so the UI would briefly render the free state without it.
 *
 * Both are pinned to `bk4.a`, the plain "premium" status (`fk4` is a promotion, `dk4` free,
 * `ek4` expired). The premium-feature settings list is separate state and is left untouched.
 *
 * Repackaging the APK invalidates the Play license shipped with PairIP
 * (`com.pairip.application.Application` -> `LicenseClient.checkLicense`), which otherwise raises
 * the "not licensed" path and kills the process after 30 seconds, so that check is disabled too.
 */

/** Final status object used by every premium gate: `bk4` implements `ck4`. */
private const val PREMIUM_STATUS = "Lbk4;"

/** Free status object (`dk4`), the value the server mapping must never produce. */
private const val FREE_STATUS = "Ldk4;"

/** Server answer handler of the subscription repository. */
private const val STATUS_REPOSITORY = "Lg90;"

/** Coroutine lambda that maps the local purchase state into a status. */
private const val STATUS_MAPPER = "Lv00;"

/** PairIP Play licensing client. */
private const val LICENSE_CLIENT = "Lcom/pairip/licensecheck/LicenseClient;"

/**
 * Forces every source of the subscription status to report "premium" and stops PairIP from
 * terminating a re-signed build.
 */
internal fun BytecodePatchContext.unlockPremium() {
    forceSubscribedServerStatus()
    forcePremiumLocalStatus()
    disablePairIpLicenseCheck()
}

/** `g90.b`: both branches of the `do4 ? bk4.a : dk4.a` mapping return the premium status. */
private fun BytecodePatchContext.forceSubscribedServerStatus() {
    val method = mutableClassDefBy(STATUS_REPOSITORY).methods.singleOrNull { it.name == "b" }
        ?: throw PatchException("Could not find the subscription status handler ($STATUS_REPOSITORY->b)")

    val freeStatusIndex = method.instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
        reference?.definingClass == FREE_STATUS && reference.name == "a"
    }
    if (freeStatusIndex < 0) {
        throw PatchException("Could not find the free status constant in the subscription status handler")
    }

    val register = (method.instructions[freeStatusIndex] as OneRegisterInstruction).registerA
    method.replaceInstruction(freeStatusIndex, "sget-object v$register, $PREMIUM_STATUS->a:$PREMIUM_STATUS")
}

/**
 * `v00.invokeSuspend`: the mapper is a coroutine state machine whose first branch (`a == 0`) is
 * the subscription mapping. Returning the premium status right after the dispatch field is read
 * skips that mapping (and every suspension point inside it) without touching the second use of
 * the same lambda shape.
 */
private fun BytecodePatchContext.forcePremiumLocalStatus() {
    val method = mutableClassDefBy(STATUS_MAPPER).methods.singleOrNull { it.name == "invokeSuspend" }
        ?: throw PatchException("Could not find the subscription status mapper ($STATUS_MAPPER->invokeSuspend)")

    val dispatchIndex = method.instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
        reference?.definingClass == STATUS_MAPPER && reference.name == "a"
    }
    if (dispatchIndex < 0) {
        throw PatchException("Could not find the state machine dispatch field in the subscription status mapper")
    }

    val dispatchRegister = (method.instructions[dispatchIndex] as OneRegisterInstruction).registerA
    val originalCode = method.instructions[dispatchIndex + 1]

    method.addInstructionsWithLabels(
        dispatchIndex + 1,
        """
            if-nez v$dispatchRegister, :original
            sget-object v$dispatchRegister, $PREMIUM_STATUS->a:$PREMIUM_STATUS
            return-object v$dispatchRegister
        """,
        ExternalLabel("original", originalCode),
    )
}

/** `LicenseClient.checkLicense(Context)`: no-op, so a re-signed APK never enters the "not licensed" path. */
private fun BytecodePatchContext.disablePairIpLicenseCheck() {
    val method = mutableClassDefBy(LICENSE_CLIENT).methods.singleOrNull { it.name == "checkLicense" }
        ?: throw PatchException("Could not find the PairIP license check ($LICENSE_CLIENT->checkLicense)")

    val instructionCount = method.implementation?.instructions?.size
        ?: throw PatchException("PairIP license check has no implementation")

    method.removeInstructions(instructionCount)
    method.addInstructions(0, "return-void")
}

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock premium",
    description = "Unlocks Ascent Premium. The subscription status the app reports to its UI is " +
        "always \"premium\" (server answer, cached state and first-launch mapping), and the Play " +
        "license check is disabled so the re-signed build does not shut itself down.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ASCENT)

    execute {
        unlockPremium()
    }
}
