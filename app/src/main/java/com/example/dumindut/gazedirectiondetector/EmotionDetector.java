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
import com.google.gson.Gson;
import com.microsoft.projectoxford.emotion.EmotionServiceRestClient;
import com.microsoft.projectoxford.emotion.contract.RecognizeResult;
import com.microsoft.projectoxford.emotion.rest.EmotionServiceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Created by dumindut on 4/10/2016.
 */
public class EmotionDetector extends Detector<Face> {
    private Detector<Face> mDelegate;
    private TextView emotionText;
    private EmotionServiceRestClient client;
    private Frame theFrame;
    private long lastTime;
    private long lastTimeForJointAttention;

    EmotionDetector(Detector<Face> delegate, TextView textView, EmotionServiceRestClient client1) {
        mDelegate = delegate;
        emotionText = textView;
        client = client1;
        lastTime = lastTimeForJointAttention = System.currentTimeMillis();
    }

    @Override
    public SparseArray<Face> detect(Frame frame) {
        // *** Custom frame processing code
        theFrame = frame;

        if(System.currentTimeMillis()-lastTime > 3000 && Data.isIdentified){
           doRecognizeEmotions();
           lastTime = System.currentTimeMillis();
        }

        if(System.currentTimeMillis()-lastTimeForJointAttention > 300 && Data.isIdentified){
            recognizeFeatures();
            lastTimeForJointAttention = System.currentTimeMillis();
        }

        return mDelegate.detect(frame);
    }

    public boolean isOperational() {
        return mDelegate.isOperational();
    }

    public boolean setFocus(int id) {
        return mDelegate.setFocus(id);
    }


    public void doRecognizeEmotions() {
        // Do emotion detection using auto-detected faces.
        try {
            new doRequest().execute();
        } catch (Exception e) {
            emotionText.setText("Error encountered. Exception is: " + e.toString());
        }
    }


    private class doRequest extends AsyncTask<String, String, List<RecognizeResult>> {
        // Store error message
        private Exception e = null;

        public doRequest() {
        }

        @Override
        protected List<RecognizeResult> doInBackground(String... args) {
            try {
                return processWithAutoFaceDetection();
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
                emotionText.setText("Error: " + e.getMessage());
                //Log.e("error", e.getMessage());
                this.e = null;
            } else {
                if (result.size() == 0) {
                    emotionText.setText("No emotion detected :(");
                    //Log.e("error", "No emotion detected :(");
                } else {
                    Integer count = 0;
                    String resultText = "";

                    for (RecognizeResult r : result) {
                        resultText += (String.format("\nFace #%1$d \n", count));

                        Double[] valueList = new Double[8];
                        valueList[0] = r.scores.anger;
                        valueList[1] = r.scores.contempt;
                        valueList[2] = r.scores.disgust;
                        valueList[3] = r.scores.fear;
                        valueList[4] = r.scores.happiness;
                        valueList[5] = r.scores.neutral;
                        valueList[6] = r.scores.sadness;
                        valueList[7] = r.scores.surprise;


                        String[] emotions = new String[8];
                        emotions[0] = "Anger";
                        emotions[1] = "Contempt";
                        emotions[2] = "Disgust";
                        emotions[3] = "Fear";
                        emotions[4] = "Happiness";
                        emotions[5] = "Neutral";
                        emotions[6] = "Sadness";
                        emotions[7] = "Surprise";

                        String most = "";
                        String secondMost = "";
                        Double mostValue = 0.;
                        Double secondMostValue = 0.;

                        for (int i=0;i<8;i++){
                            if(valueList[i] > mostValue){
                                secondMostValue = mostValue;
                                secondMost = most;
                                mostValue = valueList[i];
                                most = emotions[i];
                            }
                            else if(valueList[i] > secondMostValue){
                                secondMostValue = valueList[i];
                                secondMost = emotions[i];
                            }
                        }

                        resultText += most + " : " + (int)(double)(mostValue*100) + "%\n";
                        resultText += secondMost + " : " + (int)(double)(secondMostValue*100) + "%\n";
                        resultText += (String.format("face rectangle: %d, %d, %d, %d", r.faceRectangle.left, r.faceRectangle.top, r.faceRectangle.width, r.faceRectangle.height));
                        count++;
                    }
                    emotionText.setText(resultText);
                    //Log.e("result", resultText);
                }
            }
        }
    }


    private List<RecognizeResult> processWithAutoFaceDetection() throws EmotionServiceException, IOException {
        Log.d("emotion", "Start emotion detection with auto-face detection");

        Gson gson = new Gson();

        // Put the image into an input stream for detection.

        ByteBuffer byteBuffer = theFrame.getGrayscaleImageData();
        int width = theFrame.getMetadata().getWidth();
        int heigth = theFrame.getMetadata().getHeight();
        YuvImage yuvimage = new YuvImage(byteBuffer.array(), ImageFormat.NV21, width, heigth, null);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        yuvimage.compressToJpeg(new Rect(0, 0, width, heigth), 100, output);

        byte[] outputArray = output.toByteArray();

        //If portrait, changing the outputArray
        if (theFrame.getMetadata().getRotation() ==3){
            Bitmap bmp = BitmapFactory.decodeByteArray(outputArray, 0, outputArray.length);
            Bitmap rotatedBmp = rotateImage(bmp);
            ByteArrayOutputStream output2 = new ByteArrayOutputStream();
            rotatedBmp.compress(Bitmap.CompressFormat.JPEG, 100, output2);
            outputArray = output2.toByteArray();
        }

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputArray);

        List<RecognizeResult> result = null;
        result = client.recognizeImage(inputStream);

        String json = gson.toJson(result);
        Log.d("result", json);

        return result;
    }

    //Method to rotate bitmap by 90degrees clockwise
    private Bitmap rotateImage(Bitmap bitmapSrc) {
        Matrix matrix = new Matrix();
        matrix.postRotate(270);
        return Bitmap.createBitmap(bitmapSrc, 0, 0,
                bitmapSrc.getWidth(), bitmapSrc.getHeight(), matrix, true);
    }

    //Method to check for Joint attention
    private void recognizeFeatures(){
        //Checking for eye contact
        if(Data.isChildLookingAtParent && Data.isParentLookingAtChild){
            Data.hasEyeContact=true;
            Log.e("faceGraphic","Eye Contact");
        }
        else{
            Data.hasEyeContact=false;
        }

        //Checking for joint attention
        if(!Data.isChildLookingAtParent && !Data.isParentLookingAtChild){
            Data.hasJointAttention = false;
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
                        Data.hasJointAttention = true;      //Two rays meet within the camera preview.
                        Data.meetX = (float)meetX;
                        Data.meetY = (float)meetY;
                        Log.e("EmotionDetector","Joint Attention");
                    }
                }
            }
        }
    }
}