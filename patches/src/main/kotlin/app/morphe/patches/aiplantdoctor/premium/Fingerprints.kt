package app.morphe.patches.aiplantdoctor.premium

import app.morphe.patcher.Fingerprint

/**
 * The in_app_purchase_android plugin's `queryPurchasesAsync` success callback,
 * `LA/i;->j(Ln2/d;Ljava/util/List;)V` in the 3.3.0 build.
 *
 * It wraps the billing result and the returned `List<Purchase>` into a
 * `PlatformPurchasesResponse` (`LH8/F`) and posts that to the Pigeon reply, so it is
 * the single funnel for both the INAPP and the SUBS query. The signature is unique
 * across the app's dex files.
 */
internal object QueryPurchasesCallbackFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ln2/d;", "Ljava/util/List;"),
)

/**
 * Converts a `BillingResult` (`Ln2/d`) into the Pigeon `PlatformBillingResult`
 * (`LH8/q`): `LG2/h;->k(Ln2/d;)LH8/q;`, mapping the response code onto the `LH8/p`
 * enum. Rewriting the input's response code before it runs is how the callback is
 * made to report `BillingResponse.ok`.
 */
internal object BillingResultConverterFingerprint : Fingerprint(
    parameters = listOf("Ln2/d;"),
    returnType = "LH8/q;",
)

/**
 * PairIP's licence gate, `com.pairip.application.Application.attachBaseContext` calls it before
 * Flutter starts: `Lcom/pairip/licensecheck/LicenseClient;->checkLicense(Landroid/content/Context;)V`.
 *
 * Play's licence check can never pass for a re-signed APK, and PairIP's failure path starts
 * LicenseActivity and kills the process, so the patched build never reaches its own code.
 */
internal object PairIpLicenseCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    name = "checkLicense",
    parameters = listOf("Landroid/content/Context;"),
    returnType = "V",
)
