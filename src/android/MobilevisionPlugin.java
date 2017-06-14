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
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

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
	private static final String TAG = "Mobilevision";
    private static final int FACE_TRACKER_SEC = 0;
    private static final int PERMISSION_DENIED_ERROR = 20;

    public CallbackContext callbackContext;


    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
         if (action.equals("faceTracker")) {
			 faceTracker(args, callbackContext);			 
			 return true;
		 }
		 return false;
	}

	
	public void faceTracker(JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
		requestPermissions();
        launchActivity();

		PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
        r.setKeepCallback(true);
        callbackContext.sendPluginResult(r);
	}

    /**
	 * Ouverture de l'activité face tracker
	 */
	protected void launchActivity() throws JSONException {
        Intent intent = new Intent(this.cordova.getActivity(), FaceTrackerActivity.class);
        this.cordova.getActivity().startActivity(intent);
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

}
