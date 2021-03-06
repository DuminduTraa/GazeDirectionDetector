package com.example.dumindut.gazedirectiondetector;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.dumindut.gazedirectiondetector.ui.camera.CameraSourcePreview;
import com.example.dumindut.gazedirectiondetector.ui.camera.GraphicOverlay;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.microsoft.projectoxford.emotion.EmotionServiceRestClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Face Tracking activity. Currently the main activity in the app
 */
public final class FaceTrackerActivity extends AppCompatActivity {
    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private TextView resultTextView;
    private TextView feedbackTextView;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private boolean mIsFrontFacing;

    public EmotionServiceRestClient emotionClient;
    public FaceServiceRestClient faceClient;
    public VisionServiceRestClient visionClient;

    //private FileWriter txtFileWriter;

    /**
     * Initializes the UI and initiates the creation of a face detector
     * Microsoft API clients are initiated in onCreate
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Intent intent = getIntent();
        mIsFrontFacing = intent.getBooleanExtra("isFrontFacing",true);

        if(isExternalStorageWritable()){
            String fileName = new SimpleDateFormat("yyyyMMddHHmm'.txt'").format(new Date());
            try{
                File txtFileFolder = getFileStorageDir("Parent Child Monitor");
                File txtFile = new File(txtFileFolder,fileName);
                Data.txtFileWriter = new FileWriter(txtFile);
            }
            catch (IOException E){
                Toast.makeText(FaceTrackerActivity.this, "could not open the file writer", Toast.LENGTH_SHORT).show();
            }
        }
        else{
            Toast.makeText(FaceTrackerActivity.this, "External Storage not available!!!", Toast.LENGTH_SHORT).show();
        }


        //API calls
        if (emotionClient == null) {
           emotionClient = new EmotionServiceRestClient(getString(R.string.emotion_subscription_key));
        }
        if(faceClient == null){
            faceClient = new FaceServiceRestClient(getString(R.string.face_subscription_key));
        }
        if (visionClient == null){
            visionClient = new VisionServiceRestClient(getString(R.string.face_subscription_key));
        }

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        resultTextView = (TextView) findViewById(R.id.result_text_view);
        feedbackTextView = (TextView) findViewById(R.id.feedback_text_View);

        final Button stopButton = (Button) findViewById(R.id.stopButton);
        final Button resetButton = (Button) findViewById(R.id.resetButton);
        stopButton.setOnClickListener(mStopButtonListener);
        resetButton.setOnClickListener(mResetButtonListener);

        if (savedInstanceState != null) {
            mIsFrontFacing = savedInstanceState.getBoolean("IsFrontFacing");
        }

        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
    }

    public File getFileStorageDir(String fileName) {
        // Get the directory for the user's public documents directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), fileName);
        if (!file.mkdirs()) {
            Log.e("file", "Directory not created");
        }
        return file;
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }


    /**
     * Requesting camera permission. Manifest should include the camera permission
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

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    /**
     * Creating the camera source in conjunction with the multi processor and face detector of
     * Google vision API
     */
    private void createCameraSource() {

        Context context = getApplicationContext();

        // Face Detector for face detection and processing
        FaceDetector faceDetector = new FaceDetector.Builder(context)
                .setMode(FaceDetector.ACCURATE_MODE)
                .build();

        // faceDetector is wrapped with feature detector
        FeatureDetector featureDetector = new FeatureDetector(faceDetector,resultTextView,feedbackTextView,
                                                emotionClient,faceClient,visionClient);

        //Setting processor for the detector
        featureDetector.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                .build());

        if (!faceDetector.isOperational()) {
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        int facing = CameraSource.CAMERA_FACING_FRONT;
        if (!mIsFrontFacing) {
            facing = CameraSource.CAMERA_FACING_BACK;
        }

        mCameraSource = new CameraSource.Builder(context, featureDetector)
                .setRequestedPreviewSize(Data.PREVIEW_WIDTH,Data.PREVIEW_HEIGHT)
                .setFacing(facing)
                .setRequestedFps(Data.REQUESTED_FRAME_RATE)
                .setAutoFocusEnabled(true)
                .build();
    }

    /**
     * Restarts the camera.
     */
     @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
     }

    /**
     * Stops the camera.
     */
      @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
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
            // we have permission, sno create the camerasource
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
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    /**
     * Saves the camera facing mode, so that it can be restored after the device is rotated.
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean("IsFrontFacing", mIsFrontFacing);
    }


    /*
    OnClick Listener for the reset button. the static variables in Data class are cleared.
     */
    private View.OnClickListener mResetButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mCameraSource != null) {
                mCameraSource.release();
                mCameraSource = null;
            }
            Data.clearData();
            resultTextView.setText("Results");
            feedbackTextView.setText("Feedback");
            createCameraSource();
            startCameraSource();
        }
    };

    /**
     * Stopping the processing and sae the log file
     */
    private View.OnClickListener mStopButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mCameraSource != null) {
                mCameraSource.release();
                mCameraSource = null;
            }
            try{
                Data.txtFileWriter.close();
            }
            catch(IOException E){
                Toast.makeText(FaceTrackerActivity.this, "Could not close the file!!!", Toast.LENGTH_SHORT).show();
            }
            Data.clearData();
            resultTextView.setText("Results");
            feedbackTextView.setText("Feedback");

            Intent intent = new Intent(FaceTrackerActivity.this,MainActivity.class);
            startActivity(intent);

        }
    };

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * this will be called again when the camera source is created.
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

    /* Graphic Face Tracker */

    /*Factory for creating a face tracker to track a face. The multiprocessor
    * uses this factory to create faceTrackers for each face.*/
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /*Face tracker for each face, maintaining a face graphic within the app's
    * associated face overlay*/
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay,mIsFrontFacing);
        }


        /*Start tracking the detected face instance within the face overlay.*/
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /*Update the position and characteristics of the face within the overlay*/
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
        }

        /*Hides the graphic when the face is not detected.*/
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /*Called when the face is assumed to be gone for good. Remove the graphic
        * annotation from the overlay.*/
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }
}