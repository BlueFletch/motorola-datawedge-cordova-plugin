package com.bluefletch.motorola;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.bluefletch.motorola.BarcodeScan;
import com.bluefletch.motorola.ScanCallback;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class DataWedgeIntentHandler {
    
    protected static Object stateLock = new Object();
    protected static boolean hasInitialized = false;

    protected static String TAG= DataWedgeIntentHandler.class.getSimpleName();

    protected Context applicationContext;

    protected static String DEFAULT_ACTION = "com.bluefletch.motorola.datawedge.ACTION";
    protected String dataWedgeAction = DEFAULT_ACTION;
    /**
    * This function must be called with the intent Action as configured in the DataWedge Application
    **/
    public void setDataWedgeIntentAction(String action){
        Log.i(TAG, "Setting data wedge intent to " + action);
        if (action == null || "".equals(action)) return;
        this.dataWedgeAction = action;
    }

    protected ScanCallback<BarcodeScan> scanCallback;
    public void setScanCallback(ScanCallback<BarcodeScan> callback){
        scanCallback = callback;
    }
    protected ScanCallback<List<String>> magstripeCallback;
    public void setMagstripeReadCallback(ScanCallback<List<String>> callback){
        magstripeCallback = callback;
    }


    public DataWedgeIntentHandler(Context context) {
        TAG = this.getClass().getSimpleName();
        applicationContext = context;
    }

    public void start() {
        Log.i(TAG, "Open called");
        if (hasInitialized) {
            return;
        }
        synchronized (stateLock) {
            if (hasInitialized) {
                return;
            }

            Log.i(TAG, "Register for Datawedge intent: " + dataWedgeAction);

            applicationContext.registerReceiver(dataReceiver, new IntentFilter(dataWedgeAction));

            enableScanner(true);
            hasInitialized = true;
        }
    }

    public void stop() {
        if (!hasInitialized) {
            return;
        }
        synchronized (stateLock) {
            if (!hasInitialized) {
                return;
            }

            Log.i(TAG, "Running close plugin intent");

            enableScanner(false);

            try {
                applicationContext.unregisterReceiver(dataReceiver);
            } catch(Exception ex) {
                Log.e(TAG, "Exception while unregistering data receiver. Was start ever called?", ex);
            }

            hasInitialized = false;
        }
    }

    public boolean hasListeners(){
        return this.scanCallback != null || this.magstripeCallback != null;
    }

    protected void enableScanner(boolean shouldEnable) {
        Intent enableIntent = new Intent("com.motorolasolutions.emdk.datawedge.api.ACTION_SCANNERINPUTPLUGIN");
        enableIntent.putExtra("com.motorolasolutions.emdk.datawedge.api.EXTRA_PARAMETER", 
            shouldEnable ? "ENABLE_PLUGIN" : "DISABLE_PLUGIN");

        applicationContext.sendBroadcast(enableIntent);
    }

    public void startScanning(boolean turnOn) {
        synchronized (stateLock) {
            Intent scanOnIntent = new Intent("com.motorolasolutions.emdk.datawedge.api.ACTION_SOFTSCANTRIGGER");
            scanOnIntent.putExtra("com.motorolasolutions.emdk.datawedge.api.EXTRA_PARAMETER", 
                turnOn ? "START_SCANNING" : "STOP_SCANNING");

            applicationContext.sendBroadcast(scanOnIntent);
        }
    }

    public void switchProfile(String profile) {
        synchronized (stateLock) {
            Intent profileIntent = new Intent("com.motorolasolutions.emdk.datawedge.api.ACTION_SETDEFAULTPROFILE");
            profileIntent.putExtra("com.motorolasolutions.emdk.datawedge.api.EXTRA_PROFILENAME", profile);

            applicationContext.sendBroadcast(profileIntent);
        }
    }

    public void handleIntent(Intent intent){
        if (intent != null) {
			String action = intent.getAction();
			if(action != null && action.equals(DEFAULT_ACTION)) {
				dataReceiver.onReceive(applicationContext, intent);
			}
        }
    }

    private static String TRACK_PREFIX_FORMAT = "com.motorolasolutions.emdk.datawedge.msr_track%d";
    private static String TRACK_STATUS_FORMAT = "com.motorolasolutions.emdk.datawedge.msr_track%d_status";
    /**
     * Receiver to handle receiving data from intents
     */
    private BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Data receiver trigged");
            try {
                if("scanner".equalsIgnoreCase(intent.getStringExtra("com.motorolasolutions.emdk.datawedge.source"))) {
                    if (scanCallback == null) {
                        Log.e(TAG, "Scan data received, but callback is null.");
                        return;
                    }
                    String barcode = intent.getStringExtra("com.motorolasolutions.emdk.datawedge.data_string");
                    String labelType = intent.getStringExtra("com.motorolasolutions.emdk.datawedge.label_type");

                    scanCallback.execute(new BarcodeScan(labelType, barcode));
                } else {
                    if (magstripeCallback == null) {
                        Log.e(TAG, "Magstripe data received, but callback is null.");
                        return;
                    }

                    List<String> tracks = new ArrayList<String>();

                    for (int i=0; i<=2; i++) {
                        byte[] trackData = intent.getByteArrayExtra(String.format(TRACK_PREFIX_FORMAT, i+1));
                        int trackStatus = intent.getIntExtra(String.format(TRACK_STATUS_FORMAT, i+1),0);
                        
                        if (trackStatus == 1) {//1 is valid
                            tracks.add(new String(trackData).trim());
                        } else {
                            tracks.add(null);//prefill with null
                        }
                    }
                    magstripeCallback.execute(tracks);
                    
                }

                
            } catch(Exception ex) {
                Log.e(TAG, "Exception raised during callback processing.", ex);
            }
        }
    };

}
