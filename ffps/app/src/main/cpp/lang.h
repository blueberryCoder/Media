//
// Created by Administrator on 1/10/2017.
//

#ifndef FFPS_LANG_H
#define FFPS_LANG_H
#define TAG "PUBLISH"

#include "android/log.h"

#define LOGD(fmt, ...) \
        __android_log_print(ANDROID_LOG_DEBUG,TAG,fmt, ##__VA_ARGS__)

#define LOGI(fmt, ...) \
        __android_log_print(ANDROID_LOG_INFO,TAG,fmt,##__VA_ARGS__)

#define LOGE(fmt, ...) \
        __android_log_print(ANDROID_LOG_ERROR,TAG,fmt,##__VA_ARGS__)

#endif //FFPS_LANG_H
