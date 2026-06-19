/* global cordova */

/**
 * cordova-plugin-barcode-scanner
 *
 * Scans 1D linear barcodes only. QR codes and other 2D codes (Aztec, Data
 * Matrix, PDF417) are intentionally NOT scanned.
 */

var exec = require('cordova/exec');

var SERVICE = 'BarcodeScanner';

/**
 * Supported 1D barcode formats.
 *
 * These values can be passed in `options.formats` to restrict which symbologies
 * the scanner will accept, and they are also the values returned in
 * `result.format`.
 */
var FORMATS = {
    EAN_13: 'EAN_13',
    EAN_8: 'EAN_8',
    UPC_A: 'UPC_A',
    UPC_E: 'UPC_E',
    CODE_39: 'CODE_39',
    CODE_93: 'CODE_93',
    CODE_128: 'CODE_128',
    ITF: 'ITF',
    CODABAR: 'CODABAR'
};

/**
 * Scan a single 1D barcode.
 *
 * @param {function(Object)} success Called with a result object:
 *        { text: String, format: String, cancelled: Boolean }
 *        When the user cancels, `cancelled` is true and `text`/`format` are "".
 * @param {function(String)} [error] Called with an error message string.
 * @param {Object} [options]
 * @param {String}   [options.prompt]  Message shown on the scanner overlay.
 * @param {String[]} [options.formats] Restrict accepted formats, e.g.
 *        ['EAN_13', 'CODE_128']. Defaults to every supported 1D format.
 *        Any 2D formats requested here are ignored.
 * @param {Boolean}  [options.beep]    Play a short beep on a successful scan.
 *        Defaults to true.
 */
function scan(success, error, options) {
    if (typeof success !== 'function') {
        throw new Error('cordova.plugins.barcodeScanner.scan requires a success callback');
    }

    var opts = options || {};

    // Defensive normalisation so the native layer always receives a plain object.
    var payload = {
        prompt: typeof opts.prompt === 'string' ? opts.prompt : undefined,
        formats: Array.isArray(opts.formats) ? opts.formats : undefined,
        beep: typeof opts.beep === 'boolean' ? opts.beep : true
    };

    exec(success, error || function () {}, SERVICE, 'scan', [payload]);
}

module.exports = {
    scan: scan,
    FORMATS: FORMATS
};
