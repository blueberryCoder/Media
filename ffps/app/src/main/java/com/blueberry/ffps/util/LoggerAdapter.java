package com.blueberry.ffps.util;

/**
 * Created by blueberry on 1/9/2017.
 */

public interface LoggerAdapter {
    void d(String tag, String message);

    void e(String tag, String message);

    void w(String tag, String message);

    void i(String tag, String message);

    void v(String tag, String message);

}
