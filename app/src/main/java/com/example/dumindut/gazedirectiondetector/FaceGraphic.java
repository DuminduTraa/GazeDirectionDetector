package com.example.dumindut.gazedirectiondetector;

/**
 * Created by dumindut on 29/8/2016.
 */

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.example.dumindut.gazedirectiondetector.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.face.Face;

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

        float x = face.getPosition().x + width / 2;
        float y = face.getPosition().y + height / 2;
        float x_canvas = translateX(x);
        float y_canvas = scaleY(y);

        String name;
        long currentTime = System.currentTimeMillis();

        canvas.drawCircle(x_canvas, y_canvas, FACE_POSITION_RADIUS+5.0f, mFacePositionPaint);
        canvas.drawCircle(translateX(Data.meetX), scaleY(Data.meetY), FACE_POSITION_RADIUS+5.0f, mFacePositionPaint);

        // Draws a bounding box around the face.
        float xOffset = scaleX(face.getWidth() / 2.0f);
        float yOffset = scaleY(face.getHeight() / 2.0f);
        float left = x_canvas - xOffset;
        float top = y_canvas - yOffset;
        float right = x_canvas + xOffset;
        float bottom = y_canvas + yOffset;
        canvas.drawRect(left, top, right, bottom, mBoxPaint);

        ///////////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////////////////////////////////////////////////////////////////////////

        if(Data.isIdentified){  //Parent and Child defined
            boolean isSignificantFace = false;

            boolean couldBeParent = Math.abs(x-Data.Parent.x)<Data.Parent.faceWidth/2 &&
                    Math.abs(y-Data.Parent.y)<Data.Parent.faceHeight/2;
            boolean couldBeChild = Math.abs(x-Data.Child.x)<Data.Child.faceWidth/2 &&
                    Math.abs(y-Data.Child.y)<Data.Child.faceHeight/2;

            //Updating parent, child basic information and keep tracking.
            if(mFaceId == Data.Parent.id || couldBeParent){
                name = Data.PARENT;
                Data.updateParent(mFaceId,x,y,height,width);
            }
            else if(mFaceId == Data.Child.id || couldBeChild) {
                name = Data.CHILD;
                Data.updateChild(mFaceId,x,y,height,width);
            }
            else{name = Data.UNKNOWN;}

            //If the face is a significant face(occurs in each 300 milliseconds)
            // Calculating features
            if(name == Data.PARENT  && currentTime-Data.Parent.lastTime >= 300){
                isSignificantFace = true;
                Data.Parent.lastTime = currentTime;
            }
            else if(name == Data.CHILD  && currentTime-Data.Child.lastTime >= 300){
                isSignificantFace = true;
                Data.Child.lastTime = currentTime;
            }

            // Main face processing task happens once in each 300 milli seconds
            if(isSignificantFace){
                float eulerY = face.getEulerY();
                float eulerZ = face.getEulerZ();

                float theta = Math.abs(eulerZ); //0-60
                float dirLineLength = Math.abs(eulerY)/60*1000;
                boolean isThetaPositive;
                boolean isLeft;
                double stopX;
                double stopY;

                float globalTheta; // 0-360

                //Drawing a looking direction line from the middle of the face. Using only rotation details
                //All the details according to the person, not the camera.
                if (eulerZ > 0) {isThetaPositive = false;}
                else {isThetaPositive = true;}
                if (eulerY > 0) {isLeft = false;}       // Left is person's left
                else {isLeft = true;}

                //Defining global theta(0-360) taking into consideration isThetaPositive and IsLeft
                if(isThetaPositive && isLeft){ // Third Quadrant (180-270) range 180(0)-240(60)
                    globalTheta = 180 + theta;
                }
                else if(isThetaPositive && !isLeft){    // First Quadrant (0-90) range 0(0)-60(60)
                    globalTheta = theta;
                }
                else if(!isThetaPositive && isLeft){   // Second Quadrant(90-180) range 120(60)-180(0)
                    globalTheta =  180 - theta;
                }
                else{  //4th Quadrant (270-360)  range 300(60)-360(0)
                    globalTheta = 360 - theta;
                }

                stopX = x_canvas+dirLineLength*Math.cos(Math.toRadians(globalTheta));
                stopY = y_canvas+dirLineLength*Math.sin(Math.toRadians(globalTheta));
                canvas.drawLine(x_canvas,y_canvas,(float)stopX,(float)stopY,mFacePositionPaint);

                //Assinging globalTheta to relevant person
                if(name == Data.PARENT){Data.Parent.globalTheta = globalTheta;}
                if(name == Data.CHILD){Data.Child.globalTheta = globalTheta;}
            }
        }
        else{
            name = Data.UNKNOWN;
        }
        canvas.drawText(name, x_canvas + ID_X_OFFSET, y_canvas + ID_Y_OFFSET, mIdPaint);
    }
}