package org.apache.cordova.mobilevision;

import org.apache.cordova.BuildHelper;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.Manifest;
import android.graphics.Matrix;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.Context;
import android.content.res.ColorStateList;
import android.support.design.widget.Snackbar;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class FaceTrackerActivity extends AppCompatActivity implements CameraSource.ShutterCallback, CameraSource.PictureCallback {
    private static final String TAG = "FaceTrackerActivity";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private FloatingActionButton mButtonTakePhoto;
    private TextView mTextViewCamera;
    
    private static final int RC_HANDLE_GMS = 9001;

    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private AtomicBoolean isTakePhotoEnable = new AtomicBoolean(false);

    private int quality;
    private int colorOK;
    private int colorKO;
    private ColorStateList colorStateListOK;
    private ColorStateList colorStateListKO;
    private String messageTakePhotoOK;
    private String messageTakePhotoKO;
    private float minFaceSize;

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final String packageName = getApplication().getPackageName();
        setContentView(getApplication().getResources().getIdentifier("mobilevision_main", "layout", packageName));
        mPreview = (CameraSourcePreview) findViewById(getApplication().getResources().getIdentifier("preview", "id", packageName));
		mGraphicOverlay = (GraphicOverlay) findViewById(getApplication().getResources().getIdentifier("faceOverlay", "id", packageName));
        mButtonTakePhoto = (FloatingActionButton) findViewById(getApplication().getResources().getIdentifier("buttonTakePhoto", "id", packageName));
        mTextViewCamera = (TextView) findViewById(getApplication().getResources().getIdentifier("textCamera", "id", packageName));

        quality = getIntent().getExtras().getInt("quality");
        colorOK = Color.parseColor(getIntent().getExtras().getCharSequence("colorOK").toString());
        colorKO = Color.parseColor(getIntent().getExtras().getCharSequence("colorKO").toString());
        colorStateListOK = ColorStateList.valueOf(colorOK);
        colorStateListKO = ColorStateList.valueOf(colorKO);
        messageTakePhotoOK = getIntent().getExtras().getCharSequence("messageTakePhotoOK").toString();
        messageTakePhotoKO = getIntent().getExtras().getCharSequence("messageTakePhotoKO").toString();
        minFaceSize = getIntent().getExtras().getFloat("minFaceSize");

        Log.d(TAG, "quality="+ quality);
        Log.d(TAG, "colorOK="+ colorOK);
        Log.d(TAG, "colorKO="+ colorKO);
        Log.d(TAG, "messageTakePhotoOK="+ messageTakePhotoOK);
        Log.d(TAG, "messageTakePhotoKO="+ messageTakePhotoKO);
        Log.d(TAG, "minFaceSize="+ minFaceSize);

        disableTakePhoto();
        mButtonTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTakePicture(v);
            }
        });

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, "Access to the camera is needed for detection",
                Snackbar.LENGTH_INDEFINITE)
                .setAction("OK", listener)
                .show();
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {
        Log.i(TAG, "createCameraSource");

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                //.setClassificationType(FaceDetector.NO_LANDMARKS)
                .setClassificationType(FaceDetector.NO_CLASSIFICATIONS)
                .setLandmarkType (FaceDetector.ALL_LANDMARKS)
                .setMinFaceSize(minFaceSize)
                .setProminentFaceOnly(true)
                .build();

        detector.setProcessor(new LargestFaceFocusingProcessor(detector, new GraphicFaceTracker(mGraphicOverlay)));

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.i(TAG, "Face detector dependencies are not yet available.");
        }
        else {
            Log.i(TAG, "Face detector dependencies available.");
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        int widthPixel = Math.min(metrics.heightPixels, metrics.widthPixels);
        int heightPixels = Math.min(metrics.heightPixels, metrics.widthPixels);
        mCameraSource = new CameraSource.Builder(context, detector)
                //.setRequestedPreviewSize(640, 480)
                .setRequestedPreviewSize(widthPixel, heightPixels)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(20.0f)
                .build();
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, ">>onPause");
        mPreview.stop();
        Log.d(TAG, "<<onPause");
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, ">>onDestroy");
        if (mCameraSource != null) {
            mCameraSource.release();
        }
        Log.d(TAG, "<<onDestroy");
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage("This application cannot run because it does not have the camera permission.  The application will now exit.")
                .setPositiveButton("OK", listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
            if (!isTakePhotoEnable.get()) {
                isTakePhotoEnable.set(true);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isTakePhotoEnable.get()) {
                            enableTakePhoto();
                        }
                        
                    }
                });
            }
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
            if (isTakePhotoEnable.get()) {
                isTakePhotoEnable.set(false);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isTakePhotoEnable.get()) {
                            disableTakePhoto();
                        }
                    }
                });
            }
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
            if (isTakePhotoEnable.get()) {
                isTakePhotoEnable.set(false);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isTakePhotoEnable.get()) {
                            disableTakePhoto();
                        }
                    }
                });
            }
        }
    }

    public void onTakePicture(View v) {
        mCameraSource.takePicture(this, this);
    }
    
    @Override
    public void onShutter() {
        Log.i(TAG, "onShutter");
        //Snackbar.make(findViewById(android.R.id.content), "Photo prise", Snackbar.LENGTH_SHORT).setActionTextColor(Color.RED).show();
    }

    private void disableTakePhoto() {
        mTextViewCamera.setText(messageTakePhotoKO);
        mTextViewCamera.setTextColor(colorKO);
        //mTextViewCamera.setBackgroundColor(colorKO);
        mButtonTakePhoto.setEnabled(false);
        //mButtonTakePhoto.setImageResource(R.drawable.camera_off);
        mButtonTakePhoto.setImageResource(getApplication().getResources().getIdentifier("camera_off", "drawable", getApplication().getPackageName()));
        mButtonTakePhoto.setBackgroundTintList(colorStateListKO);
        mButtonTakePhoto.setEnabled(false);
    }

    private void enableTakePhoto() {
        mTextViewCamera.setText(messageTakePhotoOK);
        mTextViewCamera.setTextColor(colorOK);
        //mTextViewCamera.setBackgroundColor(colorOK);
        mButtonTakePhoto.setEnabled(true);
        //mButtonTakePhoto.setImageResource(R.drawable.camera);
        mButtonTakePhoto.setImageResource(getApplication().getResources().getIdentifier("camera", "drawable", getApplication().getPackageName()));
        mButtonTakePhoto.setBackgroundTintList(colorStateListOK);
        mButtonTakePhoto.setEnabled(true);
    }

    
    @Override
    public void onPictureTaken(byte[] data) {
        Log.i(TAG, "onPictureTaken taille du buffer=" + ((data == null) ? 0 : data.length));

        // Pas d'image
        if (data == null || data.length == 0) {
            Log.e(TAG, "Pas d'image");
            setResult(2, new Intent());
            finish();
            return;
        }

        // Traitement de l'image
        int maxSize = 320;
        mPreview.stop();

        //  Before making an actual bitmap, check size
		//  If the bitmap's size is too large,out of memory occurs.
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        Bitmap loadedImage = BitmapFactory.decodeByteArray(data, 0, data.length, opt);
        Log.d(TAG, "Taille image orignale width=" + opt.outWidth + " height=" + opt.outHeight);

        // Scaling image
        int srcSize = Math.max(opt.outWidth, opt.outHeight);        
        opt.inSampleSize = maxSize < srcSize ? (srcSize / maxSize) : 1;
        Log.d(TAG, "Options sample size " + opt.inSampleSize);
        opt.inJustDecodeBounds = false;
        Bitmap scaledBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opt);
        Log.d(TAG, "Taille image scaled width=" + opt.outWidth + " height=" + opt.outHeight);
        
        // Rotate image 90
        Matrix matrix = new Matrix();
        matrix.postRotate(180+90);
        Bitmap rotatedBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
        Log.d(TAG, "Taille image rotated width=" + rotatedBitmap.getWidth() + " height=" + rotatedBitmap.getHeight());

        // compression
        int mQuality = 100;
        ByteArrayOutputStream jpegCompress = new ByteArrayOutputStream();
        if (rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, mQuality, jpegCompress)) {
            byte[] bufPictureTakenCompress = jpegCompress.toByteArray();
            Log.d(TAG, "Taille image compresse : " + bufPictureTakenCompress.length);

            Intent intent = new Intent();
            intent.putExtra("picture", bufPictureTakenCompress);
            setResult(RESULT_OK, intent);
        } else {
            Log.e(TAG, "Impossible de compresser l'image");
            setResult(2, new Intent());
        }

        finish();
    }
}