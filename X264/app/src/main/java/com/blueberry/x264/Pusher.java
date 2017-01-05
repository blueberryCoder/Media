package com.blueberry.x264;

/**
 * Created by blueberry on 1/3/2017.
 */

public final class Pusher {


    /**
     * 初始化。
     *
     * @param destUrl 目标url
     * @param w       宽
     * @param h       高
     * @return 结果
     */
    public static native int init(String destUrl, int w, int h);

    /**
     * 传入数据。
     *
     * @param bytes
     * @param w
     * @param h
     * @return
     */
    public static native int push(byte[] bytes,int w,int h);

    /**
     * 停止
     * @return
     */
    public static native int stop();
}
