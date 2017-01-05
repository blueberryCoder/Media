package com.blueberry.helloffmpeg;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {


    private Button btnConfig;
    private Button btnAvCodec;
    private Button btnProtocol;
    private Button btnFilter;
    private Button btnFormat;
    private TextView tvInfo;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        setListeners();
    }

    private void setListeners() {
        btnConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvInfo.setText(FFmpegUtil.configInfo());
            }
        });

        btnFormat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvInfo.setText(FFmpegUtil.formatInfo());
            }
        });

        btnFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvInfo.setText(FFmpegUtil.filterInfo());
            }
        });

        btnProtocol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvInfo.setText(FFmpegUtil.protocolInfo());
            }
        });

        btnAvCodec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvInfo.setText(FFmpegUtil.avCodecInfo());
            }
        });
    }

    private void initView() {
        btnConfig = (Button) findViewById(R.id.btn_config);
        btnAvCodec = (Button) findViewById(R.id.btn_avcodec);
        btnProtocol = (Button) findViewById(R.id.btn_protocol);
        btnFilter = (Button) findViewById(R.id.btn_avfilter);
        btnFormat = (Button) findViewById(R.id.btn_avformat);
        tvInfo = (TextView) findViewById(R.id.tv_info);
    }


}
