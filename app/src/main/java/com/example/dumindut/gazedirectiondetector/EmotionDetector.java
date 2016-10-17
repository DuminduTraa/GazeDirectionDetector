package com.example.dumindut.gazedirectiondetector;

import android.graphics.ImageFormat;
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
import com.microsoft.projectoxford.emotion.EmotionServiceClient;
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
    private EmotionServiceClient client;
    private Frame theFrame;

    EmotionDetector(Detector<Face> delegate, TextView textView, EmotionServiceClient client1) {
        mDelegate = delegate;
        emotionText = textView;
        client = client1;
    }

    @Override
    public SparseArray<Face> detect(Frame frame) {
        // *** Custom frame processing code

        theFrame = frame;
        doRecognize();


        return mDelegate.detect(frame);
    }

    public boolean isOperational() {
        return mDelegate.isOperational();
    }

    public boolean setFocus(int id) {
        return mDelegate.setFocus(id);
    }


    public void doRecognize() {

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
                        resultText += (String.format("\t anger: %1$.5f\n", r.scores.anger));
                        resultText += (String.format("\t contempt: %1$.5f\n", r.scores.contempt));
                        resultText += (String.format("\t disgust: %1$.5f\n", r.scores.disgust));
                        resultText += (String.format("\t fear: %1$.5f\n", r.scores.fear));
                        resultText += (String.format("\t happiness: %1$.5f\n", r.scores.happiness));
                        resultText += (String.format("\t neutral: %1$.5f\n", r.scores.neutral));
                        resultText += (String.format("\t sadness: %1$.5f\n", r.scores.sadness));
                        resultText += (String.format("\t surprise: %1$.5f\n", r.scores.surprise));
                        resultText += (String.format("\t face rectangle: %d, %d, %d, %d", r.faceRectangle.left, r.faceRectangle.top, r.faceRectangle.width, r.faceRectangle.height));
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
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        List<RecognizeResult> result = null;
        result = client.recognizeImage(inputStream);

        String json = gson.toJson(result);
        Log.d("result", json);

        return result;
    }

}