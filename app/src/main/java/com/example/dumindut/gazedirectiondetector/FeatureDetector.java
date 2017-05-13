package com.example.dumindut.gazedirectiondetector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.AsyncTask;
import android.util.Log;
import android.util.SparseArray;
import android.widget.TextView;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.microsoft.projectoxford.emotion.EmotionServiceRestClient;
import com.microsoft.projectoxford.emotion.contract.RecognizeResult;
import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalysisResult;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.System.currentTimeMillis;


/**
 * Feature Detector class which is used to calculate all the features by providing custom
 * frame processing on the google vision camera's preview frames. The feature Detector is
 * wrapped around the main faceDetector and set the other resources(CameraSource and
 * multiProcessor) setting a pipeline structure.
 */
public class FeatureDetector extends Detector<Face> {

    private Detector<Face> mDelegate;
    private TextView resultTextView;
    private TextView feedbackTextView;
    private EmotionServiceRestClient emotionClient;
    private FaceServiceRestClient faceClient;
    private VisionServiceRestClient visionClient;
    private byte[] outputArray;
    private long lastTime;
    private long startTime;
    private long thisFrameTime;
    private int count = 1;

    private int isParentLookingAtChild = -1;
    private int isChildLookingAtParent = -1;
    private int hasEyeContact = -1;
    private int areBothAtSameEyeLevel = -1;
    private int hasJointAttention = -1;
    private float[] parentEmotionVec = new float[8];
    private float[] childEmotionVec = new float[8];
    private float[] unknownEmotionVec = new float[8];


    FeatureDetector(Detector<Face> delegate, TextView textView, TextView textView2, EmotionServiceRestClient client1,
                    FaceServiceRestClient client2, VisionServiceRestClient client3) {
        mDelegate = delegate;
        resultTextView = textView;
        feedbackTextView = textView2;
        emotionClient = client1;
        faceClient = client2;
        visionClient = client3;
        lastTime = currentTimeMillis();
        startTime = lastTime;
    }

    /**
     *All the feature detection happens here while the preview frame from google's camera source is
     * accessed. Custom frame processing takes place at relevant time thresholds or simply returns
     * the frame.
     *
     * @param frame Preview frame from google vision Camera Source. Format NV21
     */
    @Override
    public SparseArray<Face> detect(Frame frame) {
        // *** Custom frame processing code
        //Custom frame processing on the frame once per every threshold seconds approximately
        thisFrameTime = currentTimeMillis();
        if(thisFrameTime-lastTime > Data.FEATURE_DETECTION_TIME_THRESHOLD){
            lastTime = currentTimeMillis();
            outputArray = getByteArray(frame);
            if(Data.isIdentified){
                if(count%Data.AGE_DETECTION_FRAME_COUNT_THRESHOLD == 0){
                    doDifferentiate();
                }
                recognizeFeatures();
                count++;
            }
            else{
                doDifferentiate();
            }
            doRecognizeEmotions();
        }
        return mDelegate.detect(frame);
    }

    public boolean isOperational() {
        return mDelegate.isOperational();
    }

    public boolean setFocus(int id) {
        return mDelegate.setFocus(id);
    }

    /**
     * Calling age detection task to identify parent and child
     */
    public void doDifferentiate(){
        try{
            new ageDetectionTask().execute();
        }
        catch(Exception e){
            resultTextView.setText("Error encountered in age detection : " + e.toString());
            Log.e("Age detection ",e.toString());
        }
    }

    /**
     * Age detection task using Microsoft Cognitive Services Face API.
     */
    private class ageDetectionTask extends AsyncTask<String, String, com.microsoft.projectoxford.face.contract.Face[]> {
        private Exception e = null;
        public ageDetectionTask(){}

        /**
         * Async Task activity, calling the remote face client by sending frame data
         * @param args
         * @return Array including identified faces(objects) with their results
         */
        @Override
        protected com.microsoft.projectoxford.face.contract.Face[] doInBackground(String...args) {
            try {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(outputArray);
                return faceClient.detect(inputStream, false, false,
                        new FaceServiceClient.FaceAttributeType[] {
                                FaceServiceClient.FaceAttributeType.Age,});
            } catch (Exception e) {
                this.e = e;
            }
            return null;
        }

        /**
         * On successful completion of calling client and with the results produced by client
         * starting the differentiating task and storing parent and child information on Data class
         * to be shared with Google's vision face processing.
         * @param result resulting array containing face information
         */
        @Override
        protected void onPostExecute(com.microsoft.projectoxford.face.contract.Face[] result) {
            if (e != null) {
                resultTextView.setText("Error, age detection: " + e.getMessage());
                Log.e("Age detection", e.getMessage());
                this.e = null;
            }
            else if (result.length==1 && !Data.isIdentified) {
                Data.isOnlyOneFace=true;
            }
            else if (result.length==2){
                double x1 = result[0].faceRectangle.left + result[0].faceRectangle.width/2;
                double x2 = result[1].faceRectangle.left + result[1].faceRectangle.width/2;
                double y1 = result[0].faceRectangle.top + result[0].faceRectangle.height/2;
                double y2 = result[1].faceRectangle.top + result[1].faceRectangle.height/2;
                double age1 = result[0].faceAttributes.age;
                double age2 = result[1].faceAttributes.age;
                float width1 = result[0].faceRectangle.width;
                float width2 = result[1].faceRectangle.width;
                float height1 = result[0].faceRectangle.height;
                float height2 = result[1].faceRectangle.height;

                if(age1>age2){
                    Data.Parent.x = (float)x1;
                    Data.Parent.y = (float)y1;
                    Data.Parent.faceWidth = width1;
                    Data.Parent.faceHeight = height1;
                    Data.Child.x = (float)x2;
                    Data.Child.y = (float)y2;
                    Data.Child.faceWidth = width2;
                    Data.Child.faceHeight = height2;
                }
                else{
                    Data.Parent.x = (float)x2;
                    Data.Parent.y = (float)y2;
                    Data.Parent.faceWidth = width2;
                    Data.Parent.faceHeight = height2;
                    Data.Child.x = (float)x1;
                    Data.Child.y = (float)y1;
                    Data.Child.faceWidth = width1;
                    Data.Child.faceHeight = height1;
                }
                Data.isIdentified=true;
                Data.isOnlyOneFace=false;
            }
            else{
                resultTextView.setText("Could not find faces for age detection:(");
                Log.e("Age detection", "Could not find faces");
            }
        }
    }

    /**
     * Executing emotion recognition task
     */
    public void doRecognizeEmotions() {
        // Do emotion detection using auto-detected faces.
        try {
            new emotionDetectionTask().execute();
        }
        catch (Exception e) {
            resultTextView.setText("Error encountered in emotion detection : " + e.toString());
            Log.e("Emotion detection ",e.toString());
        }
    }

    /**
     * Emotion detection task using Microsoft Cognitive Services Emotion API
     */
    private class emotionDetectionTask extends AsyncTask<String, String, List<RecognizeResult>> {
        private Exception e = null;
        public emotionDetectionTask() {}

        /**
         * Calling the emotion client and sending the frame data.
         * @param args
         * @return results including emotions and their associated probability score on identified
         * faces
         */
        @Override
        protected List<RecognizeResult> doInBackground(String... args) {
            try {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(outputArray);
                List<RecognizeResult> result = emotionClient.recognizeImage(inputStream);
                return result;
            } catch (Exception e) {
                this.e = e;    // Store error
            }
            return null;
        }

        /**
         * On the completion of producing results with emotion client, storing emotion results
         * of parent and child and adding to the cumulative vectors.
         * @param result resulting list containing emotions and probability scores of each face.
         */
        @Override
        protected void onPostExecute(List<RecognizeResult> result) {
            super.onPostExecute(result);
            // Display based on error existence
            if (e != null) {
                resultTextView.setText("Error, emotion detection: " + e.getMessage());
                Log.e("Emotion detection ", e.getMessage());
                this.e = null;
            }
            else if (result.size() == 1 && Data.isOnlyOneFace) {
                float[] emotionVec = new float[8];
                RecognizeResult r = result.get(0);

                emotionVec[0] = (float)r.scores.anger;
                emotionVec[1] = (float)r.scores.contempt;
                emotionVec[2] = (float)r.scores.disgust;
                emotionVec[3] = (float)r.scores.fear;
                emotionVec[4] = (float)r.scores.happiness;
                emotionVec[5] = (float)r.scores.neutral;
                emotionVec[6] = (float)r.scores.sadness;
                emotionVec[7] = (float)r.scores.surprise;

                unknownEmotionVec = emotionVec;
            }
            else if (result.size() == 2 && Data.isIdentified) {
                for (RecognizeResult r : result) {
                    float x = r.faceRectangle.left + r.faceRectangle.width/2;
                    float y = r.faceRectangle.top + r.faceRectangle.height/2;

                    float[] emotionVec = new float[8];
                    emotionVec[0] = (float)r.scores.anger;
                    emotionVec[1] = (float)r.scores.contempt;
                    emotionVec[2] = (float)r.scores.disgust;
                    emotionVec[3] = (float)r.scores.fear;
                    emotionVec[4] = (float)r.scores.happiness;
                    emotionVec[5] = (float)r.scores.neutral;
                    emotionVec[6] = (float)r.scores.sadness;
                    emotionVec[7] = (float)r.scores.surprise;

                    float sqrDistanceToParent = Math.abs(x-Data.Parent.x)*Math.abs(x-Data.Parent.x) +
                            Math.abs(y-Data.Parent.y)*Math.abs(y-Data.Parent.y);
                    float sqrDistanceToChild = Math.abs(x-Data.Child.x)*Math.abs(x-Data.Child.x) +
                            Math.abs(y-Data.Child.y)*Math.abs(y-Data.Child.y);

                    if(sqrDistanceToParent < sqrDistanceToChild){
                        //Assigning emotion details to parent
                        parentEmotionVec = emotionVec;
                    }
                    else{
                        //Assigning emotion details to child
                        childEmotionVec = emotionVec;
                    }
                }
            }
            else{
                resultTextView.setText("Could not find two faces for emotion detection :(");
                Log.e("Emotion detection ", " Could not find two faces'");
            }
            showResults(resultTextView);
        }
    }

    /**
     * Rotating the bitmap if the device is orientated vertically
     * @param bitmapSrc Existing bitmap(landscape preview)
     * @return rotated bitmap
     */
    private Bitmap rotateImage(Bitmap bitmapSrc) {
        Matrix matrix = new Matrix();
        matrix.postRotate(270);
        return Bitmap.createBitmap(bitmapSrc, 0, 0,
                bitmapSrc.getWidth(), bitmapSrc.getHeight(), matrix, true);
    }


    /**
     * Recognizing features other than emotions
     *      Whether parent looking at child
     *      Whether child looking at parent
     *      Whether they have eye contact
     *      Whether they have joint attention on probably an object
     *      Whether parent and child ar same eye level
     * The current results are stored and also the buffer is added with current results
     *
     */
    private void recognizeFeatures(){
        isChildLookingAtParent = 0;
        isParentLookingAtChild = 0;
        hasEyeContact = 0;
        areBothAtSameEyeLevel = 0;
        hasJointAttention = 0;

        double thetaThreshold1;   //to y-faceheight*factor
        double thetaThreshold2;     //to y+faceheight*factor
        double thetaThresholdHigh;   //whichever higher among the above two
        double thetaThresholdLow;

        float parentTheta;
        float childTheta;

        float parentEulerZ = Data.Parent.eulerZ;
        float parentEulerY = Data.Parent.eulerY;
        float childEulerZ = Data.Child.eulerZ;
        float childEulerY = Data.Child.eulerY;

        float parentAbsZ = Math.abs(parentEulerZ);
        float childAbsZ = Math.abs(childEulerZ);
        float parentAbsY = Math.abs(parentEulerY);
        float childAbsY = Math.abs(childEulerY);

        float heightChange;

        /*calculating looking directions of parent and child*/

        //All the details according to the person, not the camera.
        //Defining global theta(0-360) taking into consideration isThetaPositive and IsLeft
        if(parentEulerZ<0){
            if(parentEulerY<0){
                parentTheta = 180 + parentAbsZ; // Third Quadrant (180-270) range 180(0)-240(60)
            }
            else{
                parentTheta = parentAbsZ; // First Quadrant (0-90) range 0(0)-60(60)
            }
        }
        else{
            if(parentEulerY<0){
                parentTheta =  180 - parentAbsZ;  // Second Quadrant(90-180) range 120(60)-180(0)
            }
            else{
                parentTheta = 360 - parentAbsZ;  //4th Quadrant (270-360)  range 300(60)-360(0)
            }
        }

        if(childEulerZ<0){
            if(childEulerY<0){
                childTheta = 180 + childAbsZ; // Third Quadrant (180-270) range 180(0)-240(60)
            }
            else{
                childTheta = childAbsZ; // First Quadrant (0-90) range 0(0)-60(60)
            }
        }
        else{
            if(childEulerY<0){
                childTheta =  180 - childAbsZ;  // Second Quadrant(90-180) range 120(60)-180(0)
            }
            else{
                childTheta = 360 - childAbsZ;  //4th Quadrant (270-360)  range 300(60)-360(0)
            }
        }

        /////////////////////////////////////////////////////////////////////////////////////////
        // Detecting whether parent looking at child

        /*Assigning thresholds and checking whether the looking direction(globalTheta) falls in
        * between the thresholds*/
        heightChange = Data.Child.faceHeight*Data.FACE_HEIGHT_FACTOR;
        thetaThreshold1 = Math.atan2(Data.Child.y-heightChange-Data.Parent.y , Data.Child.x-Data.Parent.x);
        thetaThreshold2 = Math.atan2(Data.Child.y+heightChange-Data.Parent.y , Data.Child.x-Data.Parent.x);
        thetaThreshold1 = Math.toDegrees(thetaThreshold1);
        thetaThreshold2 = Math.toDegrees(thetaThreshold2);

        if(thetaThreshold1>thetaThreshold2){
            thetaThresholdHigh = thetaThreshold1;
            thetaThresholdLow = thetaThreshold2;
        }
        else{
            thetaThresholdHigh = thetaThreshold2;
            thetaThresholdLow = thetaThreshold1;
        }

        if(parentAbsY>Data.DIR_LENGTH_THRESHOLD_X_LOOKING_AT_Y){
            //if the two thresholds fall in first and fourth quadrants
            if(thetaThresholdHigh>270 && thetaThresholdLow<90){
                if(parentTheta>thetaThresholdHigh && parentTheta<thetaThresholdLow){
                    isParentLookingAtChild = 1;
                }
            }
            //other cases
            else{
                if(parentTheta>thetaThresholdLow && parentTheta<thetaThresholdHigh){
                    isParentLookingAtChild = 1;
                }
            }
        }

        ///////////////////////////////////////////////////////////////////////////////////////////
        // Checking whether child looking at parent

         /*Assigning thresholds and checking whether the looking direction(globalTheta) falls in
        * between the thresholds*/
        heightChange = Data.Parent.faceHeight*Data.FACE_HEIGHT_FACTOR;
        thetaThreshold1 = Math.atan2(Data.Parent.y-heightChange-Data.Child.y , Data.Parent.x-Data.Child.x);
        thetaThreshold2 = Math.atan2(Data.Parent.y+heightChange-Data.Child.y , Data.Parent.x-Data.Child.x);
        thetaThreshold1 = Math.toDegrees(thetaThreshold1);
        thetaThreshold2 = Math.toDegrees(thetaThreshold2);

        if(thetaThreshold1>thetaThreshold2){
            thetaThresholdHigh = thetaThreshold1;
            thetaThresholdLow = thetaThreshold2;
        }
        else{
            thetaThresholdHigh = thetaThreshold2;
            thetaThresholdLow = thetaThreshold1;
        }

        if(childAbsY>Data.DIR_LENGTH_THRESHOLD_X_LOOKING_AT_Y){
            //if the two thresholds fall in first and fourth quadrants
            if(thetaThresholdHigh>270 && thetaThresholdLow<90){
                if(childTheta>thetaThresholdHigh && childTheta<thetaThresholdLow){
                    isChildLookingAtParent = 1;
                }
            }
            //other cases
            else{
                if(childTheta>thetaThresholdLow && childTheta<thetaThresholdHigh){
                    isChildLookingAtParent = 1;
                }
            }
        }

        //////////////////////////////////////////////////////////////////////////////////////////
        // Checking for eye contact (Simply taking the logical AND between the above two features)
        if(isChildLookingAtParent==1 && isParentLookingAtChild==1){
            hasEyeContact = 1;
        }

        //////////////////////////////////////////////////////////////////////////////////////////
        //Checking whether parent ad child at same eye level
        if(Math.abs(Data.Parent.y-Data.Child.y)< Data.Parent.faceHeight*Data.PARENT_ELEVATION_FACTOR){
            areBothAtSameEyeLevel = 1;
        }


        /////////////////////////////////////////////////////////////////////////////////////////
        // Checking for joint attention
        float dirLengthSum = parentAbsY+childAbsY;
        if(isChildLookingAtParent==0 && isParentLookingAtChild==0 && dirLengthSum > Data.DIR_LENGTH_SUM_THRESHOLD){
            Data.meetX = 0;
            Data.meetY = 0;
            float x1 = Data.Parent.x;
            float y1 = Data.Parent.y;
            float x2 = Data.Child.x;
            float y2 = Data.Child.y;
            double theta1 = Math.toRadians(parentTheta);
            double theta2 = Math.toRadians(childTheta);


            /*Mathematical calculation to detect whether the looking rays meet within the preview
             * frame(padded with a face width/height) */
            double u = (y2 + (x1-x2)*Math.tan(theta2) - y1)/(Math.sin(theta1)-Math.cos(theta1)*Math.tan(theta2));
            double v = (x1 + u*Math.cos(theta1) - x2)/Math.cos(theta2);

            if(u>0 && v>0){//two rays meet
                double meetX = x1 + u*Math.cos(theta1);
                double meetY = y1 + u*Math.sin(theta1);

                if(meetX < Data.previewWidth){
                    if(meetY < Data.previewHeight){
                        //Two rays meet within the camera preview.
                        hasJointAttention=1;
                    }
                }
            }
        }
    }

        /**
     * Showing current results in the results text view in UI
     * @param textView result text view
     */
    public void showResults(TextView textView){
        String resultText = "\n";
        String logText = "";

        logText += (thisFrameTime-startTime)/1000.0f + ", ";
        logText += hasJointAttention+ ", ";
        logText += hasEyeContact + ", ";
        logText += areBothAtSameEyeLevel + ", ";
        logText += isParentLookingAtChild + ", ";
        logText += isChildLookingAtParent + ", ";

        if(Data.isIdentified){
            logText += Data.PARENT + ", ";
            for(int i=0; i<8; i++){
                logText +=String.format("%.10f", parentEmotionVec[i])+ ", ";
            }
            logText += Data.CHILD + ", ";
            for(int i=0; i<7; i++){
                logText += String.format("%.10f", childEmotionVec[i])+ ", ";
            }
            logText += String.format("%.10f", childEmotionVec[7]);
        }
        else if (Data.isOnlyOneFace){
            logText += Data.UNKNOWN + ", ";
            for(int i=0; i<8; i++){
                logText +=String.format("%.10f", unknownEmotionVec[i])+ ", ";
            }
            logText += Data.NONE + ", ";
            for(int i=0; i<7; i++){
                logText += String.format("%.10f", 0.0)+ ", ";
            }
            logText += String.format("%.10f", 0.0);
        }
        else{
            logText += Data.NONE + ", ";
            for(int i=0; i<8; i++){
                logText +=String.format("%.10f", 0.0)+ ", ";
            }
            logText += Data.NONE + ", ";
            for(int i=0; i<7; i++){
                logText += String.format("%.10f", 0.0)+ ", ";
            }
            logText += String.format("%.10f", 0.0);
        }


        try{
            Data.txtFileWriter.append(System.lineSeparator());
            Data.txtFileWriter.append(logText);
            Data.txtFileWriter.flush();
            Log.e("log",logText);
        }
        catch(IOException e){
            Log.e("Writing to File",e.toString());
        }

        resultText += "Parent looking at child : " + isParentLookingAtChild;
        resultText += "\nChild looking at parent : " + isChildLookingAtParent;
        resultText += "\nEye Contact : " + hasEyeContact;
        resultText += "\nBoth at same eye level : " + areBothAtSameEyeLevel;
        resultText += "\nJoint Attention : " + hasJointAttention;
        if(Data.isIdentified){
            resultText += "\nParent Emotion Vec : " + Arrays.toString(parentEmotionVec);
            resultText += "\nChild Emotion Vec : " + Arrays.toString(childEmotionVec);
        }
        else if (Data.isOnlyOneFace){
            resultText += "\nUndeterminate Emotion Vec : " + Arrays.toString(unknownEmotionVec);
        }
        else{
            resultText += "\nNO Emotions";
        }
        textView.setText(resultText);
        Log.d("  ", resultText);
    }

    /**
     * Since all the microsoft api s need the preview frame to be converted to an byte array first,
     * doing the conversion here at once without repeating again and again. This method is called
     * at the beginning of frame processing
     * @param frame
     * @return
     */
    private byte[] getByteArray(Frame frame) {
        int width = frame.getMetadata().getWidth();
        int height = frame.getMetadata().getHeight();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ByteBuffer byteBuffer = frame.getGrayscaleImageData();
        YuvImage yuvimage = new YuvImage(byteBuffer.array(), ImageFormat.NV21, width, height, null);
        yuvimage.compressToJpeg(new Rect(0, 0, width, height), 100, output);
        byte[] outputArray = output.toByteArray();

        //If portrait, changing the outputArray
        if (frame.getMetadata().getRotation() == 3) {
            Bitmap bmp = BitmapFactory.decodeByteArray(outputArray, 0, outputArray.length);
            Bitmap rotatedBmp = rotateImage(bmp);
            ByteArrayOutputStream output2 = new ByteArrayOutputStream();
            rotatedBmp.compress(Bitmap.CompressFormat.JPEG, 100, output2);
            outputArray = output2.toByteArray();
        }
        return outputArray;
    }

}