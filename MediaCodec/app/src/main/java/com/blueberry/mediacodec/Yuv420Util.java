package com.blueberry.mediacodec;

/**
 * Created by blueberry on 1/5/2017.
 */

public class Yuv420Util {
    /**
     * Nv21:
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * VUVU
     * VUVU
     * VUVU
     * VUVU
     * <p>
     * I420:
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * UUUU
     * UUUU
     * VVVV
     * VVVV
     *
     * @param data
     * @param dstData
     * @param w
     * @param h
     */
    public static void Nv21ToI420(byte[] data, byte[] dstData, int w, int h) {

        int size = w * h;
        // Y
        System.arraycopy(data, 0, dstData, 0, size);
        for (int i = 0; i < size / 4; i++) {
            dstData[size + i] = data[size + i * 2 + 1]; //U
            dstData[size + size / 4 + i] = data[size + i * 2]; //V
        }
    }

    public static void Nv21ToYuv420SP(byte[] data, byte[] dstData, int w, int h) {
        int size = w * h;
        // Y
        System.arraycopy(data, 0, dstData, 0, size);

        for (int i = 0; i < size / 4; i++) {
            dstData[size + i * 2] = data[size + i * 2 + 1]; //U
            dstData[size + i * 2 + 1] = data[size + i * 2]; //V
        }
    }


    public static void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height) {
        if (nv21 ==null || nv12 ==null)return;
        int framesize = width * height;
        int i =0, j =0;
        System.arraycopy(nv21,0, nv12,0, framesize);
        for (i =0; i < framesize; i++) {
            nv12[i] = nv21[i];
        }
        for (j =0; j < framesize /2; j +=2) {
            nv12[framesize + j -1] = nv21[j + framesize];
        }

        for (j =0; j < framesize /2; j +=2) {
            nv12[framesize + j] = nv21[j + framesize -1];
        }
    }
}