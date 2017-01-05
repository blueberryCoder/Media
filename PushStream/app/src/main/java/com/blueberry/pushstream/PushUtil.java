package com.blueberry.pushstream;

/**
 * Created by blueberry on 12/26/2016.
 */

public class PushUtil {
    static {
        System.loadLibrary("pubsher");
    }


    public  static native int stream(String inputStr,String outputStr);
}
