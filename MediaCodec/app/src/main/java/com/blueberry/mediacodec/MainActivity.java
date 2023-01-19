package com.blueberry.mediacodec;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static android.hardware.Camera.Parameters.FOCUS_MODE_AUTO;
import static android.hardware.Camera.Parameters.PREVIEW_FPS_MAX_INDEX;
import static android.hardware.Camera.Parameters.PREVIEW_FPS_MIN_INDEX;
import static android.media.MediaCodec.CONFIGURE_FLAG_ENCODE;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
import static android.media.MediaFormat.KEY_BIT_RATE;
import static android.media.MediaFormat.KEY_COLOR_FORMAT;
import static android.media.MediaFormat.KEY_FRAME_RATE;
import static android.media.MediaFormat.KEY_I_FRAME_INTERVAL;
import static android.media.MediaFormat.KEY_MAX_INPUT_SIZE;

/**
 * https://developer.android.google.cn/reference/android/media/MediaCodec.html#dequeueInputBuffer(long)
 */

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback2 {

    private static final String TAG = "MainActivity";

    private static final String VCODEC_MIME = "video/avc";
    private static final String ACODEC = "audio/mp4a-latm";

    private EditText etOutput;
    private Button btnStart;
    private SurfaceView mSurfaceView;

    private SurfaceHolder mSurfaceHolder;

    private Camera mCamera;
    private Camera.Size previewSize;

    private boolean isStarted;
    private int videoTrackIndex;
    private int audioTrackIndex;
    private int colorFormat;
    private long presentationTimeUs;

    private AudioRecord mAudioRecord;

    private MediaCodec.BufferInfo vBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec.BufferInfo aBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec vencoder;
    private MediaMuxer mediaMuxer;

    private int aSampleRate;
    private int abits;
    private int aChannelCount;
    private byte[] abuffer;

    private static final int ABITRATE_KBPS = 30;

    private MediaCodec aEncoder;
    private boolean aloop;
    private Thread audioWorkThread;

    private FileOutputStream avcFos = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        etOutput = (EditText) findViewById(R.id.et_output_url);
        btnStart = (Button) findViewById(R.id.btn_start);
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mSurfaceView.setKeepScreenOn(true);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
            @Override
            public void onClick(View v) {
                codecToggle();
            }
        });
    }

    private void codecToggle() {
        if (isStarted) {
            stop();
        } else {
            start();
        }
        btnStart.setText(isStarted ? "停止" : "开始");
    }

    private void start() {
        isStarted = true;
        if (mCamera != null) {
            // 初始化视频编码器
            initVideoEncoder();
            initAudioDevice();
            initAudioEncoder();
        }
        presentationTimeUs = new Date().getTime() * 1000;
        try {
            avcFos = new FileOutputStream(new File("sdcard", Constants.AVC_FILE));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //write mp4 file.
        //https://developer.android.google.cn/reference/android/media/MediaMuxer.html
        try {
            mediaMuxer = new MediaMuxer(etOutput.getText().toString().trim(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            videoTrackIndex = mediaMuxer.addTrack(vencoder.getOutputFormat());
            audioTrackIndex = mediaMuxer.addTrack(aEncoder.getOutputFormat());
            mediaMuxer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initAudioEncoder() {
        try {
            aEncoder = MediaCodec.createEncoderByType(ACODEC);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("初始化音频编码器失败", e);
        }
        Log.d(TAG, String.format("编码器:%s创建完成", aEncoder.getName()));

        MediaFormat aformat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                aSampleRate, aChannelCount);
        aformat.setInteger(KEY_BIT_RATE, 1000 * ABITRATE_KBPS);
        aformat.setInteger(KEY_MAX_INPUT_SIZE, 0);
        aEncoder.configure(aformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        aloop = true;
        mAudioRecord.startRecording();
        audioWorkThread=new Thread(fetchAudioRunnable);
        audioWorkThread.start();
        aEncoder.start();
    }

    private Runnable fetchAudioRunnable = new Runnable() {
        @Override
        public void run() {
            fetchAudioFromDevice();
        }
    };

    private void fetchAudioFromDevice() {
        Log.d(TAG, "录音线程开始");
        while (aloop && mAudioRecord != null && !Thread.interrupted()) {
            int size = mAudioRecord.read(abuffer, 0, abuffer.length);
            if (size < 0) {
                Log.i(TAG, "audio ignore,no data to read.");
                break;
            }
            if (aloop) {
                byte[] audio = new byte[size];
                System.arraycopy(abuffer, 0, audio, 0, size);
                onGetPcmFrame(audio);
            }
        }

        Log.d(TAG, "录音线程结束");
    }

    private void initAudioDevice() {
        //音频采样率，44100是目前的标准，但是某些设备仍然支持22050,16000,11025
        int[] sampleRates = {44100, 22050, 16000, 11025};
        for (int sampleRate : sampleRates) {

            //编码制式PCM
            int audioForamt = AudioFormat.ENCODING_PCM_16BIT;
            // stereo 立体声,mono单声道
            int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;

            int buffsize = 2 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioForamt);
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    sampleRate, channelConfig, audioForamt, buffsize);
            if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "initialized the mic failed");
                continue;
            }
            aSampleRate = sampleRate;
            abits = audioForamt;
            aChannelCount = channelConfig == AudioFormat.CHANNEL_CONFIGURATION_STEREO ? 2 : 1;
            abuffer = new byte[Math.min(4096, buffsize)];
        }
    }

    private void stop() {
        if (!isStarted) return;
        try {
            audioWorkThread.interrupt();
            aloop = false;
            aEncoder.stop();
            aEncoder.release();
            vencoder.stop();
            vencoder.release();

            mAudioRecord.stop();
            mAudioRecord.release();

            mediaMuxer.stop();
            mediaMuxer.release();
        } catch (Exception e) {
        }
        isStarted = false;
    }

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
//        https://developer.android.google.cn/reference/android/media/MediaFormat.html
        MediaFormat mediaFormat = MediaFormat
                .createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, previewSize.width, previewSize.height);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        mediaFormat.setInteger(KEY_BIT_RATE, 300 * 1000); //比特率
        mediaFormat.setInteger(KEY_COLOR_FORMAT, colorFormat);
        mediaFormat.setInteger(KEY_FRAME_RATE, 30);
        mediaFormat.setInteger(KEY_I_FRAME_INTERVAL, 5);

        vencoder.configure(mediaFormat, null, null, CONFIGURE_FLAG_ENCODE);
        vencoder.start();
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
                    logColorFormatName(format);
                    break;
                }
            }
        }
        return matchedFormat;
    }

    private void logColorFormatName(int format) {
        switch (format) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                Log.d(TAG, "COLOR_FormatYUV420Flexible");
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                Log.d(TAG, "COLOR_FormatYUV420PackedPlanar");
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                Log.d(TAG, "COLOR_FormatYUV420Planar");
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                Log.d(TAG, "COLOR_FormatYUV420PackedSemiPlanar");
                break;
            case COLOR_FormatYUV420SemiPlanar:
                Log.d(TAG, "COLOR_FormatYUV420SemiPlanar");
                break;
        }
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

    @Override
    protected void onResume() {
        super.onResume();
        initCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCamera != null) {
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stop();
    }

    private void initCamera() {
        openCamera();
        setParameters();
        setCameraDisplayOrientation(this, Camera.CameraInfo.CAMERA_FACING_BACK, mCamera);
        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
        mCamera.addCallbackBuffer(new byte[calculateLength(ImageFormat.NV21)]);
        mCamera.setPreviewCallback(getPreviewCallBack());
    }

    private void openCamera() throws RuntimeException {
        if (mCamera == null) {
            try {
                mCamera = Camera.open();
            } catch (Exception e) {
                Log.e(TAG, "摄像头打开失败");
                e.printStackTrace();
                Toast.makeText(this, "摄像头不可用!", Toast.LENGTH_LONG).show();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e1) {
                }
                throw new RuntimeException(e);
            }
        }
    }

    private int calculateLength(int format) {
        return previewSize.width * previewSize.height
                * ImageFormat.getBitsPerPixel(format) / 8;
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
        parameters.setPreviewFormat(ImageFormat.NV21);

        // Set preview size.
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        for (Camera.Size size : supportedPreviewSizes) {
            if (size.width >= 240 && size.width <= 680) {
                previewSize = size;
                Log.d(TAG, "select preview size width=" + size.width + ",height=" + size.height);
                break;
            }
        }
        parameters.setPreviewSize(previewSize.width, previewSize.height);

        int defFps = 20 * 1000;
        int[] dstRange = {defFps, defFps};

        //set fps range.
        List<int[]> supportedPreviewFpsRange = parameters.getSupportedPreviewFpsRange();
        for (int[] fps : supportedPreviewFpsRange) {
            if (fps[PREVIEW_FPS_MAX_INDEX] > defFps && fps[PREVIEW_FPS_MIN_INDEX] < defFps) {
                dstRange = fps;
                Log.d(TAG, "find fps:" + Arrays.toString(dstRange));

                break;
            }
        }
        parameters.setPreviewFpsRange(dstRange[PREVIEW_FPS_MIN_INDEX],
                dstRange[PREVIEW_FPS_MAX_INDEX]);
        parameters.setFocusMode(FOCUS_MODE_AUTO);

        mCamera.setParameters(parameters);
    }

    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {
        Log.d(TAG, "surfaceRedrawNeeded: ");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated: ");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged: ");
        initCamera();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed: ");
    }

    public Camera.PreviewCallback getPreviewCallBack() {

        return new Camera.PreviewCallback() {
            byte[] dstByte = new byte[calculateLength(ImageFormat.NV21)];

            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if (isStarted) {
                    if (data != null) {
                        // data 是Nv21
                        if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                            Yuv420Util.Nv21ToYuv420SP(data, dstByte, previewSize.width, previewSize.height);
                        } else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                            Yuv420Util.Nv21ToI420(data, dstByte, previewSize.width, previewSize.height);

                        } else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
                            // Yuv420_888

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
                        camera.addCallbackBuffer(data);
                    } else {
                        camera.addCallbackBuffer(new byte[calculateLength(ImageFormat.NV21)]);
                    }
                }
            }
        };
    }

    private void onGetPcmFrame(byte[] data) {
        ByteBuffer[] inputBuffers = aEncoder.getInputBuffers();
        ByteBuffer[] outputBuffers = aEncoder.getOutputBuffers();
        int inputBufferId = aEncoder.dequeueInputBuffer(-1);
        if (inputBufferId >= 0) {
            ByteBuffer bb = inputBuffers[inputBufferId];
            bb.clear();
            bb.put(data, 0, data.length);
            long pts = new Date().getTime() * 1000 - presentationTimeUs;
            aEncoder.queueInputBuffer(inputBufferId, 0, data.length, pts, 0);
        }

        for (; ; ) {
            int outputBufferId = aEncoder.dequeueOutputBuffer(aBufferInfo, 0);
            if (outputBufferId >= 0) {
                // outputBuffers[outputBufferId] is ready to be processed or rendered.
                ByteBuffer bb = outputBuffers[outputBufferId];
                onEncodeAacFrame(bb, aBufferInfo);
                aEncoder.releaseOutputBuffer(outputBufferId, false);
            }
            if (outputBufferId < 0) {
                break;
            }
        }
    }

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

    private void onEncodeAacFrame(ByteBuffer bb, MediaCodec.BufferInfo info) {
        mediaMuxer.writeSampleData(audioTrackIndex, bb, info);
    }

    // when got encoded h264 es stream.
    private void onEncodedh264Frame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        int oldPos = es.position();
        byte[] arr = new byte[bi.size];
        es.get(arr,bi.offset,bi.size);
        es.position(oldPos);

        try {
            avcFos.write(arr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaMuxer.writeSampleData(videoTrackIndex, es, bi);
    }
}
