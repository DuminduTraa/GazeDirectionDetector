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

    private boolean mIsFrontFacing;

    FaceGraphic(GraphicOverlay overlay,boolean facing) {
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

        mIsFrontFacing = facing;

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

        float width = face.getWidth();
        float height = face.getHeight();
        float faceArea = width*height;

        float x = translateX(face.getPosition().x + width / 2);
        float y = translateY(face.getPosition().y + height / 2);

        String name;
        long currentTime = System.currentTimeMillis();

        canvas.drawCircle(x, y, FACE_POSITION_RADIUS+5.0f, mFacePositionPaint);

        // Draws a bounding box around the face.
        float xOffset = scaleX(face.getWidth() / 2.0f);
        float yOffset = scaleY(face.getHeight() / 2.0f);
        float left = x - xOffset;
        float top = y - yOffset;
        float right = x + xOffset;
        float bottom = y + yOffset;
        canvas.drawRect(left, top, right, bottom, mBoxPaint);

        ///////////////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////////////////////////////////////////////////////////////////////////////
        //All the regular decision Making with reference to Data classes and updating parent
        // and child data in Data class.

        if(Data.ids.size() == 0){
            Data.addNew(mFaceId, x, y, height, width );
            name = Data.UNKNOWN;
        }
        else if(Data.ids.size() == 1){
            float difX = Math.abs(x-Data.positionX.get(0));
            float difY = Math.abs(y-Data.positionY.get(0));
            double rootSquareDiff = Math.sqrt(difX*difX+difY*difY);
            if(mFaceId != Data.ids.get(0) && rootSquareDiff > 250) {
                Data.addNew(mFaceId, x, y, height, width);
                name = Data.UNKNOWN;
            }
            else{
                Data.updateNew(0,mFaceId, x, y, height, width);
                name = Data.UNKNOWN;
            }
        }
        else{//2 faces are in the scene already
            if(Data.faceCount < 100){
                float difX = Math.abs(x-Data.positionX.get(0));
                float difY = Math.abs(y-Data.positionY.get(0));
                double rootSquareDiff = Math.sqrt(difX*difX+difY*difY);
                if(mFaceId == Data.ids.get(0) || rootSquareDiff < 100){
                    Data.areadiff += faceArea - Data.faceHeight.get(1)*Data.faceHeight.get(1);
                    Data.updateNew(0,mFaceId,x,y,height,width);
                }
                else{
                    Data.areadiff += Data.faceHeight.get(0)*Data.faceHeight.get(0) - faceArea;
                    Data.updateNew(1,mFaceId,x,y,height,width);
                }
                name = Data.UNKNOWN;
                Data.faceCount++;
            }
            else if(Data.faceCount == 100){
                Data.faceCount++;
                float difX = Math.abs(x-Data.positionX.get(0));
                float difY = Math.abs(y-Data.positionY.get(0));
                double rootSquareDiff = Math.sqrt(difX*difX+difY*difY);
                if(Data.areadiff > 2500){
                    if(mFaceId == Data.ids.get(0) || rootSquareDiff < 100){
                        name = Data.PARENT;
                        Data.updateNew(0,mFaceId,x,y,height,width);
                    }
                    else{
                        name = Data.CHILD;
                        Data.updateNew(1,mFaceId,x,y,height,width);
                    }
                    Data.addUnknownToParent(0);
                    Data.addUnknownToChild(1);
                    Data.Parent.lastTime = Data.Child.lastTime = currentTime;
                    Data.isIdentified = true;
                }
                else if(Data.areadiff < -2500){
                    if(mFaceId == Data.ids.get(0) || rootSquareDiff < 100){
                        name = Data.CHILD;
                        Data.updateNew(0,mFaceId,x,y,height,width);
                    }
                    else{
                        name = Data.PARENT;
                        Data.updateNew(1,mFaceId,x,y,height,width);
                    }
                    Data.addUnknownToParent(1);
                    Data.addUnknownToChild(0);
                    Data.Parent.lastTime = Data.Child.lastTime = currentTime;
                    Data.isIdentified = true;
                }
                else{
                    Data.faceCount = 0;
                    name = Data.UNKNOWN;
                }
            }
            else{  //Parent and Child already defined
                boolean isSignificantFace = false;
                float difX = Math.abs(x-Data.Parent.x);
                float difY = Math.abs(y-Data.Parent.y);
                double rootSquareDiff = Math.sqrt(difX*difX+difY*difY);

                //Updating parent, child basic information and keep tracking.
                if(mFaceId == Data.Parent.id || rootSquareDiff < 100 ){
                    name = Data.PARENT;
                    Data.updateNew(Data.ids.indexOf(Data.Parent.id), mFaceId,x,y,height,width);
                    Data.updateParent(mFaceId,x,y,height,width);
                }
                else {
                    name = Data.CHILD;
                    Data.updateNew(Data.ids.indexOf(Data.Child.id), mFaceId,x,y,height,width);
                    Data.updateChild(mFaceId,x,y,height,width);
                }

                //If the face is a significant face(occurs in each 300 milliseconds)
                // Calculating features
                if(name == Data.PARENT  && currentTime- Data.Parent.lastTime >= 300){
                    isSignificantFace = true;
                    Data.Parent.lastTime = currentTime;
                }
                else if(name == Data.CHILD  && currentTime- Data.Child.lastTime >= 300){
                    isSignificantFace = true;
                    Data.Child.lastTime = currentTime;
                }

                // Main face processing task happens once in each 300 milli seconds
                if(isSignificantFace){

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

                    float theta = Math.abs(eulerZ);
                    float dirLineLength = Math.abs(eulerY)/60*1000;
                    boolean isThetaPositive;
                    boolean isYLeft;
                    double stopX;
                    double stopY;

                    canvas.drawText("rotationY: " + String.format("%.2f", eulerY), x-100,y-150, mIdPaint);
                    canvas.drawText("rotationZ: " + String.format("%.2f", eulerZ), x-100,y-100, mIdPaint);


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

                        //Drawing a looking direction line from the middle of the face. Using only rotation details
                        //Looking from selfie camera. All the details according to the frame, not person

                        if(mIsFrontFacing) {
                            if (eulerZ < 0) {
                                isThetaPositive = false;
                            } else {
                                isThetaPositive = true;
                            }
                            if (eulerY < 0) {
                                isYLeft = false;
                            }       // Left is preview frame's left
                            else {
                                isYLeft = true;
                            }
                        }

                        else{
                            if (eulerZ > 0) {
                                isThetaPositive = false;
                            } else {
                                isThetaPositive = true;
                            }
                            if (eulerY > 0) {
                                isYLeft = false;
                            }       // Left is preview frame's left
                            else {
                                isYLeft = true;
                            }
                        }

                        if(isThetaPositive && isYLeft){
                            stopX = x-dirLineLength*Math.cos(Math.toRadians(theta));
                            stopY = y-dirLineLength*Math.sin(Math.toRadians(theta));
                            canvas.drawLine(x,y,(float)stopX,(float)stopY,mFacePositionPaint);
                        }
                        else if(isThetaPositive && !isYLeft){
                            stopX = x+dirLineLength*Math.cos(Math.toRadians(theta));
                            stopY = y+dirLineLength*Math.sin(Math.toRadians(theta));
                            canvas.drawLine(x,y,(float)stopX,(float)stopY,mFacePositionPaint);
                        }
                        else if(!isThetaPositive && isYLeft){
                            stopX = x-dirLineLength*Math.cos(Math.toRadians(theta));
                            stopY = y+dirLineLength*Math.sin(Math.toRadians(theta));
                            canvas.drawLine(x,y,(float)stopX,(float)stopY,mFacePositionPaint);
                        }
                        else{
                            stopX = x+dirLineLength*Math.cos(Math.toRadians(theta));
                            stopY = y-dirLineLength*Math.sin(Math.toRadians(theta));
                            canvas.drawLine(x,y,(float)stopX,(float)stopY,mFacePositionPaint);
                        }

                    }
                }
            }
        }

        canvas.drawText(name, x + ID_X_OFFSET, y + ID_Y_OFFSET, mIdPaint);
    }
}