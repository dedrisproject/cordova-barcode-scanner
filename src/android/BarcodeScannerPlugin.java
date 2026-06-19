package com.dedris.barcodescanner;

import android.app.Activity;
import android.content.Intent;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Cordova bridge for the 1D barcode scanner.
 *
 * The actual camera + decoding work happens in {@link BarcodeScannerActivity},
 * which is launched for a result. This keeps the WebView untouched and lets the
 * scanner run full screen.
 */
public class BarcodeScannerPlugin extends CordovaPlugin {

    /** Arbitrary request code used with startActivityForResult. */
    private static final int REQUEST_SCAN = 0x0BA7;

    private CallbackContext callbackContext;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("scan".equals(action)) {
            this.callbackContext = callbackContext;
            JSONObject options = args.optJSONObject(0);
            startScan(options != null ? options : new JSONObject());
            return true;
        }
        return false;
    }

    private void startScan(final JSONObject options) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(cordova.getActivity(), BarcodeScannerActivity.class);

                String prompt = options.optString("prompt", null);
                if (prompt != null && !prompt.isEmpty()) {
                    intent.putExtra(BarcodeScannerActivity.EXTRA_PROMPT, prompt);
                }

                JSONArray formats = options.optJSONArray("formats");
                if (formats != null) {
                    intent.putExtra(BarcodeScannerActivity.EXTRA_FORMATS, formats.toString());
                }

                intent.putExtra(BarcodeScannerActivity.EXTRA_BEEP, options.optBoolean("beep", true));

                cordova.setActivityResultCallback(BarcodeScannerPlugin.this);
                cordova.getActivity().startActivityForResult(intent, REQUEST_SCAN);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_SCAN) {
            return;
        }

        CallbackContext callback = this.callbackContext;
        this.callbackContext = null;
        if (callback == null) {
            return;
        }

        try {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String text = data.getStringExtra(BarcodeScannerActivity.RESULT_TEXT);
                String format = data.getStringExtra(BarcodeScannerActivity.RESULT_FORMAT);

                JSONObject result = new JSONObject();
                result.put("text", text != null ? text : "");
                result.put("format", format != null ? format : "");
                result.put("cancelled", false);
                callback.success(result);
                return;
            }

            // RESULT_CANCELED: either a user cancel or a recoverable error.
            String error = data != null ? data.getStringExtra(BarcodeScannerActivity.RESULT_ERROR) : null;
            if (error != null) {
                callback.error(error);
                return;
            }

            JSONObject cancelled = new JSONObject();
            cancelled.put("text", "");
            cancelled.put("format", "");
            cancelled.put("cancelled", true);
            callback.success(cancelled);
        } catch (JSONException e) {
            callback.error("Failed to build scan result: " + e.getMessage());
        }
    }
}
