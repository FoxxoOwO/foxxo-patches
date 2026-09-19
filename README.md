# AI Plant Doctor – Morphe Premium Patch

> **Cíl:** `me.jodoin.aiplantdoctor v3.1.0_antisplit.apk`  
> **Framework:** [Morphe](https://morphe.software) (ReVanced-based bytecode patcher)

---

## Co patch dělá

Patch odemyká všechny premium funkce aplikace **AI Plant Doctor** tím, že
přesměruje Google Play Billing komunikaci. Aplikace si vždy bude myslet, že
uživatel má aktivní roční předplatné.

### Premium SKU

| Product ID | Typ |
|---|---|
| `me.jodoin.aiplantdoctor.premium_annual` | **Roční** (toto je fakeováno) |
| `me.jodoin.aiplantdoctor.premium_monthly` | Měsíční |

### Odemčené funkce

- Neomezená diagnostika rostlin (AI Plant Doctor)
- Přístup k asistentovi Budsy
- Detekce škůdců a chorob bez limitu
- Personalizované plány péče
- Pokročilá analýza zdraví rostlin
- Credits pro AI diagnózy
- Day pass funkce

---

## Jak patch funguje

```
Flutter (Dart/libapp.so)
       ↕  BasicMessageChannel (Pigeon)
Java Billing Layer  ← INTERCEPT HERE
       ↕
Google Play BillingClient  (přeskočen)
```

### Architektura aplikace

AI Plant Doctor je **Flutter aplikace**. Logika předplatného běží v Dart kódu
uvnitř `libapp.so` (ARM64 AOT binary) – ten nelze patchovat bytecode nástrojem.

Avšak Dart vrstva získává stav předplatného přes **Pigeon IPC** od Java pluginu
`in_app_purchase_android` (`io.flutter.plugins.inapppurchase.InAppPurchasePlugin`).

### Co přesně patchujeme

Plugin registruje `BasicMessageChannel` pro každou metodu billing API.
Klíčová metoda je `queryPurchasesAsync` – Flutter ji volá aby zjistil,
co uživatel koupil.

**Fingerprint** identifikuje handler (anonymní třída/lambda) přes unikátní
string literál:
```
"dev.flutter.pigeon.in_app_purchase_android.InAppPurchaseApi.queryPurchasesAsync"
```

**Injektovaný smali** (na začátku `onMessage()`) vytvoří fake HashMap strukturu:
```json
{
  "billingResult": { "responseCode": 0, "debugMessage": "" },
  "purchasesList": [{
    "orderId": "GPA.morphe-premium-annual-9999",
    "packageName": "me.jodoin.aiplantdoctor",
    "products": ["me.jodoin.aiplantdoctor.premium_annual"],
    "purchaseTime": 9999999999999,
    "purchaseState": 1,
    "purchaseToken": "morphe-fake-token-premium-annual-000",
    "quantity": 1,
    "autoRenewing": true,
    "acknowledged": true
  }]
}
```

Tato data jsou poslána zpět do Dartu přes `BasicMessageChannel.Reply.reply()`,
metoda pak okamžitě vrátí (`return-void`), takže reálné volání BillingClient
nikdy neproběhne.

---

## Struktura souborů

```
morphe-patch/
├── settings.gradle.kts          # Morphe plugin configuration
├── build.gradle.kts             # Root build
├── gradle.properties
├── extensions/
│   └── proguard-rules.pro
└── patches/
    ├── build.gradle.kts
    ├── stub/
    └── src/main/kotlin/app/morphe/patches/aiplantdoctor/premium/
        ├── Fingerprints.kt          # Method identifiers
        └── UnlockPremiumPatch.kt    # Main patch logic
```

---

## Jak použít s Morphe

### Prerekvizity
- Nainstalovaná aplikace [Morphe](https://morphe.software)
- APK soubor: `me.jodoin.aiplantdoctor v3.1.0_antisplit.apk`

### Integrace do Morphe patches repozitáře

1. Zkopírujte obsah `patches/src/main/kotlin/app/morphe/patches/aiplantdoctor/`
   do Morphe patches repozitáře ve stejné cestě.

2. Přidejte patch soubory do repozitáře.

3. Sestavte patch bundle:
   ```bash
   ./gradlew build
   ```

4. V aplikaci Morphe:
   - Vyberte APK: `me.jodoin.aiplantdoctor v3.1.0_antisplit.apk`
   - Vyberte patch: **Unlock premium**
   - Klikněte na Patch

### Přímé použití s CLI (revanced-cli/morphe-cli)

```bash
java -jar morphe-cli.jar \
  patch \
  --patch-bundle morphe-patches.jar \
  --include "Unlock premium" \
  "me.jodoin.aiplantdoctor v3.1.0_antisplit.apk"
```

---

## Technické poznámky

### Proč patchujeme Java vrstvu, ne libapp.so?

- `libapp.so` obsahuje Dart AOT kód → nativní ARM64 instrukce
- Morfhe/ReVanced pracuje s Dalvik bytecode (DEX soubory)
- Dart runtime načítá `isPremiumProvider`/`hasPremiumSubscriptionProvider`
  ze subscription service, která čerpá data z Pigeon channel

### Proč `queryPurchasesAsync` a ne `onPurchasesUpdated`?

`queryPurchasesAsync` je volán při každém spuštění aplikace (a po reconnectu
billing clientu) a je hlavním zdrojem pravdy o stavu předplatného.
`onPurchasesUpdated` je reaktivní event pouze pro nové nákupy.

### Možné problémy

| Problém | Příčina | Řešení |
|---|---|---|
| Fingerprint nenalezen | Jiná verze Pigeon v pluginu | Ověřit channel string v DEX |
| App crashuje | Jiná struktura reply | Zkontrolovat Messages.kt v plugin source |
| Premium stale po restartu | App cachuje jinak | Přidat injection i do `startConnection` handler |

### Verifikace channel strings v APK

```powershell
$bytes = [System.IO.File]::ReadAllBytes("me.jodoin.aiplantdoctor v3.1.0_antisplit.apk")
# (extrahovat jako ZIP, pak classes4.dex)
$text = [System.Text.Encoding]::ASCII.GetString($bytes)
$text -match "in_app_purchase_android.InAppPurchaseApi.queryPurchasesAsync"
```

---

## Soubory s análýzou APK

Při analýze APK bylo zjištěno:

- **Flutter verze:** Flutter engine (libflutter.so, libapp.so)
- **Billing plugin:** `io.flutter.plugins.inapppurchase.InAppPurchasePlugin` (classes4.dex)
- **DEX count:** 4 (classes.dex, classes2.dex, classes3.dex, classes4.dex)
- **Pigeon generovaný kód:** v classes4.dex
- **IAP Pigeon kanály** (`InAppPurchaseApi`):
  - `acknowledgePurchase`
  - `consumeAsync`
  - `endConnection`
  - `isReady`
  - `launchBillingFlow`
  - `queryProductDetailsAsync`
  - `queryPurchasesAsync` ← **PATCHOVÁNO**
  - `startConnection`
- **Subscription SKUs** (z libapp.so string pool):
  - `me.jodoin.aiplantdoctor.premium_annual`
  - `me.jodoin.aiplantdoctor.premium_monthly`
  - `me.jodoin.aiplantdoctor.assistant_daypass`
  - `me.jodoin.aiplantdoctor.credits_03/10/25/50`

---

*Patch vytvořen pro Morphe v1.43.0+ framework.*
