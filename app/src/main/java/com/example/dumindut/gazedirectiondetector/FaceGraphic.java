package com.example.dumindut.gazedirectiondetector;

/**
 * Created by dumindut on 29/8/2016.
 */

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.example.dumindut.gazedirectiondetector.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.face.Face;

/*Graphic instance for rendering face position, orientation, and landmarks
    within an associated graphic overlay view.*/
class FaceGraphic extends GraphicOverlay.Graphic {
    private static final float FACE_POSITION_RADIUS = 10.0f;
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

    /**
     * Updates the face instance from the detection of the most recent frame.  Invalidates the
     * relevant portions of the overlay to trigger a redraw.
     */
    void updateFace(Face face) {
        mFace = face;
        postInvalidate();
    }

    /**
     * Draws the face annotations for position on the supplied canvas.
     */
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

        String name = Data.UNKNOWN;

        canvas.drawCircle(x_canvas, y_canvas, FACE_POSITION_RADIUS, mFacePositionPaint);
        canvas.drawCircle(translateX(Data.meetX), scaleY(Data.meetY), FACE_POSITION_RADIUS, mFacePositionPaint);

        // Draws a bounding box around the face.
        float xOffset = scaleX(face.getWidth() / 2.0f);
        float yOffset = scaleY(face.getHeight() / 2.0f);
        float left = x_canvas - xOffset;
        float top = y_canvas - yOffset;
        float right = x_canvas + xOffset;
        float bottom = y_canvas + yOffset;
        canvas.drawRect(left, top, right, bottom, mBoxPaint);

        float eulerY = face.getEulerY();
        float eulerZ = face.getEulerZ();

        ///////////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////////////////////////////////////////////////////////////////////////
        /*
        If parent and child has been identified by the age detection task, then deriving at their
        looking directions using rotation details and storing in Data class. Also face tracking
        is done using face ID s and respective positions of the faces.
         */
        if(Data.isIdentified){  //Parent and Child defined
            //Updating parent, child basic information and keep tracking.
            if(mFaceId == Data.Parent.id){
                name = Data.PARENT;
                Data.updateParent(mFaceId,x,y,height,width,eulerZ,eulerY);
            }
            else if(mFaceId == Data.Child.id) {
                name = Data.CHILD;
                Data.updateChild(mFaceId,x,y,height,width,eulerZ,eulerY);
            }
            else{
                float sqrDistanceToParent = Math.abs(x-Data.Parent.x)*Math.abs(x-Data.Parent.x) +
                        Math.abs(y-Data.Parent.y)*Math.abs(y-Data.Parent.y);
                float sqrDistanceToChild = Math.abs(x-Data.Child.x)*Math.abs(x-Data.Child.x) +
                        Math.abs(y-Data.Child.y)*Math.abs(y-Data.Child.y);
                if(sqrDistanceToParent < sqrDistanceToChild){
                    name = Data.PARENT;
                    Data.updateParent(mFaceId,x,y,height,width,eulerZ,eulerY);
                }
                else{
                    name = Data.CHILD;
                    Data.updateChild(mFaceId,x,y,height,width,eulerZ,eulerY);
                }
            }
        }
        canvas.drawText(name, x_canvas + ID_X_OFFSET, y_canvas + ID_Y_OFFSET, mIdPaint);
    }
}