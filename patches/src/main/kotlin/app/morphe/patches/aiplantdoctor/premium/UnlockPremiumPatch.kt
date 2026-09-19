/*
 * Morphe patch for AI Plant Doctor (me.jodoin.aiplantdoctor)
 * Unlocks all premium features by bypassing Google Play Billing.
 *
 * Target: me.jodoin.aiplantdoctor v3.1.0 (antisplit APK)
 * Package: me.jodoin.aiplantdoctor
 *
 * === App Architecture ===
 * Flutter app with in_app_purchase_android plugin (Pigeon IPC).
 * Premium logic is in Dart/libapp.so, but the subscription state
 * flows through the Java Pigeon bridge which we CAN patch.
 *
 * === Subscription SKUs discovered in libapp.so ===
 *   me.jodoin.aiplantdoctor.premium_annual   (patched ← faked)
 *   me.jodoin.aiplantdoctor.premium_monthly
 *   me.jodoin.aiplantdoctor.assistant_daypass
 *   me.jodoin.aiplantdoctor.credits_03/10/25/50
 *
 * === Strategy ===
 * The Flutter in_app_purchase plugin uses Pigeon to communicate between
 * Dart and Java. Each Flutter call becomes a BasicMessageChannel message.
 *
 * QueryPurchasesAsyncSetupFingerprint → the setUp() method that registers
 * the handler for the "queryPurchasesAsync" channel message. Inside, there
 * is an anonymous MessageHandler.onMessage() that calls the real BillingClient.
 *
 * We inject smali BEFORE the BillingClient call in the handler so that:
 *   1. A fake PurchasesResultWrapper (as HashMap) is constructed.
 *   2. The Pigeon Reply callback is invoked with the fake data.
 *   3. The method returns early, skipping the real BillingClient call.
 *
 * The fake purchase map matches the structure the in_app_purchase_android
 * plugin encodes when converting a Purchase object to a Pigeon message.
 * Key fields (matching Messages.kt PurchaseWrapper):
 *   - orderId, packageName, products, purchaseTime, purchaseState,
 *     purchaseToken, quantity, autoRenewing, acknowledged
 *
 * === Register usage in the injected smali ===
 * The setup method is called with:
 *   p0 = this (InAppPurchasePlugin or wrapper)
 *   p1 = binaryMessenger (BinaryMessenger)
 *   p2 = api         (InAppPurchaseApi implementation)
 *
 * The inner MessageHandler lambda gets:
 *   p0 = this (the lambda/anon class)
 *   p1 = message (Any?) -- the encoded Pigeon call args
 *   p2 = reply  (BasicMessageChannel.Reply<Any?>) -- to send result back
 *
 * We target the lambda's onMessage method (identified by the fingerprint).
 */

package app.morphe.patches.aiplantdoctor.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val APP_PACKAGE = "me.jodoin.aiplantdoctor"
private const val SKU_ANNUAL  = "$APP_PACKAGE.premium_annual"

val unlockPremiumPatch = bytecodePatch(
    name        = "Unlock premium",
    description = "Unlocks all AI Plant Doctor premium features by bypassing Google Play Billing. " +
                  "Injects a fake active annual subscription ($SKU_ANNUAL) into the " +
                  "Flutter in_app_purchase_android Pigeon channel so the app always considers " +
                  "the user a premium subscriber.",
) {
    compatibleWith(APP_PACKAGE) {
        versions("3.1.0")
    }

    execute {
        /*
         * PATCH: queryPurchasesAsync message handler
         *
         * The fingerprint resolves to the Pigeon setUp() method that owns
         * the string literal:
         *   "dev.flutter.pigeon.in_app_purchase_android.InAppPurchaseApi.queryPurchasesAsync"
         *
         * Inside this method, a BasicMessageChannel.MessageHandler is created
         * (as an anonymous class or lambda). Its onMessage() is what we patch.
         *
         * Morphe's Fingerprint.method gives us the resolved MethodDef of the
         * MessageHandler.onMessage implementation.
         *
         * Parameters of onMessage:
         *   p0 = this
         *   p1 = message  (Ljava/lang/Object;)  -- the encoded call arguments
         *   p2 = reply    (Lio/flutter/plugin/common/BasicMessageChannel$Reply;)
         *
         * We inject at index 0 (top of method):
         *   1. Build billingResult HashMap  { responseCode=0, debugMessage="" }
         *   2. Build purchase HashMap with all fields for premium_annual
         *   3. Wrap purchase in ArrayList
         *   4. Build outer result HashMap  { billingResult, purchasesList }
         *   5. Wrap outer result in ArrayList  (Pigeon wraps replies in a list)
         *   6. Invoke p2.reply(wrappedResult)
         *   7. return-void → skip the real BillingClient call
         *
         * Registers v0–v10 are used. The smali tool adjusts the locals count.
         */
        QueryPurchasesAsyncSetupFingerprint.method.addInstructions(
            0,
            """
                # ──────────────────────────────────────────────────────────────
                #  Build  billingResult = { responseCode: 0, debugMessage: "" }
                # ──────────────────────────────────────────────────────────────
                new-instance v0, Ljava/util/HashMap;
                invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

                const-string v1, "responseCode"
                const/4 v2, 0x0
                invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
                move-result-object v2
                invoke-virtual {v0, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                const-string v1, "debugMessage"
                const-string v2, ""
                invoke-virtual {v0, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                # ──────────────────────────────────────────────────────────────
                #  Build  purchase map
                # ──────────────────────────────────────────────────────────────
                new-instance v3, Ljava/util/HashMap;
                invoke-direct {v3}, Ljava/util/HashMap;-><init>()V

                # orderId
                const-string v1, "orderId"
                const-string v2, "GPA.morphe-premium-annual-9999"
                invoke-virtual {v3, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                # packageName
                const-string v1, "packageName"
                const-string v2, "me.jodoin.aiplantdoctor"
                invoke-virtual {v3, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                # products = ["me.jodoin.aiplantdoctor.premium_annual"]
                new-instance v4, Ljava/util/ArrayList;
                invoke-direct {v4}, Ljava/util/ArrayList;-><init>()V
                const-string v5, "me.jodoin.aiplantdoctor.premium_annual"
                invoke-virtual {v4, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                const-string v1, "products"
                invoke-virtual {v3, v1, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                # purchaseTime = 9999999999999 (far future, in ms)
                const-string v1, "purchaseTime"
                const-wide v6, 0x9184E72A000L
                invoke-static {v6, v7}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;
                move-result-object v6
                invoke-virtual {v3, v1, v6}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                # purchaseState = 1 (PURCHASED)
                const-string v1, "purchaseState"
                const/4 v2, 0x1
                invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
                move-result-object v2
                invoke-virtual {v3, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                # purchaseToken
                const-string v1, "purchaseToken"
                const-string v2, "morphe-fake-token-premium-annual-000"
                invoke-virtual {v3, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                # quantity = 1
                const-string v1, "quantity"
                const/4 v2, 0x1
                invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
                move-result-object v2
                invoke-virtual {v3, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                # autoRenewing = true
                const-string v1, "autoRenewing"
                const/4 v2, 0x1
                invoke-static {v2}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
                move-result-object v2
                invoke-virtual {v3, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                # acknowledged = true
                const-string v1, "acknowledged"
                const/4 v2, 0x1
                invoke-static {v2}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
                move-result-object v2
                invoke-virtual {v3, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                # isAcknowledged (alias used by some versions of the plugin)
                const-string v1, "isAcknowledged"
                const/4 v2, 0x1
                invoke-static {v2}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
                move-result-object v2
                invoke-virtual {v3, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                # ──────────────────────────────────────────────────────────────
                #  Wrap purchase in ArrayList
                # ──────────────────────────────────────────────────────────────
                new-instance v8, Ljava/util/ArrayList;
                invoke-direct {v8}, Ljava/util/ArrayList;-><init>()V
                invoke-virtual {v8, v3}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

                # ──────────────────────────────────────────────────────────────
                #  Build outer result map
                #  { billingResult: v0, purchasesList: v8 }
                # ──────────────────────────────────────────────────────────────
                new-instance v9, Ljava/util/HashMap;
                invoke-direct {v9}, Ljava/util/HashMap;-><init>()V

                const-string v1, "billingResult"
                invoke-virtual {v9, v1, v0}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                const-string v1, "purchasesList"
                invoke-virtual {v9, v1, v8}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

                # ──────────────────────────────────────────────────────────────
                #  Pigeon wraps the reply in a List<Any?>: [result]
                # ──────────────────────────────────────────────────────────────
                new-instance v10, Ljava/util/ArrayList;
                invoke-direct {v10}, Ljava/util/ArrayList;-><init>()V
                invoke-virtual {v10, v9}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

                # ──────────────────────────────────────────────────────────────
                #  Send the faked reply back to Flutter and return early.
                #  p2 = BasicMessageChannel.Reply<Any?> provided by Pigeon.
                # ──────────────────────────────────────────────────────────────
                invoke-interface {p2, v10}, Lio/flutter/plugin/common/BasicMessageChannel${'$'}Reply;->reply(Ljava/lang/Object;)V

                return-void
            """,
        )
    }
}
