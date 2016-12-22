package com.example.dumindut.gazedirectiondetector;

/**
 * Created by dumindut on 29/8/2016.
 */

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.dumindut.gazedirectiondetector.ui.camera.CameraSourcePreview;
import com.example.dumindut.gazedirectiondetector.ui.camera.GraphicOverlay;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.MultiDetector;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor;
import com.microsoft.projectoxford.emotion.EmotionServiceRestClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;

import java.io.IOException;


public final class FaceTrackerActivity extends AppCompatActivity {
    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private TextView resultTextView;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private boolean mIsFrontFacing = true;

    public EmotionServiceRestClient client;
    public FaceServiceRestClient faceClient;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        if (client == null) {
           client = new EmotionServiceRestClient(getString(R.string.emotion_subscription_key));
        }

        if(faceClient == null){
            faceClient = new FaceServiceRestClient(getString(R.string.face_subscription_key));
        }

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        resultTextView = (TextView) findViewById(R.id.result_text_view);

        final Button flipButton = (Button) findViewById(R.id.flipButton);
        final Button resetButton = (Button) findViewById(R.id.resetButton);
        flipButton.setOnClickListener(mFlipButtonListener);
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

    private void createCameraSource() {

        Context context = getApplicationContext();

        // Face Detector for face detection and processing
        FaceDetector faceDetector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setLandmarkType (FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.ACCURATE_MODE)
                .build();

        //face detector for emotion detection
        FaceDetector faceDetector1 = new FaceDetector.Builder(context).build();

        // facedetector1 is wrapped with emotion detector
        EmotionDetector emotionDetector = new EmotionDetector(faceDetector1,resultTextView,client,faceClient);

        //Setting processors for the two detectors
        faceDetector.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                .build());

        emotionDetector.setProcessor(new LargestFaceFocusingProcessor.Builder(faceDetector1,new FaceTracker())
                .build());

        //multi detector for both face and emotion detectors
        MultiDetector multiDetector = new MultiDetector.Builder()
                .add(faceDetector)
                .add(emotionDetector)
                .build();

        if (!multiDetector.isOperational()) {
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        int facing = CameraSource.CAMERA_FACING_FRONT;
        if (!mIsFrontFacing) {
            facing = CameraSource.CAMERA_FACING_BACK;
        }

        mCameraSource = new CameraSource.Builder(context, multiDetector)
                .setRequestedPreviewSize(640,480)
                .setFacing(facing)
                .setRequestedFps(30f)
                .setAutoFocusEnabled(true)
                .build();
    }

     @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
     }

      @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

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


    /**
     * Toggles between front-facing and rear-facing modes.
     */
    private View.OnClickListener mFlipButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            mIsFrontFacing = !mIsFrontFacing;

            if (mCameraSource != null) {
                mCameraSource.release();
                mCameraSource = null;
            }
            Data.clearData();
            resultTextView.setText("Results");
            createCameraSource();
            startCameraSource();
        }
    };

    private View.OnClickListener mResetButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mCameraSource != null) {
                mCameraSource.release();
                mCameraSource = null;
            }
            Data.clearData();
            resultTextView.setText("Results");
            createCameraSource();
            startCameraSource();
        }
    };


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


        /*Start tracking the detected face instane within the face overlay.*/
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

    // new Face Tracker to the processor of emotion detector.
    class FaceTracker extends Tracker<Face> {
        public void onNewItem(int id, Face face) {
        }

        public void onUpdate(Detector.Detections<Face> detections, Face face) {
        }

        public void onDone() {
        }
    }
}