package com.blueberry.mediacodec;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.EditText;

import java.io.IOException;
import java.util.List;

import static android.hardware.Camera.Parameters.FOCUS_MODE_AUTO;
import static android.hardware.Camera.Parameters.PREVIEW_FPS_MAX_INDEX;
import static android.hardware.Camera.Parameters.PREVIEW_FPS_MIN_INDEX;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback2 {

    private static final String TAG = "MainActivity";

    private EditText etOutput;
    private Button btnStart;
    private SurfaceView mSurfaceView;

    private SurfaceHolder mSurfaceHolder;

    private Camera mCamera;
    private byte[] i420;

    private MediaCodec vencoder;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        etOutput = (EditText) findViewById(R.id.et_output_url);
        btnStart = (Button) findViewById(R.id.btn_start);
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);

        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);

        vencoder = MediaCodec.createByCodecName()
    }

    private Camera.Size previewSize;

    private void initCamera() {
        if (mCamera != null) {
            try {
                mCamera = Camera.open();
            } catch (Exception e) {
                Log.e(TAG, "摄像头打开失败");
                e.printStackTrace();
                return;
            }
        }

        setParameters();
        setCameraDisplayOrientation(this, Camera.CameraInfo.CAMERA_FACING_BACK, mCamera);
        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();

        mCamera.addCallbackBuffer(new byte[calculateLength(ImageFormat.NV21)]);
        i420 = new byte[calculateLength(ImageFormat.NV21)];
        mCamera.setPreviewCallback(getPreviewCallBack());
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

        int defFps = 30 * 1000;
        int[] dstRange = {defFps, defFps};

        //set fps range.
        List<int[]> supportedPreviewFpsRange = parameters.getSupportedPreviewFpsRange();
        for (int[] fps : supportedPreviewFpsRange) {
            if (fps[PREVIEW_FPS_MIN_INDEX] > defFps) {
                dstRange = fps;
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

    public Camera.PreviewCallback getPreviewCallBack() {
        return new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                // data 是Nv21
                Yuv420Util.Nv21ToI420(data, i420, previewSize.width, previewSize.height);
                // process i420
                processFrame(i420);
                camera.addCallbackBuffer(data);
            }
        };
    }

    /**
     * compress
     *
     * @param i420
     */
    private void processFrame(byte[] i420) {
//        MediaCodec
    }
}
