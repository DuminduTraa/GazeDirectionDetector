package com.example.dumindut.gazedirectiondetector;

/**
 * Created by dumindut on 15/11/2016.
 */
public class Data {

    public static final String PARENT = "parent";
    public static final String CHILD = "child";
    public static final String UNKNOWN = "unknown";

    public static int previewWidth = 640;
    public static int previewHeight = 480;

    public static float meetX = 0;
    public static float meetY = 0;

    public static boolean isIdentified = false;

    //Variables for the features
    public static boolean isParentLookingAtChild = false;
    public static boolean isChildLookingAtParent = false;
    public static boolean hasEyeContact = false;
    public static boolean hasJointAttention = false;
    public static String  parentEmotion1 = "No Emotion";
    public static String parentEmotion2 = "No Emotion";
    public static float parentEmotion1Value = 0;
    public static float parentEmotion2Value = 0;
    public static String childEmotion1 = "No Emotion";
    public static String childEmotion2 = "No Emotion";
    public static float childEmotion1Value = 0;
    public static float childEmotion2Value = 0;


    static void clearData(){
        isIdentified = false;
    }

    static void updateParent(int id, float x, float y, float height, float width ){
        Parent.id = id;
        Parent.x = x;
        Parent.y = y;
        Parent.faceHeight = height;
        Parent.faceWidth = width;
    }

    static void updateChild(int id, float x, float y, float height, float width){
        Child.id = id;
        Child.x = x;
        Child.y = y;
        Child.faceHeight = height;
        Child.faceWidth = width;
    }

    public static class Parent {
        public static int id = -1;
        public static float x;
        public static float y;
        public static float faceWidth;
        public static float faceHeight;

        public static float globalTheta;
        public static long lastTime;
    }

    public static class Child {
        public static int id = -2;
        public static float x;
        public static float y;
        public static float faceWidth;
        public static float faceHeight;

        public static float globalTheta;
        public static long lastTime;
    }
}