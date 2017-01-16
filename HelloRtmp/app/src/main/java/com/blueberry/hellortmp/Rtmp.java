package com.blueberry.hellortmp;

/**
 * Created by blueberry on 1/13/2017.
 */

public final class Rtmp {

    public static final native int init(String url, int timeOut);

    public static final native int sendSpsAndPps(byte[] sps, int sysLen, byte[] pps, int ppsLen, long time);

    public static final native int sendVideoFrame(byte[] frame, int len, long time);

    public static final native  int stop();

}
