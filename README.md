# cordova-plugin-1d-barcode-scanner

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platforms](https://img.shields.io/badge/platforms-iOS%20%7C%20Android-blue.svg)](#requirements)

An **open‑source** Cordova plugin that scans **1D linear barcodes** on **iOS**
and **Android**.

> ⚠️ This plugin scans **linear barcodes only** (EAN, UPC, Code 128, Code 39,
> Code 93, ITF, Codabar). **QR codes and other 2D codes (Aztec, Data Matrix,
> PDF417) are intentionally NOT scanned.**

It uses the platform's native, offline decoders — no cloud service and no
account required:

| Platform | Camera          | Decoder                                   |
|----------|-----------------|-------------------------------------------|
| Android  | CameraX         | Google ML Kit (bundled / offline)         |
| iOS      | AVFoundation    | `AVCaptureMetadataOutput` (built‑in)      |

---

## Supported barcode formats

`EAN_13`, `EAN_8`, `UPC_A`, `UPC_E`, `CODE_39`, `CODE_93`, `CODE_128`, `ITF`,
`CODABAR`.

---

## Requirements

- Cordova CLI `>= 9`
- **cordova-android `>= 12`** (Android API 33+ / AndroidX — enabled by default)
- **cordova-ios `>= 6`**
- iOS 11+ device, Android 5.0+ (API 21) device with a camera

---

## Installation

From the npm registry:

```bash
cordova plugin add cordova-plugin-1d-barcode-scanner
```

Directly from the Git repository:

```bash
cordova plugin add https://github.com/dedrisproject/cordova-barcode-scanner.git
```

Or from a local checkout:

```bash
cordova plugin add /path/to/cordova-barcode-scanner
```

### iOS camera permission message

iOS requires a usage description for the camera. The plugin adds a sensible
default, but you can override it at install time:

```bash
cordova plugin add cordova-plugin-1d-barcode-scanner \
  --variable CAMERA_USAGE_DESCRIPTION="We use the camera to scan product barcodes."
```

> On Android the `CAMERA` permission and the runtime request are handled
> automatically by the plugin — you don't need to add anything.

---

## API

The plugin is exposed as `cordova.plugins.barcodeScanner`.

### `scan(success, error, options)`

| Argument  | Type       | Description                                                        |
|-----------|------------|--------------------------------------------------------------------|
| `success` | `Function` | Called with a **result object** (see below). **Required.**         |
| `error`   | `Function` | Called with an error message `String`. Optional.                   |
| `options` | `Object`   | Optional configuration (see below).                                |

**Options**

| Key       | Type       | Default                          | Description                                                                 |
|-----------|------------|----------------------------------|-----------------------------------------------------------------------------|
| `prompt`  | `String`   | `"Point your camera at a barcode"` | Text shown on the scanner overlay.                                          |
| `formats` | `String[]` | all supported formats            | Restrict the accepted symbologies, e.g. `['EAN_13', 'CODE_128']`. Any 2D format passed here (e.g. `QR_CODE`) is ignored. |
| `beep`    | `Boolean`  | `true`                           | Play a short beep on a successful scan.                                     |

**Result object** (passed to `success`)

```json
{
  "text": "4006381333931",
  "format": "EAN_13",
  "cancelled": false
}
```

When the user dismisses the scanner, `success` is still called with
`{ "text": "", "format": "", "cancelled": true }`.

A list of the supported format strings is available on
`cordova.plugins.barcodeScanner.FORMATS`.

---

## Usage

### Basic

```js
document.addEventListener('deviceready', function () {
  cordova.plugins.barcodeScanner.scan(
    function (result) {
      if (result.cancelled) {
        console.log('User cancelled the scan');
        return;
      }
      console.log('Barcode: ' + result.text + ' (' + result.format + ')');
    },
    function (error) {
      console.error('Scan failed: ' + error);
    }
  );
});
```

### Restricting formats and customising the prompt

```js
cordova.plugins.barcodeScanner.scan(
  function (result) { /* ... */ },
  function (error) { /* ... */ },
  {
    prompt: 'Scan the product barcode',
    formats: ['EAN_13', 'EAN_8', 'UPC_A', 'CODE_128'],
    beep: true
  }
);
```

---

## Complete sample app

A minimal, copy‑paste demo. It shows a **Scan** button and prints the result on
screen.

**`www/index.html`**

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="initial-scale=1, width=device-width, viewport-fit=cover" />
  <meta name="format-detection" content="telephone=no" />
  <title>Barcode Scanner Demo</title>
  <style>
    body { font-family: -apple-system, Roboto, sans-serif; padding: 24px; text-align: center; }
    button { font-size: 18px; padding: 14px 28px; border: 0; border-radius: 8px;
             background: #007aff; color: #fff; }
    #result { margin-top: 24px; font-size: 16px; word-break: break-all; }
    #result code { background: #f2f2f7; padding: 2px 6px; border-radius: 4px; }
  </style>
</head>
<body>
  <h1>Barcode Scanner</h1>
  <button id="scanBtn" disabled>Scan a barcode</button>
  <div id="result">Waiting for <code>deviceready</code>…</div>

  <script src="cordova.js"></script>
  <script src="js/index.js"></script>
</body>
</html>
```

**`www/js/index.js`**

```js
document.addEventListener('deviceready', onDeviceReady, false);

function onDeviceReady() {
  var btn = document.getElementById('scanBtn');
  var out = document.getElementById('result');

  btn.disabled = false;
  out.textContent = 'Ready.';

  btn.addEventListener('click', function () {
    out.textContent = 'Opening camera…';

    cordova.plugins.barcodeScanner.scan(
      function (result) {
        if (result.cancelled) {
          out.textContent = 'Scan cancelled.';
          return;
        }
        out.innerHTML =
          'Format: <code>' + result.format + '</code><br>' +
          'Value: <code>' + result.text + '</code>';
      },
      function (error) {
        out.textContent = 'Error: ' + error;
      },
      {
        prompt: 'Align the barcode within the frame',
        formats: ['EAN_13', 'EAN_8', 'UPC_A', 'UPC_E', 'CODE_128', 'CODE_39'],
        beep: true
      }
    );
  });
}
```

### Build & run the sample

```bash
# Create a project and add platforms + the plugin
cordova create barcodeDemo com.example.barcodedemo BarcodeDemo
cd barcodeDemo

cordova platform add android
cordova platform add ios

cordova plugin add https://github.com/dedrisproject/cordova-barcode-scanner.git

# Replace www/index.html and www/js/index.js with the snippets above, then:
cordova run android
# or
cordova run ios
```

> The camera only works on a **physical device** (simulators/emulators have no
> real camera).

---

## How "no QR" is enforced

- **Android (ML Kit):** the scanner is built with
  `BarcodeScannerOptions.setBarcodeFormats(...)` listing **only** 1D formats.
  2D formats such as `FORMAT_QR_CODE` are never enabled, so they are never
  decoded.
- **iOS (AVFoundation):** `AVCaptureMetadataOutput.metadataObjectTypes` is set
  to **only** 1D `AVMetadataObjectType` values (intersected with the
  device‑supported types). `AVMetadataObjectTypeQRCode` is never added, and a
  defensive check additionally drops any QR result.

If you pass a 2D format name (e.g. `QR_CODE`) in `options.formats`, it is simply
ignored.

---

## Troubleshooting

- **"Camera permission was denied"** — the user declined camera access. Send
  them to the OS app settings to re‑enable it.
- **Android build fails on an old `compileSdk`** — make sure you're on
  cordova-android `>= 12`. The plugin's dependencies (CameraX / ML Kit) require
  `compileSdk 33+`.
- **iOS build can't find the camera usage string** — confirm
  `NSCameraUsageDescription` is present in your `*-Info.plist` (the plugin adds
  it automatically on install).
- **Nothing is detected** — linear barcodes need to be reasonably large and well
  lit, and roughly aligned with the on‑screen guide line.

---

## License

[MIT](LICENSE) © dedrisproject — free and open source. Contributions and issues
are welcome at the
[project repository](https://github.com/dedrisproject/cordova-barcode-scanner).
