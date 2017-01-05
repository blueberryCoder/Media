package com.blueberry.x264;

/**
 * Created by blueberry on 12/28/2016.
 */

public class Encoder {


    /**
     * 接受一个1280x720分辨率的YUV格式文件
     *
     * @param inputUrl
     * @param outputUrl
     * @return
     */
    public static native int encode(String inputUrl, String outputUrl);
}
