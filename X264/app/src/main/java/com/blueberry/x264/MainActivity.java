package com.blueberry.x264;

import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    static {
        System.loadLibrary("encode");
    }
    private EditText etInput;
    private EditText etOutput;
    private Button btnStart;
    private Button btnCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etInput = (EditText) findViewById(R.id.et_input);
        etOutput = (EditText) findViewById(R.id.et_output);



        etInput.setText("out.yuv");
        etOutput.setText("out.h264");
        btnStart = (Button) findViewById(R.id.btn_start);
        btnCamera = (Button) findViewById(R.id.btn_camera);

        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
            }
        });


        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                new Thread() {
                    @Override
                    public void run() {
                        String inputUrl = "/sdcard/" + etInput.getText().toString().trim();
                        String outputUrl = "/sdcard/" + etOutput.getText().toString().trim();
                        final StringBuilder result = new StringBuilder();
                        try {
                            Encoder.encode(inputUrl, outputUrl);
                            result.append("成功");
                        } catch (Throwable e) {

                            result.append("失败");
                        } finally {

                            v.post(new Runnable() {
                                @Override
                                public void run() {
                                    v.setEnabled(true);
                                    Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                                }
                            });
                        }

                    }
                }.start();

                v.setEnabled(false);
            }
        });
    }


}
