package com.example.dumindut.gazedirectiondetector;

/**
 * Created by dumindut on 29/8/2016.
 */

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

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
        float faceArea = width*height;

        float x = face.getPosition().x + width / 2;
        float y = face.getPosition().y + height / 2;
        float x_canvas = translateX(x);
        float y_canvas = scaleY(y);

        double rootSquareDifThreshold = Math.sqrt((width/2)*(width/2)+(height/2)*(height/2));

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
            if(mFaceId != Data.ids.get(0) && rootSquareDiff > rootSquareDifThreshold) {
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
                if(mFaceId == Data.ids.get(0) || rootSquareDiff < rootSquareDifThreshold){
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
                    if(mFaceId == Data.ids.get(0) || rootSquareDiff < rootSquareDifThreshold){
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
                    if(mFaceId == Data.ids.get(0) || rootSquareDiff < rootSquareDifThreshold){
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
                if(mFaceId == Data.Parent.id || rootSquareDiff < rootSquareDifThreshold ){
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

                    //Detecting whether parent looking at child
                    if(name == Data.PARENT){
                        Data.Parent.globalTheta = globalTheta;

                        double thetaThreshold1;   //to y-faceheight/3
                        double thetaThreshold2;     //to y+faceheight/3
                        double thetaThresholdHigh;
                        double thetaThresholdLow;

                        thetaThreshold1 = Math.atan2(Data.Child.y-Data.Child.faceHeight/4-y, Data.Child.x-x);
                        thetaThreshold2 = Math.atan2(Data.Child.y+Data.Child.faceHeight/4-y, Data.Child.x-x);
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
                                Log.e("FaceGraphic","Parent looking at child");
                            }
                            else{Data.isParentLookingAtChild=false;}
                        }
                        //other cases
                        else{
                            if(globalTheta>thetaThresholdLow && globalTheta<thetaThresholdHigh){
                                Data.isParentLookingAtChild=true;
                                Log.e("FaceGraphic","Parent looking at child");
                            }
                            else{Data.isParentLookingAtChild=false;}
                        }
                    }
                    //Detecting whether child looking at parent
                    else{
                        Data.Child.globalTheta = globalTheta;
 
                        double thetaThreshold1;   //to y-faceheight/3
                        double thetaThreshold2;     //to y+faceheight/3
                        double thetaThresholdHigh;
                        double thetaThresholdLow;

                        thetaThreshold1 = Math.atan2(Data.Parent.y-Data.Parent.faceHeight/4-y, Data.Parent.x-x);
                        thetaThreshold2 = Math.atan2(Data.Parent.y+Data.Parent.faceHeight/4-y, Data.Parent.x-x);
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
                                Data.isChildLookingAtParent=true;
                                Log.e("FaceGraphic","Child looking at parent");
                            }
                            else{Data.isChildLookingAtParent=false;}
                        }
                        //other cases
                        else{
                            if(globalTheta>thetaThresholdLow && globalTheta<thetaThresholdHigh){
                                Data.isChildLookingAtParent=true;
                                Log.e("FaceGraphic","Child looking at parent");
                            }
                            else{Data.isChildLookingAtParent=false;}
                        }
                    }
                }
            }
        }

        canvas.drawText(name, x_canvas + ID_X_OFFSET, y_canvas + ID_Y_OFFSET, mIdPaint);
    }
}