package com.bluefletch.motorola.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.net.Uri;

import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;

import com.bluefletch.motorola.BarcodeScan;
import com.bluefletch.motorola.DataWedgeIntentHandler;
import com.bluefletch.motorola.ScanCallback;

public class MotorolaDatawedgePlugin extends CordovaPlugin {

    protected CordovaResourceApi resourceApi;
    private DataWedgeIntentHandler wedge;
    protected static String TAG = "MotorolaDatawedgePlugin";
    final static String dwOutputPath =
            "/enterprise/device/settings/datawedge/autoimport";
    final static String dwTmpName = "_DWUnsupportedProfileName.profile";

    private interface FileOp {
        void run(String uri, String filename) throws Exception;
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView)
    {
        super.initialize(cordova, webView);
        resourceApi = webView.getResourceApi();
        wedge = new DataWedgeIntentHandler(cordova.getActivity().getBaseContext());
    }
    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if ("scanner.register".equals(action)) {
            wedge.setScanCallback(new ScanCallback<BarcodeScan>() {
                @Override
                public void execute(BarcodeScan scan) {
                    Log.i(TAG, "Scan result [" + scan.LabelType + "-" + scan.Barcode + "].");

                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("type", scan.LabelType);
                        obj.put("barcode", scan.Barcode);
                        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, obj);
                        pluginResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(pluginResult);
                    } catch(JSONException e){
                        Log.e(TAG, "Error building json object", e);

                    }
                }
            });
        }
        else if ("scanner.unregister".equals(action)) {
            wedge.setScanCallback(null);
            if (!wedge.hasListeners()) {
                wedge.stop();
            }
        }
        else if ("scanner.softScanOn".equals(action)){
            wedge.startScanning(true);
            callbackContext.success();
        }
        else if ("scanner.softScanOff".equals(action)) {
            wedge.startScanning(false);
            callbackContext.success();
        }

        //register for magstripe callbacks
        else if ("magstripe.register".equals(action)){
             wedge.setMagstripeReadCallback(new ScanCallback<List<String>>() {
                @Override
                public void execute(List<String> result) {
                    Log.i(TAG, "Magstripe result [" + result + "].");
                    JSONArray tracks = new JSONArray(result);
                    //send plugin result
                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, tracks);
                    pluginResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(pluginResult);
                }
            });
        }
        else if("magstripe.unregister".equals(action)) {
            wedge.setMagstripeReadCallback(null);
            if (!wedge.hasListeners()) {
                wedge.stop();
            }
        }

        //register for plugin callbacks
        else if ("switchProfile".equals(action)){
            wedge.switchProfile(args.getString(0));
        }

        else if ("importProfile".equals(action)) {
            String mUri = args.getString(0);
            String mFileName = args.getString(1);
            threadhelper( new FileOp( ){
                public void run(String uri, String filename) throws Exception {
                    copyDataWedgeProfile(uri, filename);
                    callbackContext.success();
                }
            }, mUri, mFileName, callbackContext);
        }

        else if ("stop".equals(action)){
            wedge.stop();
        }


        //start plugin now if not already started
        if ("start".equals(action) || "magstripe.register".equals(action) || "scanner.register".equals(action)) {

            //try to read intent action from inbound params
            String intentAction = null;
            if (args.length() > 0) {
                intentAction = args.getString(0);
            }
            if (intentAction != null && intentAction.length() > 0) {
                Log.i(TAG, "Intent action length  " + intentAction.length());

                wedge.setDataWedgeIntentAction(intentAction);
            }
            wedge.start();
        }

        return true;
    }
    /**
    * Always close the current intent reader
    */
    @Override
    public void onPause(boolean multitasking)
    {
        super.onPause(multitasking);
        wedge.stop();
    }

    @Override
    public void onNewIntent(Intent intent) {

        Log.i(TAG, "Got inbound intent  " + intent.getAction());
        wedge.handleIntent(intent);
    }

    /**
    * Always resume the current activity
    */
    @Override
    public void onResume(boolean multitasking)
    {
        super.onResume(multitasking);
        wedge.start();
    }

    private void threadhelper(final FileOp f, final String uri,
            final String filename, final CallbackContext callbackContext){
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    f.run(uri, filename);
                } catch ( Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /*
     * Copy datawedge profile from cordova assets to auto import folder
     */
    private void copyDataWedgeProfile(String uri, String filename) {
        try {
            final String dwFinalName = filename;
            final File tmpFile = new File(dwOutputPath, dwTmpName);
            final File finalFile = new File(dwOutputPath, dwFinalName);
            tmpFile.delete();
            finalFile.delete();

            Uri srcUri = Uri.parse(uri + dwFinalName);
            CordovaResourceApi.OpenForReadResult ofrr = resourceApi.openForRead(srcUri);
            File srcFile = new File(srcUri.getPath());
            InputStream raw = ofrr.inputStream;

            // copy the raw data to the internal phone storage
            try {
                final OutputStream output = new FileOutputStream(tmpFile);
                try {
                    try {
                        final byte[] buffer = new byte[4096];
                        int read;

                        while ((read = raw.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }

                        output.flush();
                    } finally {
                        output.close();
                    }
                } catch (Exception e) {
                    Log.i(TAG,"Error Copying datawedge profile");
                    return;
                }
            } finally {
                raw.close();
            }

            // chmod the file permissions of the input file
            tmpFile.setReadable(true, false);
            tmpFile.setWritable(true, false);
            tmpFile.setExecutable(true, false);

            // move the file to the output file so it gets imported
            tmpFile.renameTo(finalFile);
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
            Log.i(TAG, "Copying datawedge profile", e);
        }
    }
}
