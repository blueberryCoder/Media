package com.blueberry.ffps;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

import static android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
import static android.hardware.Camera.Parameters.FOCUS_MODE_AUTO;
import static android.hardware.Camera.Parameters.PREVIEW_FPS_MAX_INDEX;
import static android.hardware.Camera.Parameters.PREVIEW_FPS_MIN_INDEX;

/**
 * 使用FFmpeg进行视频编码，推流
 */
public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback2 {

    private static final String TAG = "MainActivity";

    private EditText etOutput;
    private Button btnStart;
    private SurfaceView mSurfaceView;

    private SurfaceHolder mSurfaceHolder;

    private Camera mCamera;
    private Camera.Size previewSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        etOutput = (EditText) findViewById(R.id.et_output);
        btnStart = (Button) findViewById(R.id.btn_start);
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePublish();
            }
        });
    }

    private void togglePublish() {
        if (isStarted) {
            stop();
        } else {
            start();
        }
        btnStart.setText(isStarted ? "停止" : "开始");
    }

    private boolean isStarted;

    // publish
    private void start() {
        isStarted = true;
        // init
        Publisher.init("rtmp://192.168.155.1:1935/live/test",previewSize.width,previewSize.height);
        Log.d(TAG,"初始化完成");
    }

    private void stop() {
        isStarted = false;
        Publisher.stop();
        Log.d(TAG,"停止");
    }

    public void initCamera() {
        try {
            if (mCamera == null) {
                mCamera = Camera.open();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "打开摄像头失败", Toast.LENGTH_LONG).show();
            throw new RuntimeException("打开摄像头失败", e);
        }
        setParameters();

        setCameraDisplayOrientation(MainActivity.this, CAMERA_FACING_BACK, mCamera);
        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.addCallbackBuffer(new byte[getPreviewByteSize(ImageFormat.NV21)]);
        mCamera.setPreviewCallbackWithBuffer(getPreviewByteCallBack());
        mCamera.startPreview();
    }

    private void setParameters() {
        // set params
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFocusMode(FOCUS_MODE_AUTO);
        parameters.setPreviewFormat(ImageFormat.NV21);
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        for (Camera.Size size : supportedPreviewSizes) {
            if (size.width >= 160 && size.height <= 360) {
                previewSize = size;
                Log.d(TAG, String.format("select preview'size width:%d,height:%d",
                        previewSize.width, previewSize.height));
                break;
            }
        }
        parameters.setPreviewSize(previewSize.width, previewSize.height);
        List<int[]> supportedPreviewFpsRange = parameters.getSupportedPreviewFpsRange();
        int defRange = 30 * 1000;
        int[] destRange = {30 * 1000, 30 * 1000};
        for (int[] fpsRange :
                supportedPreviewFpsRange) {
            if (destRange[PREVIEW_FPS_MIN_INDEX] >= defRange) {
                destRange = fpsRange;
            }
        }
        parameters.setPreviewFpsRange(destRange[PREVIEW_FPS_MIN_INDEX],
                destRange[PREVIEW_FPS_MAX_INDEX]);

        mCamera.setParameters(parameters);
    }

    private int getPreviewByteSize(int format) {

        return ImageFormat.getBitsPerPixel(format) * previewSize.width * previewSize.height / 8;
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

    @Override
    protected void onResume() {
        super.onResume();
        initCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCamera != null) {
            mCamera.release();
        }
        mCamera = null;

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

    public Camera.PreviewCallback getPreviewByteCallBack() {
        return new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if(isStarted){
                    Publisher.push(data,previewSize.width,previewSize.height);

                }

                if(data!=null){
                    camera.addCallbackBuffer(data);
                }else{
                    camera.addCallbackBuffer(new byte[getPreviewByteSize(ImageFormat.NV21)]);
                }
            }
        };
    }
}
