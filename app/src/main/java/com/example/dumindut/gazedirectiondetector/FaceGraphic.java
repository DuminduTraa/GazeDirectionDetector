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
        /*canvas.drawCircle(x, y, FACE_POSITION_RADIUS, mFacePositionPaint);*/

        canvas.drawText("id: " + mFaceId, x + ID_X_OFFSET, y + ID_Y_OFFSET, mIdPaint);
        /*canvas.drawText("right eye: " + String.format("%.2f", face.getIsRightEyeOpenProbability()), x + ID_X_OFFSET * 2, y + ID_Y_OFFSET * 2, mIdPaint);
        canvas.drawText("left eye: " + String.format("%.2f", face.getIsLeftEyeOpenProbability()), x - ID_X_OFFSET*2, y - ID_Y_OFFSET*2, mIdPaint);*/

        canvas.drawText("rotationY: " + String.format("%.2f", face.getEulerY()), 50,50, mIdPaint);
        canvas.drawText("rotationZ: " + String.format("%.2f", face.getEulerZ()), 50,100, mIdPaint);

        for (Landmark landmark : face.getLandmarks()) {
            if (landmark.getType() == Landmark.RIGHT_EYE) {
                float cx = translateX(landmark.getPosition().x);
                float cy = translateY(landmark.getPosition().y);
                canvas.drawCircle(cx, cy, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.LEFT_EYE) {
                float cx = translateX(landmark.getPosition().x);
                float cy = translateY(landmark.getPosition().y);
                canvas.drawCircle(cx, cy, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.LEFT_CHEEK) {
                float cx = translateX(landmark.getPosition().x);
                float cy = translateY(landmark.getPosition().y);
                canvas.drawCircle(cx, cy, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.RIGHT_CHEEK) {
                float cx = translateX(landmark.getPosition().x);
                float cy = translateY(landmark.getPosition().y);
                canvas.drawCircle(cx, cy, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.NOSE_BASE) {
                float cx = translateX(landmark.getPosition().x);
                float cy = translateY(landmark.getPosition().y);
                canvas.drawCircle(cx, cy, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
            else if (landmark.getType() == Landmark.BOTTOM_MOUTH) {
                float cx = translateX(landmark.getPosition().x);
                float cy = translateY(landmark.getPosition().y);
                canvas.drawCircle(cx, cy, FACE_POSITION_RADIUS, mFacePositionPaint);
            }
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
