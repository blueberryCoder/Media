package com.blueberry.ffps.util;


/**
 * Created by blueberry on 1/9/2017.
 */

public final class Logger {

    private static Printer print = new LoggerPrinter();

    private static boolean enable = true;

    private Logger() {
    }

    public static void enable() {
        enable = true;
    }

    public static void disable() {
        enable = false;
    }

    public static void setLoggerAdapterFactory(LoggerAdapterFactory loggerAdapterFactory) {
        print.setLoggerAdapterFactory(loggerAdapterFactory);
    }

    public static void init(String TAG) {
        print.init(TAG);
    }

    public static void v(String format, Object... params) {
        if (enable) {
            print.v(format, params);
        }
    }

    public static void d(String format, Object... params) {
        if (enable) {
            print.d(format, params);
        }
    }

    public static void i(String format, Object... params) {
        if (enable) {
            print.i(format, params);
        }
    }

    public static void w(String format, Object... params) {
        if (enable) {
            print.w(format, params);
        }
    }

    public static void e(String format, Object... params) {
        if (enable) {
            print.e(format, params);
        }
    }

}
