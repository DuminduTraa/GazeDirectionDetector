package com.example.dumindut.gazedirectiondetector;

/**
 * Created by dumindut on 29/8/2016.
 */

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.example.dumindut.gazedirectiondetector.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.Landmark;

/*Graphic instance for rendering face position, orientation, and landmarks within an associated graphic overlay view.*/
class FaceGraphic extends GraphicOverlay.Graphic {
    private static final float FACE_POSITION_RADIUS = 5.0f;
    private static final float ID_TEXT_SIZE = 40.0f;
    private static final float ID_Y_OFFSET = 50.0f;
    private static final float ID_X_OFFSET = -50.0f;
    private static final float BOX_STROKE_WIDTH = 5.0f;

    private static final int COLOR_CHOICES[] = {
            Color.BLUE,
            Color.CYAN,
            Color.GREEN,
            Color.MAGENTA,
            Color.RED,
            Color.WHITE,
            Color.YELLOW
    };
    private static int mCurrentColorIndex = 0;

    private Paint mFacePositionPaint;
    private Paint mIdPaint;
    private Paint mBoxPaint;

    private volatile Face mFace;
    private int mFaceId;

    FaceGraphic(GraphicOverlay overlay) {
        super(overlay);

        mCurrentColorIndex = (mCurrentColorIndex + 1) % COLOR_CHOICES.length;
        final int selectedColor = COLOR_CHOICES[mCurrentColorIndex];

        mFacePositionPaint = new Paint();
        mFacePositionPaint.setColor(selectedColor);

        mIdPaint = new Paint();
        mIdPaint.setColor(selectedColor);
        mIdPaint.setTextSize(ID_TEXT_SIZE);

        mBoxPaint = new Paint();
        mBoxPaint.setColor(selectedColor);
        mBoxPaint.setStyle(Paint.Style.STROKE);
        mBoxPaint.setStrokeWidth(BOX_STROKE_WIDTH);
    }

    void setId(int id) {
        mFaceId = id;
    }

    void updateFace(Face face) {
        mFace = face;
        postInvalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        Face face = mFace;
        if (face == null) {
            return;
        }

        float x = translateX(face.getPosition().x + face.getWidth() / 2);
        float y = translateY(face.getPosition().y + face.getHeight() / 2);
        canvas.drawCircle(x, y, FACE_POSITION_RADIUS+5.0f, mFacePositionPaint);

        float left_eye_x = -1;
        float left_eye_y = -1;
        float right_eye_x = -1;
        float right_eye_y = -1;
        float mouth_x = -1;
        float mouth_y = -1;
        float left_mouth_x = -1;
        float left_mouth_y = -1;
        float right_mouth_x = -1;
        float right_mouth_y = -1;
        float left_cheek_x = -1;
        float left_cheek_y = -1;
        float right_cheek_x = -1;
        float right_cheek_y = -1;
        float nose_x = -1;
        float nose_y = -1;

        float eulerY = face.getEulerY();
        float eulerZ = face.getEulerZ();

        float theta;
        boolean isThetapositive;
        boolean isYLeft;
        float dirLineLength;


        canvas.drawText("id: " + mFaceId, x + ID_X_OFFSET, y + ID_Y_OFFSET, mIdPaint);


        canvas.drawText("rotationY: " + String.format("%.2f", eulerY), x-100,y-150, mIdPaint);
        canvas.drawText("rotationZ: " + String.format("%.2f", eulerZ), x-100,y-100, mIdPaint);

        /*for (Landmark landmark : face.getLandmarks()){
            float cx = translateX(landmark.getPosition().x);
            float cy = translateY(landmark.getPosition().y);
            canvas.drawCircle(cx, cy, FACE_POSITION_RADIUS, mFacePositionPaint);
        }*/

        for (Landmark landmark : face.getLandmarks()) {
            if (landmark.getType() == Landmark.RIGHT_EYE) {
                right_eye_x = translateX(landmark.getPosition().x);
                right_eye_y = translateY(landmark.getPosition().y);
                canvas.drawCircle(right_eye_x, right_eye_y, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.LEFT_EYE) {
                left_eye_x = translateX(landmark.getPosition().x);
                left_eye_y = translateY(landmark.getPosition().y);
                canvas.drawCircle(left_eye_x, left_eye_y, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.LEFT_CHEEK) {
                left_cheek_x = translateX(landmark.getPosition().x);
                left_cheek_y = translateY(landmark.getPosition().y);
                canvas.drawCircle(left_cheek_x, left_cheek_y, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.RIGHT_CHEEK) {
                right_cheek_x = translateX(landmark.getPosition().x);
                right_cheek_y = translateY(landmark.getPosition().y);
                canvas.drawCircle(right_cheek_x, right_cheek_y, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.NOSE_BASE) {
                nose_x = translateX(landmark.getPosition().x);
                nose_y = translateY(landmark.getPosition().y);
                canvas.drawCircle(nose_x, nose_y, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.LEFT_MOUTH) {
                left_mouth_x = translateX(landmark.getPosition().x);
                left_mouth_y = translateY(landmark.getPosition().y);
                canvas.drawCircle(left_mouth_x, left_mouth_y, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.RIGHT_MOUTH) {
                right_mouth_x = translateX(landmark.getPosition().x);
                right_mouth_y = translateY(landmark.getPosition().y);
                canvas.drawCircle(right_mouth_x, right_mouth_y, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.BOTTOM_MOUTH) {
                mouth_x = translateX(landmark.getPosition().x);
                mouth_y = translateY(landmark.getPosition().y);
                canvas.drawCircle( mouth_x, mouth_y, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            /*else if (landmark.getType() == Landmark.LEFT_EAR) {
                float cx = translateX(landmark.getPosition().x);
                float cy = translateY(landmark.getPosition().y);
                canvas.drawCircle(cx, cy, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.LEFT_EAR_TIP) {
                float cx = translateX(landmark.getPosition().x);
                float cy = translateY(landmark.getPosition().y);
                canvas.drawCircle(cx, cy, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.RIGHT_EAR) {
                float cx = translateX(landmark.getPosition().x);
                float cy = translateY(landmark.getPosition().y);
                canvas.drawCircle(cx, cy, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.RIGHT_EAR_TIP) {
                float cx = translateX(landmark.getPosition().x);
                float cy = translateY(landmark.getPosition().y);
                canvas.drawCircle(cx, cy, FACE_POSITION_RADIUS, mFacePositionPaint);
            }*/
        }

        //Draw the T shape connecting eyes and mouth

/*        if (left_eye_x != -1 && left_eye_y != -1 && right_eye_x != -1 &&right_eye_y != -1 && mouth_x!=-1 && mouth_y!=-1){
            canvas.drawLine(left_eye_x, left_eye_y, right_eye_x, right_eye_y, mFacePositionPaint);
            canvas.drawLine((left_eye_x+right_eye_x)/2, (left_eye_y+right_eye_y)/2, mouth_x, mouth_y, mFacePositionPaint);
        }
        else if(){

        }*/

        //Drawing a looking direction line from the middle of the face. Using only rotation details
        //Looking from selfie camera. All the details according to the frame, not person

        theta = Math.abs(eulerZ);
        dirLineLength = Math.abs(eulerY)/60*1000;
        if(eulerZ<0){isThetapositive = false;}
        else{isThetapositive = true;}
        if(eulerY<0){isYLeft = false;}       // Left is preview frame's left
        else{isYLeft = true;}

        if(isThetapositive && isYLeft){
            double stopX = x-dirLineLength*Math.cos(Math.toRadians(theta));
            double stopY = y-dirLineLength*Math.sin(Math.toRadians(theta));
            canvas.drawLine(x,y,(float)stopX,(float)stopY,mFacePositionPaint);
        }
        else if(isThetapositive && isYLeft==false){
            double stopX = x+dirLineLength*Math.cos(Math.toRadians(theta));
            double stopY = y+dirLineLength*Math.sin(Math.toRadians(theta));
            canvas.drawLine(x,y,(float)stopX,(float)stopY,mFacePositionPaint);
        }
        else if(isThetapositive==false && isYLeft){
            double stopX = x-dirLineLength*Math.cos(Math.toRadians(theta));
            double stopY = y+dirLineLength*Math.sin(Math.toRadians(theta));
            canvas.drawLine(x,y,(float)stopX,(float)stopY,mFacePositionPaint);
        }
        else if(isThetapositive==false && isYLeft==false){
            double stopX = x+dirLineLength*Math.cos(Math.toRadians(theta));
            double stopY = y-dirLineLength*Math.sin(Math.toRadians(theta));
            canvas.drawLine(x,y,(float)stopX,(float)stopY,mFacePositionPaint);
        }


        // Draws a bounding box around the face.
        float xOffset = scaleX(face.getWidth() / 2.0f);
        float yOffset = scaleY(face.getHeight() / 2.0f);
        float left = x - xOffset;
        float top = y - yOffset;
        float right = x + xOffset;
        float bottom = y + yOffset;
        canvas.drawRect(left, top, right, bottom, mBoxPaint);
    }
}
