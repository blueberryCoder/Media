package com.blueberry.mediacodec.utils;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;

import android.media.MediaCodecInfo;
import android.util.Log;

/**
 * Created by muyonggang on 2023/1/19
 */
public class PixelUtils {
   public static void logColorFormatName(String tag, int format) {
        switch (format) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                Log.d(tag, "COLOR_FormatYUV420Flexible");
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                Log.d(tag, "COLOR_FormatYUV420PackedPlanar");
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                Log.d(tag, "COLOR_FormatYUV420Planar");
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                Log.d(tag, "COLOR_FormatYUV420PackedSemiPlanar");
                break;
            case COLOR_FormatYUV420SemiPlanar:
                Log.d(tag, "COLOR_FormatYUV420SemiPlanar");
                break;
        }
    }
}
