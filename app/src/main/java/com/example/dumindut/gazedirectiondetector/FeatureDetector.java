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
    private boolean startFeedbacking = false;

    private int isParentLookingAtChild = 0;
    private int isChildLookingAtParent = 0;
    private int hasEyeContact = 0;
    private int areBothAtSameEyeLevel = 0;
    private int hasJointAttention = 0;
    private float[] parentEmotionVec = new float[8];
    private float[] childEmotionVec = new float[8];

    CircularFifoQueue<Integer> parentLookingAtChildBuffer = new CircularFifoQueue<Integer>(Data.AVERAGING_WINDOW_LENGTH);
    CircularFifoQueue<Integer> childLookingAtParentBuffer = new CircularFifoQueue<Integer>(Data.AVERAGING_WINDOW_LENGTH);
    CircularFifoQueue<Integer> eyeContactBuffer = new CircularFifoQueue<Integer>(Data.AVERAGING_WINDOW_LENGTH);
    CircularFifoQueue<Integer> bothAtSameEyeLevelBuffer = new CircularFifoQueue<Integer>(Data.AVERAGING_WINDOW_LENGTH);
    CircularFifoQueue<Integer> jointAttentiontBuffer = new CircularFifoQueue<Integer>(Data.AVERAGING_WINDOW_LENGTH);

    private float[] cumParentEmotionVec = {0,0,0,0,0,0,0,0};
    private float[] cumChildEmotionVec = {0,0,0,0,0,0,0,0};

    private float cropWidth = Data.Parent.faceWidth*Data.CROP_DIMENSION_FACTOR;
    private float cropHeight = Data.Parent.faceHeight*Data.CROP_DIMENSION_FACTOR;

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
            if(Data.isIdentified){
                lastTime = currentTimeMillis();
                outputArray = getByteArray(frame);
                if(count%Data.AGE_DETECTION_FRAME_COUNT_THRESHOLD == 0){
                    doDifferentiate();
                }
                recognizeFeatures();
                doRecognizeEmotions();

                //flagging for feedback and start feed backing with the 2 min buffer get filled.
                if(count%Data.AVERAGING_FRAME_COUNT_THRESHOLD == 0 && startFeedbacking) {
                    doFeedback();
                    count = 0;
                }

                if(count%(Data.AVERAGING_WINDOW_LENGTH)==0 && !startFeedbacking){
                    doFeedback();
                    startFeedbacking = true;
                    count = 0;
                }

                count++;
            }
            //Differentiating between parent and child using age detection for the first time
            else{
                lastTime = currentTimeMillis();
                outputArray = getByteArray(frame);
                doDifferentiate();
            }
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
         * On succesfull completion of calling client and with the results produced by client
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
            else if (result.length!=2) {
                resultTextView.setText("Could not find two faces for age detection:(");
                Log.e("Age detection", "Could not find two faces");
            }
            else {
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
            else if (result.size() != 2) {
                resultTextView.setText("Could not find two faces for emotion detection :(");
                Log.e("Emotion detection ", " Could not find two faces'");
            }
            else {
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

                    //Checking whether the face corresponds to the parent or the child.
                    if(Math.abs(x-Data.Parent.x)<Data.X_DIF_THRESHOLD && Math.abs(y-Data.Parent.y)<Data.Y_DIF_THRESHOLD){
                        //Assigning emotion details to parent
                        parentEmotionVec = emotionVec;
                        for(int i=0; i<8; i++){
                            float temp = cumParentEmotionVec[i];
                            cumParentEmotionVec[i] = emotionVec[i] + temp;
                        }
                    }
                    else if(Math.abs(x-Data.Child.x)<Data.X_DIF_THRESHOLD && Math.abs(y-Data.Child.y)<Data.Y_DIF_THRESHOLD){
                        //Assigning emotion details to child
                        childEmotionVec = emotionVec;
                        for(int i=0; i<8; i++){
                            float temp = cumChildEmotionVec[i];
                            cumChildEmotionVec[i] = emotionVec[i] + temp;
                        }
                    }
                    else{}
                }
                showResults(resultTextView);
            }
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
        double thetaThresholdHigh;
        double thetaThresholdLow;
        float globalTheta;
        float heightChange;


        /////////////////////////////////////////////////////////////////////////////////////////
        // Detecting whether parent looking at child

        /*Assigning thresholds and checking whether the looking direction(globalTheta) falls in
        * between the thresholds*/
        heightChange = Data.Child.faceHeight*Data.FACE_HEIGHT_FACTOR;
        globalTheta = Data.Parent.globalTheta;
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
        //if the two thresholds fall in first and fourth quadrants
        if(thetaThresholdHigh>270 && thetaThresholdLow<90){
            if(globalTheta>thetaThresholdHigh && globalTheta<thetaThresholdLow){
                isParentLookingAtChild = 1;
            }
        }
        //other cases
        else{
            if(globalTheta>thetaThresholdLow && globalTheta<thetaThresholdHigh){
                isParentLookingAtChild = 1;
            }
        }
        //Updating the buffer
        parentLookingAtChildBuffer.add(isParentLookingAtChild);

        ///////////////////////////////////////////////////////////////////////////////////////////
        // Checking whether child looking at parent

         /*Assigning thresholds and checking whether the looking direction(globalTheta) falls in
        * between the thresholds*/
        heightChange = Data.Parent.faceHeight*Data.FACE_HEIGHT_FACTOR;
        globalTheta = Data.Child.globalTheta;
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
        //if the two thresholds fall in first and fourth quadrants
        if(thetaThresholdHigh>270 && thetaThresholdLow<90){
            if(globalTheta>thetaThresholdHigh && globalTheta<thetaThresholdLow){
                isChildLookingAtParent = 1;
            }
        }
        //other cases
        else{
            if(globalTheta>thetaThresholdLow && globalTheta<thetaThresholdHigh){
                isChildLookingAtParent = 1;
            }
        }
        //Updating the buffer
        childLookingAtParentBuffer.add(isChildLookingAtParent);


        //////////////////////////////////////////////////////////////////////////////////////////
        // Checking for eye contact (Simply taking the logical AND between the above two features)
        if(isChildLookingAtParent==1 && isParentLookingAtChild==1){
            hasEyeContact = 1;
        }
        eyeContactBuffer.add(hasEyeContact);

        //////////////////////////////////////////////////////////////////////////////////////////
        //Checking whether parent ad child at same eye level
        if(Math.abs(Data.Parent.y-Data.Child.y)< Data.Parent.faceHeight*Data.PARENT_ELEVATION_FACTOR){
            areBothAtSameEyeLevel = 1;
        }
        bothAtSameEyeLevelBuffer.add(areBothAtSameEyeLevel);



        /////////////////////////////////////////////////////////////////////////////////////////
        // Checking for joint attention
        if(isChildLookingAtParent==0 && isParentLookingAtChild==0){
            Data.meetX = 0;
            Data.meetY = 0;
            float x1 = Data.Parent.x;
            float y1 = Data.Parent.y;
            float x2 = Data.Child.x;
            float y2 = Data.Child.y;
            double theta1 = Math.toRadians(Data.Parent.globalTheta);
            double theta2 = Math.toRadians(Data.Child.globalTheta);


            /*Mathematical calculation to detect whether the looking rays meet within the preview
             * frame(padded with a face width/height) */
            double u = (y2 + (x1-x2)*Math.tan(theta2) - y1)/(Math.sin(theta1)-Math.cos(theta1)*Math.tan(theta2));
            double v = (x1 + u*Math.cos(theta1) - x2)/Math.cos(theta2);

            if(u>0 && v>0){//two rays meet
                double meetX = x1 + u*Math.cos(theta1);
                double meetY = y1 + u*Math.sin(theta1);

                if(meetX < Data.previewWidth-cropWidth/2 && meetX > cropWidth/2){
                    if(meetY < Data.previewHeight-cropHeight/2 && meetY > cropHeight/2){
                        //Two rays meet within the camera preview.
                        Data.meetX = (float)meetX;
                        Data.meetY = (float)meetY;
                        //Describing the suspicious area with Microsoft Cognitive Services Object detection
                        doDescribe();
                    }
                    else{jointAttentiontBuffer.add(0);}
                }
                else{jointAttentiontBuffer.add(0);}
            }
            else{jointAttentiontBuffer.add(0);}
        }
        else{jointAttentiontBuffer.add(0);}
    }

    /**
     * Executing object detection task in order to describe the image portion
     */
    public void doDescribe() {
        try {
            new objectDetectionTask().execute();
        }
        catch (Exception e)
        {
            resultTextView.setText("Error encountered in object detection " + e.toString());
            Log.e("Object detection ",e.toString());
            jointAttentiontBuffer.add(0);
        }
    }

    /**
     * Object Detection task using Microsoft Cognitive Services Computer Vision API
     */
    private class objectDetectionTask extends AsyncTask<String, String, AnalysisResult> {
        // Store error message
        private Exception e = null;
        public objectDetectionTask() {}

        /**
         * Calling the computer vision client and sending the frame data of the cropped area
         * @param args
         * @return results of object detection including caption, tags etc.
         */
        @Override
        protected AnalysisResult doInBackground(String... args) {
            try {
                float crop_x = Data.meetX-cropWidth/2;
                float crop_y = Data.meetY-cropHeight/2;
                float crop_width = cropWidth;
                float crop_height = cropHeight;

                Bitmap fullBitmap = BitmapFactory.decodeByteArray(outputArray, 0, outputArray.length);
                Bitmap croppedBitmap = Bitmap.createBitmap(fullBitmap, (int)crop_x, (int)crop_y,
                        (int)crop_width, (int)crop_height);

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

                AnalysisResult result = visionClient.describe(inputStream, 1);  //max candidates = 1
                return  result;

            } catch (Exception e) {
                this.e = e;    // Store error
            }
            return null;
        }

        /**
         * On the completion of producing results with emotion client, checking the resulting tags
         * to determine whether there is an object
         * @param result Analyse result containing description of the image
         */
        @Override
        protected void onPostExecute(AnalysisResult result) {
            super.onPostExecute(result);
            // Display based on error existence
            if (e != null) {
                resultTextView.setText("Error, object detection: " + e.getMessage());
                Log.e("Object detection ", e.getMessage());
                jointAttentiontBuffer.add(0);
                this.e = null;
            }
            else {
                //Checking the resulting tags wth the suspicious tag list
                ///*********** this list needs to be added with more tags by testing object detction
                //on test cases.
                for (String tag: result.description.tags) {
                    if(Data.TAG_LIST.contains(tag)){
                        hasJointAttention = 1;
                        jointAttentiontBuffer.add(1);
                        break;
                    }
                }
            }
            if(hasJointAttention==0){
                jointAttentiontBuffer.add(0);
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
        for(int i=0; i<8; i++){
            logText +=String.format("%.10f", parentEmotionVec[i])+ ", ";
        }
        for(int i=0; i<7; i++){
            logText += String.format("%.10f", childEmotionVec[i])+ ", ";
        }
        logText += String.format("%.10f", childEmotionVec[7]);

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
        resultText += "\nParent Emotion Vec : " + Arrays.toString(parentEmotionVec);
        resultText += "\nChild Emotion Vec : " + Arrays.toString(childEmotionVec);

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


    /**
     * producing a feedback (currently only averaging results over the 2 min window) after first
     * 2 minutes, the feedback is provided once in a minute(using past 2 min results in buffers)
     */
    public void doFeedback(){
        new feedbackingTask().execute();
    }

    /**
     * AsyncTask to calculate average results, an asyncTask is used for the sake of the simplicity
     * to use th UI thread(textView) to show feedback(results)
     */
    private class feedbackingTask extends AsyncTask<String, String, float[]>{
        @Override
        protected float[] doInBackground(String... args){
            float[] results = new float[5];
            int timesParentLookedAtChild = 0;
            int timesChildLookedAtParent = 0;
            int timesOfEyeContact = 0;
            int timesOfSameEyeLevel = 0;
            int timesOfJointAttention = 0;
            float divider = (float)Data.AVERAGING_WINDOW_LENGTH;

            for(int i=0; i<Data.AVERAGING_WINDOW_LENGTH; i++){
                timesParentLookedAtChild += parentLookingAtChildBuffer.get(i);
                timesChildLookedAtParent += childLookingAtParentBuffer.get(i);
                timesOfEyeContact += eyeContactBuffer.get(i);
                timesOfSameEyeLevel += bothAtSameEyeLevelBuffer.get(i);
                timesOfJointAttention += jointAttentiontBuffer.get(i);
            }

            results[0] = timesParentLookedAtChild*100/divider;
            results[1] = timesChildLookedAtParent*100/divider;
            results[2] = timesOfEyeContact*100/divider;
            results[3] = timesOfSameEyeLevel*100/divider;
            results[4] = timesOfJointAttention*100/divider;

            return results;
        }

        /**
         * Averaging emotions over 2 min(only each emotion) and showing final results in UI thread
         * @param result
         */
        @Override
        protected void onPostExecute(float[] result){
            super.onPostExecute(result);

            float[] avgParentEmotionVec = new float[8];
            float[] avgChildEmotionVec = new float[8];
            for(int i=0; i<8; i++){
                avgChildEmotionVec[i] = cumChildEmotionVec[i]/(Data.AVERAGING_WINDOW_LENGTH);
                avgParentEmotionVec[i] = cumParentEmotionVec[i]/(Data.AVERAGING_WINDOW_LENGTH);
            }

            String resultText = "\n";

            resultText += "Time proportion parent spent looking at child : " + result[0];
            resultText += "\nTime proportion child spent looking at parent : " + result[1] ;
            resultText += "\nTime proportion on eye Contact : " + result[2] ;
            resultText += "\nTime proportion on parent child same ete level : " + result[3] ;
            resultText += "\nTime proportion spent on joint Attention : " + result[4];
            resultText += "\nAverage Parent Emotion Vec : " + Arrays.toString(avgParentEmotionVec);
            resultText += "\nAverage child Emotion Vec : " + Arrays.toString(avgChildEmotionVec);

            feedbackTextView.setText(resultText);
            Log.d("FEEDBACK  : \n", resultText);
        }
    }
}