package com.blueberry.pushstream;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * 推流
 */
public class MainActivity extends AppCompatActivity {

    private EditText etInputUrl, etOutputUrl;
    private Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etInputUrl = (EditText) findViewById(R.id.et_input);
        etOutputUrl = (EditText) findViewById(R.id.et_output);

        btn = (Button) findViewById(R.id.btn);

        etInputUrl.setText("test.flv");
        etOutputUrl.setText("rtmp://192.168.155.1:1935/live/test");

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                new Thread() {
                    @Override
                    public void run() {
                        String inputUrl =
                                "/sdcard/" + etInputUrl.getText().toString().trim();
                        String outputUrl = etOutputUrl.getText().toString().trim();
                        try {
                            PushUtil.stream(inputUrl, outputUrl);
                        } finally {
                            v.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "ok!", Toast.LENGTH_LONG).show();
                                    v.setClickable(true);
                                }
                            });
                        }
                    }
                }.start();

                v.setClickable(false);
            }
        });
    }
}
