package com.blueberry.hellortmp;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static android.hardware.Camera.Parameters.FOCUS_MODE_AUTO;
import static android.hardware.Camera.Parameters.PREVIEW_FPS_MAX_INDEX;
import static android.hardware.Camera.Parameters.PREVIEW_FPS_MIN_INDEX;
import static android.media.MediaCodec.CONFIGURE_FLAG_ENCODE;
import static android.media.MediaFormat.KEY_BIT_RATE;
import static android.media.MediaFormat.KEY_COLOR_FORMAT;
import static android.media.MediaFormat.KEY_FRAME_RATE;
import static android.media.MediaFormat.KEY_I_FRAME_INTERVAL;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback2 {

    static {
        System.loadLibrary("hellortmp");
    }

    static final int NAL_SLICE = 1;
    static final int NAL_SLICE_DPA = 2;
    static final int NAL_SLICE_DPB = 3;
    static final int NAL_SLICE_DPC = 4;
    static final int NAL_SLICE_IDR = 5;
    static final int NAL_SEI = 6;
    static final int NAL_SPS = 7;
    static final int NAL_PPS = 8;
    static final int NAL_AUD = 9;
    static final int NAL_FILLER = 12;


    private static final String VCODEC_MIME = "video/avc";

    private Button btnStart;
    private SurfaceView mSurfaceView;

    private SurfaceHolder mSurfaceHolder;

    private Camera mCamera;
    private boolean isStarted;
    private int colorFormat;
    private long presentationTimeUs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnStart = (Button) findViewById(R.id.btn_start);
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePublish();
            }
        });

        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
    }

    private void togglePublish() {
        if (isStarted) {
            stop();
        } else {
            start();
        }

        btnStart.setText(isStarted ? "停止" : "开始");
    }

    private void start() {
        isStarted = true;
        //
        initVideoEncoder();
        presentationTimeUs = new Date().getTime() * 1000;

        Rtmp.init("rtmp://192.168.155.1:1935/live/test", 5);
    }

    private MediaCodec vencoder;


    private void initVideoEncoder() {
        MediaCodecInfo mediaCodecInfo = selectCodec(VCODEC_MIME);
        colorFormat = getColorFormat(mediaCodecInfo);
        try {
            vencoder = MediaCodec.createByCodecName(mediaCodecInfo.getName());
            Log.d(TAG, "编码器:" + mediaCodecInfo.getName() + "创建完成!");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("vencodec初始化失败！", e);
        }
        MediaFormat mediaFormat = MediaFormat
                .createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                        previewSize.width, previewSize.height);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        mediaFormat.setInteger(KEY_BIT_RATE, 300 * 1000); //比特率
        mediaFormat.setInteger(KEY_COLOR_FORMAT, colorFormat);
        mediaFormat.setInteger(KEY_FRAME_RATE, 20);
        mediaFormat.setInteger(KEY_I_FRAME_INTERVAL, 5);

        vencoder.configure(mediaFormat, null, null, CONFIGURE_FLAG_ENCODE);
        vencoder.start();
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private int getColorFormat(MediaCodecInfo mediaCodecInfo) {
        int matchedFormat = 0;
        MediaCodecInfo.CodecCapabilities codecCapabilities =
                mediaCodecInfo.getCapabilitiesForType(VCODEC_MIME);
        for (int i = 0; i < codecCapabilities.colorFormats.length; i++) {
            int format = codecCapabilities.colorFormats[i];
            if (format >= codecCapabilities.COLOR_FormatYUV420Planar &&
                    format <= codecCapabilities.COLOR_FormatYUV420PackedSemiPlanar) {
                if (format >= matchedFormat) {
                    matchedFormat = format;
                    break;
                }
            }
        }
        return matchedFormat;
    }

    private void stop() {
        isStarted = false;

        vencoder.stop();
        vencoder.release();

        Rtmp.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
        }
        mCamera = null;
    }

    private void initCamera() {
        try {
            if (mCamera == null) {
                mCamera = Camera.open();
            }
        } catch (Exception e) {
            throw new RuntimeException("open camera fail", e);
        }

        setParameters();
        setCameraDisplayOrientation(this, Camera.CameraInfo.CAMERA_FACING_BACK, mCamera);
        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mCamera.addCallbackBuffer(new byte[calculateFrameSize(ImageFormat.NV21)]);
        mCamera.setPreviewCallbackWithBuffer(getPreviewCallback());
        mCamera.startPreview();
    }

    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    private void setParameters() {
        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();

        for (Camera.Size size : supportedPreviewSizes
                ) {
            if (size.width <= 360 && size.width >= 180) {
                previewSize = size;
                Log.d(TAG, "select size width=" + size.width + ",height=" + size.height);
                break;
            }
        }
        List<int[]> supportedPreviewFpsRange = parameters.getSupportedPreviewFpsRange();
        int[] destRange = {30 * 1000, 30 * 1000};

        for (int[] range : supportedPreviewFpsRange
                ) {
            if (range[PREVIEW_FPS_MIN_INDEX] >= 30 * 1000 && range[PREVIEW_FPS_MAX_INDEX] <= 100 * 1000) {
                destRange = range;
                break;
            }
        }
        parameters.setPreviewSize(previewSize.width, previewSize.height);
        parameters.setPreviewFpsRange(destRange[PREVIEW_FPS_MIN_INDEX],
                destRange[PREVIEW_FPS_MAX_INDEX]);
        parameters.setPreviewFormat(ImageFormat.NV21);
        parameters.setFocusMode(FOCUS_MODE_AUTO);

        mCamera.setParameters(parameters);
    }

    private static final String TAG = "MainActivity";
    private Camera.Size previewSize;


    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        initCamera();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }


    public Camera.PreviewCallback getPreviewCallback() {
        return new Camera.PreviewCallback() {
            byte[] dstByte = new byte[calculateFrameSize(ImageFormat.NV21)];

            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if (data == null) {
                    mCamera.addCallbackBuffer(new byte[calculateFrameSize(ImageFormat.NV21)]);
                } else {
                    if (isStarted) {
                        // data 是Nv21
                        if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                            Yuv420Util.Nv21ToYuv420SP(data, dstByte, previewSize.width, previewSize.height);
                        } else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                            Yuv420Util.Nv21ToI420(data, dstByte, previewSize.width, previewSize.height);
                        } else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar) {
                            // Yuv420packedPlannar 和 yuv420sp很像
                            // 区别在于 加入 width = 4的话 y1,y2,y3 ,y4公用 u1v1
                            // 而 yuv420dp 则是 y1y2y5y6 共用 u1v1
                            //http://blog.csdn.net/jumper511/article/details/21719313

                            //这样处理的话颜色核能会有些失真。
                            Yuv420Util.Nv21ToYuv420SP(data, dstByte, previewSize.width, previewSize.height);
                        } else {
                            System.arraycopy(data, 0, dstByte, 0, data.length);
                        }

                        onGetVideoFrame(dstByte);
                    }
                    mCamera.addCallbackBuffer(data);

                }

            }
        };
    }

    private MediaCodec.BufferInfo vBufferInfo = new MediaCodec.BufferInfo();

    private void onGetVideoFrame(byte[] i420) {
//        MediaCodec
        ByteBuffer[] inputBuffers = vencoder.getInputBuffers();
        ByteBuffer[] outputBuffers = vencoder.getOutputBuffers();

        int inputBufferId = vencoder.dequeueInputBuffer(-1);
        if (inputBufferId >= 0) {
            // fill inputBuffers[inputBufferId] with valid data
            ByteBuffer bb = inputBuffers[inputBufferId];
            bb.clear();
            bb.put(i420, 0, i420.length);
            long pts = new Date().getTime() * 1000 - presentationTimeUs;
            vencoder.queueInputBuffer(inputBufferId, 0, i420.length, pts, 0);
        }

        for (; ; ) {
            int outputBufferId = vencoder.dequeueOutputBuffer(vBufferInfo, 0);
            if (outputBufferId >= 0) {
                // outputBuffers[outputBufferId] is ready to be processed or rendered.
                ByteBuffer bb = outputBuffers[outputBufferId];
                onEncodedh264Frame(bb, vBufferInfo);
                vencoder.releaseOutputBuffer(outputBufferId, false);
            }
            if (outputBufferId < 0) {
                break;
            }
        }
    }

    private void onEncodedh264Frame(ByteBuffer bb, MediaCodec.BufferInfo vBufferInfo) {
        int offset = 4;
        //判断帧的类型
        if (bb.get(2) == 0x01) {
            offset = 3;
        }

        int type = bb.get(offset) & 0x1f;

        switch (type) {
            case NAL_SLICE:
                Log.d(TAG, "type=NAL_SLICE");
                break;
            case NAL_SLICE_DPA:
                Log.d(TAG, "type=NAL_SLICE_DPA");
                break;
            case NAL_SLICE_DPB:
                Log.d(TAG, "type=NAL_SLICE_DPB");
                break;
            case NAL_SLICE_DPC:
                Log.d(TAG, "type=NAL_SLICE_DPC");
                break;
            case NAL_SLICE_IDR: //关键帧
                Log.d(TAG, "type=NAL_SLICE_IDR");
                break;
            case NAL_SEI:
                Log.d(TAG, "type=NAL_SEI");
                break;
            case NAL_SPS: // sps
                Log.d(TAG, "type=NAL_SPS");
                //[0, 0, 0, 1, 103, 66, -64, 13, -38, 5, -126, 90, 1, -31, 16, -115, 64, 0, 0, 0, 1, 104, -50, 6, -30]
                //打印发现这里将 SPS帧和 PPS帧合在了一起发送
                // SPS为 [4，len-8]
                // PPS为后4个字节
                //so .
                byte[] pps = new byte[4];
                byte[] sps = new byte[vBufferInfo.size - 12];
                bb.getInt();// 抛弃 0,0,0,1
                bb.get(sps, 0, sps.length);
                bb.getInt();
                bb.get(pps, 0, pps.length);
                Log.d(TAG, "解析得到 sps:" + Arrays.toString(sps) + ",PPS=" + Arrays.toString(pps));

                Rtmp.sendSpsAndPps(sps, sps.length, pps, pps.length, vBufferInfo.presentationTimeUs / 1000);
                return;
            case NAL_PPS: // pps
                Log.d(TAG, "type=NAL_PPS");
                break;
            case NAL_AUD:
                Log.d(TAG, "type=NAL_AUD");
                break;
            case NAL_FILLER:
                Log.d(TAG, "type=NAL_FILLER");
                break;
        }
        byte[] bytes = new byte[vBufferInfo.size];
        bb.get(bytes);
        Rtmp.sendVideoFrame(bytes, bytes.length, vBufferInfo.presentationTimeUs / 1000);

    }

    private int calculateFrameSize(int format) {
        return previewSize.width * previewSize.height * ImageFormat.getBitsPerPixel(format) / 8;
    }
}
