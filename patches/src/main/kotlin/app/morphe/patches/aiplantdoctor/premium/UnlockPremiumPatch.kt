package app.morphe.patches.aiplantdoctor.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

private const val APP_PACKAGE = "me.jodoin.aiplantdoctor"
private const val SKU_ANNUAL  = "$APP_PACKAGE.premium_annual"

/**
 * JSON parsed by `com.android.billingclient.api.Purchase.(String, String)`.
 * The plugin's `Purchase -> PlatformPurchase` converter reads exactly these keys,
 * so what reaches Dart is an owned, acknowledged annual subscription.
 */
private val FAKE_PURCHASE_JSON = """
    {"orderId":"GPA.3312-5819-2144-88410",
    "packageName":"me.jodoin.aiplantdoctor",
    "productId":"$SKU_ANNUAL",
    "productIds":["$SKU_ANNUAL"],
    "purchaseTime":1758250000000,
    "purchaseState":1,
    "purchaseToken":"morphe_fake_token_annual",
    "autoRenewing":true,
    "acknowledged":true,
    "quantity":1,
    "developerPayload":""}
""".trimIndent()
    .lines()
    .joinToString(separator = "")
    .replace("\"", "\\\"")

private val COMPATIBILITY_AIPLANT = Compatibility(
    name = "AI Plant Doctor",
    packageName = APP_PACKAGE,
    targets = listOf(
        AppTarget(
            version = "3.3.0"
        )
    )
)

val unlockPremiumPatch = bytecodePatch(
    name        = "Unlock premium",
    description = "Unlocks all AI Plant Doctor premium features by bypassing Google Play Billing and " +
        "returning an active annual subscription. Also clears the PairIP licence gate, which would " +
        "otherwise kill the re-signed build before it starts.",
) {
    compatibleWith(COMPATIBILITY_AIPLANT)

    execute {
        // 0. The release APK is wrapped in PairIP. Its Application runs checkLicense() in
        //    attachBaseContext, the re-signed build fails Play's licence check, and PairIP then
        //    starts LicenseActivity and kills the process before Flutter is up. Blank the check
        //    out: the licence gate is the only thing it drives.
        PairIpLicenseCheckFingerprint.method.apply {
            val instructionCount = implementation?.instructions?.size
                ?: throw PatchException("PairIP checkLicense has no implementation")
            removeInstructions(instructionCount)
            addInstructions(0, "return-void")
        }

        // 1. Report BillingResponse.OK (responseCode = 0, empty debug message) whatever
        //    Play Billing answered, so the purchase list is never discarded.
        BillingResultConverterFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                iput v0, p0, Ln2/d;->a:I
                const-string v0, ""
                iput-object v0, p0, Ln2/d;->c:Ljava/lang/String;
            """
        )

        // 2. Replace the queried purchases with a single owned annual subscription.
        //    The plugin turns this Purchase into the PlatformPurchase Dart sees, and the
        //    app's own subscription check then reports an active premium plan.
        QueryPurchasesCallbackFingerprint.method.addInstructions(
            0,
            """
                new-instance p2, Ljava/util/ArrayList;
                invoke-direct {p2}, Ljava/util/ArrayList;-><init>()V

                new-instance v0, Lcom/android/billingclient/api/Purchase;
                const-string v1, "$FAKE_PURCHASE_JSON"
                invoke-direct {v0, v1, v1}, Lcom/android/billingclient/api/Purchase;-><init>(Ljava/lang/String;Ljava/lang/String;)V

                invoke-virtual {p2, v0}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
            """
        )
    }
}
