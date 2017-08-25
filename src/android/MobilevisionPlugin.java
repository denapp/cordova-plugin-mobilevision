package org.apache.cordova.mobilevision;

import org.apache.cordova.BuildHelper;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.LOG;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManager;
import android.view.Window;
import android.util.Base64;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

/**
 * @author Denis Apparicio
 *
 */
public class MobilevisionPlugin extends CordovaPlugin {
	private static final String TAG = "MobilevisionPlugin";
    private static final int FACE_TRACKER_SEC = 0;
    private static final int PERMISSION_DENIED_ERROR = 20;

    public static final int REQUEST_CODE = 0x0ba7c1ae;

    public CallbackContext callbackContext;

    private int quality;
    private String colorOK;
    private String colorKO;
    private String messageTakePhotoOK;
    private String messageTakePhotoKO;
    private float minFaceSize;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
         if (action.equals("faceTracker")) {
             
			 faceTracker(args, callbackContext);			 

             PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
             r.setKeepCallback(true);
             callbackContext.sendPluginResult(r);

			 return true;
		 }
		 return false;
	}

	public void faceTracker(JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        LOG.d(TAG, "args="+ args);
        
        // Recuperation des parametres
        try {
            quality = args.getInt(0); 
            colorOK =  args.getString(1);
            colorKO =  args.getString(2);
            messageTakePhotoOK = args.getString(3);
            messageTakePhotoKO = args.getString(4);
            minFaceSize = (float) args.getDouble(5);
        }
        catch(JSONException e) {
            LOG.d(TAG, "JSONException parse", e);
            throw e;
        }
        LOG.d(TAG, "quality="+ quality);
        LOG.d(TAG, "colorOK="+ colorOK);
        LOG.d(TAG, "colorKO="+ colorKO);
        LOG.d(TAG, "messageTakePhotoOK="+ messageTakePhotoOK);
        LOG.d(TAG, "messageTakePhotoKO="+ messageTakePhotoKO);
        LOG.d(TAG, "minFaceSize="+ minFaceSize);

        // Permissions et lancement de l activite 
		requestPermissions();
        launchActivity();

		PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
        r.setKeepCallback(true);
        callbackContext.sendPluginResult(r);
	}

    private int getInt(JSONArray args, int index, int defaultValue) {
        try { return args.getInt(index); } catch(Throwable e) { return defaultValue;}
    }

    private float getFloat(JSONArray args, int index, float defaultValue) {
        try { return (float) args.getDouble(index); } catch(Throwable e) { return defaultValue;}
    }
    
    private String getString(JSONArray args, int index, String defaultValue) {
        try { return args.getString(index); } catch(Throwable e) { return defaultValue;}
    }


    /**
	 * Ouverture de l'activité face tracker
	 */
	protected void launchActivity() throws JSONException {
        Intent intent = new Intent(cordova.getActivity().getBaseContext(), FaceTrackerActivity.class);
        intent.putExtra("quality", quality);
        intent.putExtra("colorOK", colorOK);
        intent.putExtra("colorKO", colorKO);
        intent.putExtra("messageTakePhotoOK", messageTakePhotoOK);
        intent.putExtra("messageTakePhotoKO", messageTakePhotoKO);
        intent.putExtra("minFaceSize", minFaceSize);


        intent.setPackage(cordova.getActivity().getApplicationContext().getPackageName());

        LOG.d(TAG, "launchActivity");
        cordova.startActivityForResult((CordovaPlugin) this, intent, REQUEST_CODE);
    }

	/**
	 * Check permissions
	 */
    private void requestPermissions() {
        boolean cameraPermission = PermissionHelper.hasPermission(this, Manifest.permission.CAMERA);

        // CB-10120: The CAMERA permission does not need to be requested unless it is declared
        // in AndroidManifest.xml. This plugin does not declare it, but others may and so we must
        // check the package info to determine if the permission is present.
        if (!cameraPermission) {
            cameraPermission = true;
            try {
                PackageManager packageManager = this.cordova.getActivity().getPackageManager();
                String[] permissionsInPackage = packageManager.getPackageInfo(this.cordova.getActivity().getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
                if (permissionsInPackage != null) {
                    for (String permission : permissionsInPackage) {
                        if (permission.equals(Manifest.permission.CAMERA)) {
                            cameraPermission = false;
                            break;
                        }
                    }
                }
            } catch (NameNotFoundException e) {
                // We are requesting the info for our package, so this should
                // never be caught
            }
        }

        if (!cameraPermission) {
            PermissionHelper.requestPermission(this, FACE_TRACKER_SEC, Manifest.permission.CAMERA);
        }
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
                return;
            }
        }
        switch (requestCode) {
            case FACE_TRACKER_SEC:
                launchActivity();
                break;
        }
    }

    /**
     * Called when the FacrTrackerActivity exits.
     *
     * @param requestCode The request code originally supplied to startActivityForResult(),
     *                    allowing you to identify who this result came from.
     * @param resultCode  The integer result code returned by the child activity through its setResult().
     * @param intent      An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {        
        LOG.d(TAG, "onActivityResult requestCode="+ requestCode +" resultCode="+resultCode);

        if (requestCode == REQUEST_CODE && this.callbackContext != null) {   
            LOG.d(TAG, "result");
            
            if (resultCode == Activity.RESULT_CANCELED) {
                LOG.d(TAG, "Cancel capture.");
                callbackContext.error("Cancel capture.");
                return;
            }

            if (resultCode != Activity.RESULT_OK) {
                LOG.e(TAG, "Error return image resultCode="+resultCode);
                callbackContext.error("Error return image.");
                return;
            }
                
            // recuperation de l'image encode compresse
            byte[] image = intent.getByteArrayExtra("picture");
            if (image == null) {
                LOG.e(TAG, "Unable to create bitmap!");
                callbackContext.error("Unable to create bitmap!");
                return;
            }
            
            LOG.d(TAG, "picture=" +  new String(Base64.encode(image, Base64.NO_WRAP)));

            try {
                // Image au format jpeg
                byte[] output = Base64.encode(image, Base64.NO_WRAP);
                String jsOut = new String(output);

                // SUCESS image restitué.                
                callbackContext.success(jsOut);

                jsOut = null;
                output = null;
                image = null;
            } catch (Exception e) {
                LOG.e(TAG, "Error return image.");
                callbackContext.error("Error return image.");
            }
        }
        else {
            LOG.e(TAG, "onActivityResult Error.");
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }
}
