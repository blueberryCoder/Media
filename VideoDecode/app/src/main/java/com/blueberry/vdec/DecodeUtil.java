package com.blueberry.vdec;

/**
 * Created by blueberry on 12/23/2016.
 */

public class DecodeUtil {
    static {
        System.loadLibrary("decode");
    }

    public static native int decode(String inputUrl,String outputUrl);
}
