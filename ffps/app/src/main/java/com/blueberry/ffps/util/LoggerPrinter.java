package com.blueberry.ffps.util;

/**
 * Created by blueberry on 1/9/2017.
 */

final class LoggerPrinter implements Printer {

    public static String TAG = "default";
    private static int METHOD_OFFSET = 5;

    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    private LoggerAdapterFactory factory = new LoggerAdapterFactory() {
        @Override
        public LoggerAdapter create() {
            return new AndroidLoggerAdapter();
        }
    };

    private LoggerAdapter getLogger() {
        return factory.create();
    }

    LoggerPrinter() {
    }

    public void init(String tag) {
        TAG = tag;
    }

    @Override
    public void d(String message, Object... args) {
        String header = logHeader(DEBUG);
        logAccordingToLevel(DEBUG, header + String.format(message, args));
    }


    @Override
    public void e(String message, Object... args) {
        String header = logHeader(ERROR);
        logAccordingToLevel(ERROR, header + String.format(message, args));
    }

    @Override
    public void w(String message, Object... args) {
        String header = logHeader(WARN);
        logAccordingToLevel(WARN, header + String.format(message, args));
    }

    @Override
    public void i(String message, Object... args) {
        String header = logHeader(INFO);
        logAccordingToLevel(INFO, header + String.format(message, args));
    }

    @Override
    public void v(String message, Object... args) {
        String header = logHeader(VERBOSE);
        logAccordingToLevel(VERBOSE, header + String.format(message, args));
    }

    public void setLoggerAdapterFactory(LoggerAdapterFactory loggerAdapterFactory) {
        this.factory = loggerAdapterFactory;
    }

    private String logHeader(int level) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement stackTraceElement = stackTrace[METHOD_OFFSET];
        StringBuilder sb = new StringBuilder();
        sb.append("Class:")
                .append(getClassSimpleName(stackTraceElement))
                .append(";Method:")
                .append(getMethodName(stackTraceElement))
                .append(";Line:")
                .append(getLineNumber(stackTraceElement))
                .append(";\n");
        return sb.toString();
    }

    private void logAccordingToLevel(int level, String str) {
        switch (level) {
            case VERBOSE:
                getLogger().v(TAG, str);
                break;
            case DEBUG:
                getLogger().d(TAG, str);
                break;
            case INFO:
                getLogger().i(TAG, str);
                break;
            case WARN:
                getLogger().w(TAG, str);
                break;
            case ERROR:
                getLogger().e(TAG, str);
                break;
        }
    }

    private String getLineNumber(StackTraceElement stackTraceElement) {
        int lineNumber = stackTraceElement.getLineNumber();
        return String.valueOf(lineNumber);
    }

    private String getMethodName(StackTraceElement stackTraceElement) {
        String methodName = stackTraceElement.getMethodName();
        return methodName;
    }

    private String getClassSimpleName(StackTraceElement stackTraceElement) {
        String sb = stackTraceElement.getClassName();
        return sb;
    }

}
