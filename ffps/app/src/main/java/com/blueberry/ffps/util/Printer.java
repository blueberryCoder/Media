package com.blueberry.ffps.util;

/**
 * Created by blueberry on 1/9/2017.
 */

public interface Printer {

    void init(String tag);

    void setLoggerAdapterFactory(LoggerAdapterFactory factory);

    void d(String message, Object... args);

    void e(String message, Object... args);

    void w(String message, Object... args);

    void i(String message, Object... args);

    void v(String message, Object... args);
}
