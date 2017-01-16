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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.System.currentTimeMillis;


/**
 * Created by dumindut on 4/10/2016.
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
    private int count = 1;
    private boolean startFeedbacking = false;

    private static final float X_DIF_THRESHOLD = 10.0f;
    private static final float Y_DIF_THRESHOLD = 40.0f;
    private static final String[] tags = {"colored","dog","animal","stuffed","bear","teddy","toy",
            "colorful","decorated","plastic","sign"};
    private static final ArrayList<String> taglist = new ArrayList<String>(Arrays.asList(tags));
    private static final int FEEDBACK_FRAME_COUNT_THRESHOLD = 20;
    private static final int AGE_DETECTION_FRAME_COUNT_THRESHOLD = 2;
    private static final int FEATURE_DETECTION_TIME_THRESHOLD = 3000;

    private boolean isParentLookingAtChild = false;
    private boolean isChildLookingAtParent = false;
    private boolean hasEyeContact = false;
    private boolean hasJointAttention = false;
    private float[] parentEmotionVec = new float[8];
    private float[] childEmotionVec = new float[8];

    CircularFifoQueue<Integer> parentLookingAtChildBuffer = new CircularFifoQueue<Integer>(FEEDBACK_FRAME_COUNT_THRESHOLD*2);
    CircularFifoQueue<Integer> childLookingAtParentBuffer = new CircularFifoQueue<Integer>(FEEDBACK_FRAME_COUNT_THRESHOLD*2);
    CircularFifoQueue<Integer> eyeContactBuffer = new CircularFifoQueue<Integer>(FEEDBACK_FRAME_COUNT_THRESHOLD*2);
    CircularFifoQueue<Integer> jointAttentiontBuffer = new CircularFifoQueue<Integer>(FEEDBACK_FRAME_COUNT_THRESHOLD*2);
    private float[] cumParentEmotionVec = {0,0,0,0,0,0,0,0};
    private float[] cumChildEmotionVec = {0,0,0,0,0,0,0,0};

    FeatureDetector(Detector<Face> delegate, TextView textView, TextView textView2, EmotionServiceRestClient client1,
                    FaceServiceRestClient client2, VisionServiceRestClient client3) {
        mDelegate = delegate;
        resultTextView = textView;
        feedbackTextView = textView2;
        emotionClient = client1;
        faceClient = client2;
        visionClient = client3;
        lastTime = currentTimeMillis();
    }

    @Override
    public SparseArray<Face> detect(Frame frame) {
        // *** Custom frame processing code
        if(currentTimeMillis()-lastTime > FEATURE_DETECTION_TIME_THRESHOLD && Data.isIdentified){
            lastTime = currentTimeMillis();
            outputArray = getByteArray(frame);
            if(count%AGE_DETECTION_FRAME_COUNT_THRESHOLD == 0){
                doDifferentiate();
            }
            recognizeFeatures();
            doRecognizeEmotions();
            if(count%(2*FEEDBACK_FRAME_COUNT_THRESHOLD)==0 && !startFeedbacking){
                doFeedback();
                startFeedbacking = true;
                count = 0;
            }
            if(count%FEEDBACK_FRAME_COUNT_THRESHOLD == 0 && startFeedbacking){
                doFeedback();
                count = 0;
            }
            count++;
        }
        if(currentTimeMillis()-lastTime > FEATURE_DETECTION_TIME_THRESHOLD && !Data.isIdentified){
            lastTime = currentTimeMillis();
            outputArray = getByteArray(frame);
            doDifferentiate();
        }
        return mDelegate.detect(frame);
    }

    public boolean isOperational() {
        return mDelegate.isOperational();
    }

    public boolean setFocus(int id) {
        return mDelegate.setFocus(id);
    }

    public void doDifferentiate(){
        try{
            new ageDetectionTask().execute();
        }
        catch(Exception e){
            resultTextView.setText("Error encountered in age detection : " + e.toString());
            Log.e("Age detection ",e.toString());
        }
    }

    private class ageDetectionTask extends AsyncTask<String, String, com.microsoft.projectoxford.face.contract.Face[]> {
        private Exception e = null;
        public ageDetectionTask(){}

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

    private class emotionDetectionTask extends AsyncTask<String, String, List<RecognizeResult>> {
        private Exception e = null;
        public emotionDetectionTask() {}

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

                    if(Math.abs(x-Data.Parent.x)<X_DIF_THRESHOLD && Math.abs(y-Data.Parent.y)<Y_DIF_THRESHOLD){
                        //Parent
                        parentEmotionVec = emotionVec;
                        for(int i=0; i<8; i++){
                            float temp = cumParentEmotionVec[i];
                            cumParentEmotionVec[i] = emotionVec[i] + temp;
                        }
                    }
                    else if(Math.abs(x-Data.Child.x)<X_DIF_THRESHOLD && Math.abs(y-Data.Child.y)<Y_DIF_THRESHOLD){
                        //Child
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

    //Method to rotate bitmap by 90degrees clockwise
    private Bitmap rotateImage(Bitmap bitmapSrc) {
        Matrix matrix = new Matrix();
        matrix.postRotate(270);
        return Bitmap.createBitmap(bitmapSrc, 0, 0,
                bitmapSrc.getWidth(), bitmapSrc.getHeight(), matrix, true);
    }

    //Method to check for features other than emotions
    private void recognizeFeatures(){
        isChildLookingAtParent = false;
        isParentLookingAtChild = false;
        hasEyeContact = false;
        hasJointAttention = false;

        double thetaThreshold1;   //to y-faceheight/4
        double thetaThreshold2;     //to y+faceheight/4
        double thetaThresholdHigh;
        double thetaThresholdLow;
        float globalTheta;

        /////////////////////////////////////////////////////////////////////////////////////////
        // Detecting whether parent looking at child
        globalTheta = Data.Parent.globalTheta;
        thetaThreshold1 = Math.atan2(Data.Child.y-Data.Child.faceHeight/4-Data.Parent.y, Data.Child.x-Data.Parent.x);
        thetaThreshold2 = Math.atan2(Data.Child.y+Data.Child.faceHeight/4-Data.Parent.y, Data.Child.x-Data.Parent.x);
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
                isParentLookingAtChild = true;
            }
        }
        //other cases
        else{
            if(globalTheta>thetaThresholdLow && globalTheta<thetaThresholdHigh){
                isParentLookingAtChild = true;
            }
        }
        //Updating the buffer
        if(isParentLookingAtChild){parentLookingAtChildBuffer.add(1);}
        else{parentLookingAtChildBuffer.add(0);}

        ///////////////////////////////////////////////////////////////////////////////////////////
        // Checking whether child looking at parent
        globalTheta = Data.Child.globalTheta;
        thetaThreshold1 = Math.atan2(Data.Parent.y-Data.Parent.faceHeight/4-Data.Child.y, Data.Parent.x-Data.Child.x);
        thetaThreshold2 = Math.atan2(Data.Parent.y+Data.Parent.faceHeight/4-Data.Child.y, Data.Parent.x-Data.Child.x);
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
                isChildLookingAtParent = true;
            }
        }
        //other cases
        else{
            if(globalTheta>thetaThresholdLow && globalTheta<thetaThresholdHigh){
                isChildLookingAtParent = true;
            }
        }
        //Updating the buffer
        if(isChildLookingAtParent){childLookingAtParentBuffer.add(1);}
        else{childLookingAtParentBuffer.add(0);}

        //////////////////////////////////////////////////////////////////////////////////////////
        // Checking for eye contact
        if(isChildLookingAtParent && isParentLookingAtChild){
            hasEyeContact = true;
            eyeContactBuffer.add(1);
        }
        else{eyeContactBuffer.add(0);}

        /////////////////////////////////////////////////////////////////////////////////////////
        // Checking for joint attention
        if(!isChildLookingAtParent && !isParentLookingAtChild){
            Data.meetX = 0;
            Data.meetY = 0;
            float x1 = Data.Parent.x;
            float y1 = Data.Parent.y;
            float x2 = Data.Child.x;
            float y2 = Data.Child.y;
            double theta1 = Math.toRadians(Data.Parent.globalTheta);
            double theta2 = Math.toRadians(Data.Child.globalTheta);

            double u = (y2 + (x1-x2)*Math.tan(theta2) - y1)/(Math.sin(theta1)-Math.cos(theta1)*Math.tan(theta2));
            double v = (x1 + u*Math.cos(theta1) - x2)/Math.cos(theta2);

            if(u>0 && v>0){//two rays meet
                double meetX = x1 + u*Math.cos(theta1);
                double meetY = y1 + u*Math.sin(theta1);

                if(meetX < Data.previewWidth-Data.Parent.faceWidth && meetX > Data.Parent.faceWidth){
                    if(meetY < Data.previewHeight-Data.Parent.faceHeight && meetY > Data.Parent.faceHeight){
                        //Two rays meet within the camera preview.
                        Data.meetX = (float)meetX;
                        Data.meetY = (float)meetY;
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

    private class objectDetectionTask extends AsyncTask<String, String, AnalysisResult> {
        // Store error message
        private Exception e = null;
        public objectDetectionTask() {}

        @Override
        protected AnalysisResult doInBackground(String... args) {
            try {
                float crop_x = Data.meetX-Data.Parent.faceWidth;
                float crop_y = Data.meetY-Data.Parent.faceHeight;
                float crop_width = 2*Data.Parent.faceWidth;
                float crop_height = 2*Data.Parent.faceHeight;

                Bitmap fullBitmap = BitmapFactory.decodeByteArray(outputArray, 0, outputArray.length);
                Bitmap croppedBitmap = Bitmap.createBitmap(fullBitmap, (int)crop_x, (int)crop_y,
                        (int)crop_width, (int)crop_height);

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

                AnalysisResult result = visionClient.describe(inputStream, 1);
                return  result;

            } catch (Exception e) {
                this.e = e;    // Store error
            }
            return null;
        }

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
                for (String tag: result.description.tags) {
                    if(taglist.contains(tag)){
                        hasJointAttention = true;
                        jointAttentiontBuffer.add(1);
                        break;
                    }
                }
            }
            if(!hasJointAttention){
                jointAttentiontBuffer.add(0);
            }
        }
    }

    public void showResults(TextView textView){
        String resultText = "\n";

        resultText += "Parent looking at child : " + isParentLookingAtChild;
        resultText += "\nChild looking at parent : " + isChildLookingAtParent;
        resultText += "\nEye Contact : " + hasEyeContact;
        resultText += "\nJoint Attention : " + hasJointAttention;
        resultText += "\nParent Emotion Vec : " + Arrays.toString(parentEmotionVec);
        resultText += "\nChild Emotion Vec : " + Arrays.toString(childEmotionVec);

        textView.setText(resultText);
        Log.d("  ", resultText);
    }

    private byte[] getByteArray(Frame frame){
        int width = frame.getMetadata().getWidth();
        int height = frame.getMetadata().getHeight();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ByteBuffer byteBuffer = frame.getGrayscaleImageData();
        YuvImage yuvimage = new YuvImage(byteBuffer.array(), ImageFormat.NV21, width, height, null);
        yuvimage.compressToJpeg(new Rect(0, 0, width, height), 100, output);
        byte[] outputArray = output.toByteArray();

        //If portrait, changing the outputArray
        if (frame.getMetadata().getRotation() ==3){
            Bitmap bmp = BitmapFactory.decodeByteArray(outputArray, 0, outputArray.length);
            Bitmap rotatedBmp = rotateImage(bmp);
            ByteArrayOutputStream output2 = new ByteArrayOutputStream();
            rotatedBmp.compress(Bitmap.CompressFormat.JPEG, 100, output2);
            outputArray = output2.toByteArray();
        }
        return outputArray;
    }

    public void doFeedback(){
        new feedbackingTask().execute();
    }

    private class feedbackingTask extends AsyncTask<String, String, float[]>{
        @Override
        protected float[] doInBackground(String... args){
            float[] results = new float[4];
            int timesParentLookedAtChild = 0;
            int timesChildLookedAtParent = 0;
            int timesOfEyeContact = 0;
            int timesOfJointAttention = 0;
            float divider = (float)FEEDBACK_FRAME_COUNT_THRESHOLD*2;

            for(int i=0; i<FEEDBACK_FRAME_COUNT_THRESHOLD*2; i++){
                timesParentLookedAtChild += parentLookingAtChildBuffer.get(i);
                timesChildLookedAtParent += childLookingAtParentBuffer.get(i);
                timesOfEyeContact += eyeContactBuffer.get(i);
                timesOfJointAttention += jointAttentiontBuffer.get(i);
            }

            results[0] = timesParentLookedAtChild*100/divider;
            results[1] = timesChildLookedAtParent*100/divider;
            results[2] = timesOfEyeContact*100/divider;
            results[3] = timesOfJointAttention*100/divider;

            return results;
        }

        @Override
        protected void onPostExecute(float[] result){
            super.onPostExecute(result);

            float[] avgParentEmotionVec = new float[8];
            float[] avgChildEmotionVec = new float[8];
            for(int i=0; i<8; i++){
                avgChildEmotionVec[i] = cumChildEmotionVec[i]/(2*FEEDBACK_FRAME_COUNT_THRESHOLD);
                avgParentEmotionVec[i] = cumParentEmotionVec[i]/(2*FEEDBACK_FRAME_COUNT_THRESHOLD);
            }

            String resultText = "\n";

            resultText += "Time proportion parent spent looking at child : " + result[0];
            resultText += "\nTime proportion child spent looking at parent : " + result[1] ;
            resultText += "\nTime proportion on eye Contact : " + result[2] ;
            resultText += "\nTime proportion spent on joint Attention : " + result[3];
            resultText += "\nAverage Parent Emotion Vec : " + Arrays.toString(avgParentEmotionVec);
            resultText += "\nAverage child Emotion Vec : " + Arrays.toString(avgChildEmotionVec);

            feedbackTextView.setText(resultText);
            Log.d("FEEDBACK  : \n", resultText);
        }
    }
}