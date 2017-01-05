package com.blueberry.vdec;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private EditText etInput;
    private EditText etOuput;
    private Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etInput = (EditText) findViewById(R.id.et_input);
        etOuput = (EditText) findViewById(R.id.et_output);
        btn = (Button) findViewById(R.id.btn);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String inputUrl = Environment.getExternalStorageDirectory()
                        +"/"+etInput.getText().toString().trim();
                final String outputUrl = Environment.getExternalStorageDirectory()
                        +"/"+etOuput.getText().toString().trim();
                Log.i(TAG, "inputUrl:"+inputUrl
                +"\noutputUrl:"+outputUrl);
                new AsyncTask<Void,Void,Void>(){
                    @Override
                    protected Void doInBackground(Void... params) {
                        DecodeUtil.decode(inputUrl,outputUrl);

                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        Toast.makeText(MainActivity.this,"解码成功",Toast.LENGTH_LONG)
                                .show();
                    }
                }.execute();
            }
        });


    }


}
