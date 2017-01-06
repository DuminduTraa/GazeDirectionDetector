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
    public TextView resultTextView;
    private EmotionServiceRestClient emotionClient;
    private FaceServiceRestClient faceClient;
    private VisionServiceRestClient visionClient;
    private byte[] outputArray;
    private long lastTime;
    private static final float X_DIF_THRESHOLD = 10.0f;
    private static final float Y_DIF_THRESHOLD = 40.0f;
    private int count = 0;
    private static final String[] tags = {"colored","dog","animal","stuffed","bear","teddy","toy",
            "colorful","decorated","plastic","sign"};
    private static final ArrayList<String> taglist = new ArrayList<String>(Arrays.asList(tags));

    FeatureDetector(Detector<Face> delegate, TextView textView, EmotionServiceRestClient client1,
                    FaceServiceRestClient client2, VisionServiceRestClient client3) {
        mDelegate = delegate;
        resultTextView = textView;
        emotionClient = client1;
        faceClient = client2;
        visionClient = client3;
        lastTime = currentTimeMillis();
    }

    @Override
    public SparseArray<Face> detect(Frame frame) {
        // *** Custom frame processing code
        if(currentTimeMillis()-lastTime > 3000 && Data.isIdentified){
            outputArray = getByteArray(frame);
            if(count == 1){
                doDifferentiate();;
                count = 0;
            }
            recognizeFeatures();
            doRecognizeEmotions();
            lastTime = currentTimeMillis();
            count++;
        }
        if(currentTimeMillis()-lastTime > 3000 && !Data.isIdentified){
            outputArray = getByteArray(frame);
            doDifferentiate();
            lastTime = currentTimeMillis();
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

                    float[] valueList = new float[8];
                    valueList[0] = (float)r.scores.anger;
                    valueList[1] = (float)r.scores.contempt;
                    valueList[2] = (float)r.scores.disgust;
                    valueList[3] = (float)r.scores.fear;
                    valueList[4] = (float)r.scores.happiness;
                    valueList[5] = (float)r.scores.neutral;
                    valueList[6] = (float)r.scores.sadness;
                    valueList[7] = (float)r.scores.surprise;

                    if(Math.abs(x-Data.Parent.x)<X_DIF_THRESHOLD && Math.abs(y-Data.Parent.y)<Y_DIF_THRESHOLD){
                        //Parent
                        Data.parentEmotions = valueList;
                    }
                    else if(Math.abs(x-Data.Child.x)<X_DIF_THRESHOLD && Math.abs(y-Data.Child.y)<Y_DIF_THRESHOLD){
                        //Child
                        Data.childEmtions = valueList;
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
        Data.isParentLookingAtChild = false;
        Data.isChildLookingAtParent = false;
        Data.hasEyeContact=false;
        Data.hasJointAttention = false;

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
                Data.isParentLookingAtChild=true;
            }
        }
        //other cases
        else{
            if(globalTheta>thetaThresholdLow && globalTheta<thetaThresholdHigh){
                Data.isParentLookingAtChild=true;
            }
        }

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
                Data.isParentLookingAtChild=true;
            }
        }
        //other cases
        else{
            if(globalTheta>thetaThresholdLow && globalTheta<thetaThresholdHigh){
                Data.isParentLookingAtChild=true;
            }
        }

        //////////////////////////////////////////////////////////////////////////////////////////
        // Checking for eye contact
        if(Data.isChildLookingAtParent && Data.isParentLookingAtChild){
            Data.hasEyeContact=true;
        }

        /////////////////////////////////////////////////////////////////////////////////////////
        // Checking for joint attention
        if(!Data.isChildLookingAtParent && !Data.isParentLookingAtChild){
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

                if(meetX < Data.previewWidth-Data.Parent.faceWidth/2 && meetX > Data.Parent.faceWidth/2){
                    if(meetY < Data.previewHeight-Data.Parent.faceHeight/2 && meetY > Data.Parent.faceHeight/2){
                        //Two rays meet within the camera preview.
                        Data.meetX = (float)meetX;
                        Data.meetY = (float)meetY;
                        doDescribe();
                    }
                }
            }
        }
    }

    public void doDescribe() {
        try {
            new objectDetectionTask().execute();
        }
        catch (Exception e)
        {
            resultTextView.setText("Error encountered in object detection " + e.toString());
            Log.e("Object detection ",e.toString());
        }
    }

    private class objectDetectionTask extends AsyncTask<String, String, AnalysisResult> {
        // Store error message
        private Exception e = null;
        public objectDetectionTask() {}

        @Override
        protected AnalysisResult doInBackground(String... args) {
            try {
                float crop_x = Data.meetX-Data.Parent.faceWidth/2;
                float crop_y = Data.meetY-Data.Parent.faceHeight/2;

                Bitmap fullBitmap = BitmapFactory.decodeByteArray(outputArray, 0, outputArray.length);
                Bitmap croppedBitmap = Bitmap.createBitmap(fullBitmap, (int)crop_x, (int)crop_y,
                        (int)Data.Parent.faceWidth, (int)Data.Parent.faceHeight);

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
                this.e = null;
            }
            else {
                for (String tag: result.description.tags) {
                    if(taglist.contains(tag)){
                        Data.hasJointAttention=true;
                        break;
                    }
                }
            }
        }
    }

    public void showResults(TextView textView){
        String resultText = "\n";

        resultText += "Parent looking at child : " + Data.isParentLookingAtChild;
        resultText += "\nChild looking at parent : " + Data.isChildLookingAtParent;
        resultText += "\nEye Contact : " + Data.hasEyeContact;
        resultText += "\nJoint Attention : " + Data. hasJointAttention;

        textView.setText(resultText);
        Log.d("  ", resultText);
    }

    public byte[] getByteArray(Frame frame){
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
}