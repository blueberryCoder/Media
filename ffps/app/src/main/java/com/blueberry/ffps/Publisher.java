package com.blueberry.ffps;

/**
 * Created by blueberry on 1/9/2017.
 */

public class Publisher {
    static {
        System.loadLibrary("ffps");
    }

    public static native int init(String outUrl, int w, int h);

    public static native int push(byte[] data, int w, int h);

    public static native int stop();
}
