package com.blueberry.helloffmpeg;

/**
 * Created by blueberry on 12/23/2016.
 */

public class FFmpegUtil {
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("helloworld");
    }


    public static native String configInfo();

    public static native String formatInfo();

    public static native String filterInfo();

    public static native String protocolInfo();

    public static native String avCodecInfo();
}
