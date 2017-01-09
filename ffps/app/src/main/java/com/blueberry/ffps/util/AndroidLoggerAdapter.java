package com.blueberry.ffps.util;

import android.util.Log;

/**
 * Created by blueberry on 1/9/2017.
 */

public class AndroidLoggerAdapter implements LoggerAdapter {

    @Override
    public void d(String tag, String message) {
        Log.d(tag,message);
    }

    @Override
    public void e(String tag, String message) {
        Log.e(tag,message);
    }

    @Override
    public void w(String tag, String message) {
        Log.w(tag,message);
    }

    @Override
    public void i(String tag, String message) {
        Log.i(tag,message);
    }

    @Override
    public void v(String tag, String message) {
        Log.v(tag,message);
    }
}
