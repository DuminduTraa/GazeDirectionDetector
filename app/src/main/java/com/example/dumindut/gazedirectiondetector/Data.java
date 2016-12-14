package com.example.dumindut.gazedirectiondetector;

import java.util.ArrayList;

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

    public static int faceCount = 0;
    public static float areadiff = 0; //  0 - 1

    public static boolean isIdentified = false;


    public static ArrayList<Integer> ids = new ArrayList<Integer>();
    public static ArrayList<Float> positionX = new ArrayList<Float>();
    public static ArrayList<Float> positionY = new ArrayList<Float>();
    public static ArrayList<Float> faceHeight = new ArrayList<Float>();
    public static ArrayList<Float> faceWidth = new ArrayList<Float>();

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

    static void addNew(int id, float x, float y, float height, float width){
        ids.add(id);
        positionX.add(x);
        positionY.add(y);
        faceHeight.add(height);
        faceWidth.add(width);
    }

    static void updateNew(int index, int id, float x, float y, float height, float width){
        ids.remove(index);
        positionX.remove(index);
        positionY.remove(index);
        faceHeight.remove(index);
        faceWidth.remove(index);
        ids.add(index, id);
        positionX.add(index, x);
        positionY.add(index, y);
        faceHeight.add(index, height);
        faceWidth.add(index, width);
    }

    static void clearData(){
        ids.clear();
        positionX.clear();
        positionY.clear();
        faceHeight.clear();
        faceWidth.clear();

        faceCount = 0;
        areadiff = 0;
        isIdentified = false;
    }


    static void addUnknownToParent(int i){
        updateParent(ids.get(i), positionX.get(i), positionY.get(i), faceHeight.get(i),faceWidth.get(i));
    }

    static void addUnknownToChild(int i){
        updateChild(ids.get(i), positionX.get(i), positionY.get(i), faceHeight.get(i),faceWidth.get(i));
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
        public static Integer id ;
        public static float x;
        public static float y;
        public static float faceWidth;
        public static float faceHeight;

        public static float globalTheta;

        public static long lastTime;
    }


    public static class Child {
        public static Integer id;
        public static float x;
        public static float y;
        public static float faceWidth;
        public static float faceHeight;

        public static float globalTheta;

        public static long lastTime;
    }
}