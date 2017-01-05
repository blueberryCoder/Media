#include <jni.h>

#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libswscale/swscale.h"
#include "libavutil/avutil.h"
#include "libavutil/imgutils.h"
#include "libavutil/error.h"

#include "android/log.h"

#define LOGI(format, ...) \
      __android_log_print(ANDROID_LOG_INFO, TAG,  format, ##__VA_ARGS__)

#define LOGE(format, ...) \
      __android_log_print(ANDROID_LOG_ERROR, TAG,  format, ##__VA_ARGS__)


#define TAG "Decode"
#ifdef cplusplus
extern "C" {
#endif

//http://blog.csdn.net/leixiaohua1020/article/details/47010637
int decode(const char *, const char *);

void logCallBack(void *, int, const char *, va_list);

/**
 * @param inputUrl_ 输入的url
 * @param outputUrl_ 输出的url
 */
JNIEXPORT jint JNICALL
Java_com_blueberry_vdec_DecodeUtil_decode(JNIEnv *env, jclass type, jstring inputUrl_,
                                          jstring outputUrl_) {
    const char *inputUrl = (*env)->GetStringUTFChars(env, inputUrl_, 0);
    const char *outputUrl = (*env)->GetStringUTFChars(env, outputUrl_, 0);

    int ret = decode2(inputUrl, outputUrl);
    (*env)->ReleaseStringUTFChars(env, inputUrl_, inputUrl);
    (*env)->ReleaseStringUTFChars(env, outputUrl_, outputUrl);

    return ret;
}

void logCallBack(void *ptr, int level, const char *fmt, va_list vl) {
    FILE *f = fopen("/sdcard/av_log.txt", "a+");
    if (f) {
        vfprintf(f, fmt, vl);
        fflush(f);
        fclose(f);
    }
}

/**
 * 解码
 */
int decode(const char *inputStr, const char *outputStr) {
    AVFormatContext *avFormatContext;
    //添加log
    av_log_set_callback(logCallBack);
    // 初始初始化ffmpeg库，如果ffmpeg没配置好这里会出错
    av_register_all();
    //  需要播放网路视频
    avformat_network_init();
    avFormatContext = avformat_alloc_context();
    int errorCode;
    LOGI("输入流:%s", inputStr);
    if ((errorCode = avformat_open_input(&avFormatContext, inputStr, NULL, NULL)) != 0) {
        LOGE("%s ,%d", "打开输入流失败!!! ", errorCode);
        char info[10000] = {0};
        av_strerror(errorCode, info, 1000);
        LOGE("%s", info);
        return -1;
    }

    if (avformat_find_stream_info(avFormatContext, NULL) < 0) {
        LOGE("%s", "找不得流信息 !!!");
        return -1;
    }

    int videoIndex = -1;
    int i;
    for (i = 0; i < videoIndex < avFormatContext->nb_streams; i++) {
        if (avFormatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            videoIndex = i;
            break;
        }
    }

    if (videoIndex == -1) {
        LOGE("%s", "找不到视频流!!!");
        return -1;
    }
    LOGI("%s", "找到了视频流，开始获得编码流");
    //使用avcodec----------------

    AVCodecContext *avCodecContext = avFormatContext->streams[videoIndex]->codec;
    AVCodec *avCodec = avcodec_find_decoder(avCodecContext->codec_id);
    if (avCodec == NULL) {
        LOGE("%s", "找不到codec");
        return -1;
    }
//    AVCodecContext *avCodecContext;

    if (avcodec_open2(avCodecContext, avCodec, NULL) < 0) {
        LOGE("%s", "不能打开codec");
        return -1;
    };

    LOGI("%s", "打开了codec");
    //-------------------

    AVFrame *pFrame = av_frame_alloc();
    AVFrame *pFrameYUV = av_frame_alloc();
    unsigned char *outBuffer = (unsigned char *)
            av_malloc(av_image_get_buffer_size(AV_PIX_FMT_YUV420P,
                                               avCodecContext->width, avCodecContext->height, 1));
    av_image_fill_arrays(pFrameYUV->data, pFrameYUV->linesize, outBuffer,
                         AV_PIX_FMT_YUV420P, avCodecContext->width, avCodecContext->height, 1);

    char info[1000000] = {0};
    sprintf(info, "  [Input     ]%s\n", inputStr);
    sprintf(info, "%s[Output    ]%s\n", info, outputStr);
    sprintf(info, "%s[Format    ]%s\n", info, avFormatContext->iformat->name);
    sprintf(info, "%s[Codec     ]%s\n", info, avCodecContext->codec->name);
    sprintf(info, "%s[Resolution]%dx%d\n", info, avCodecContext->width, avCodecContext->height);

    LOGI("%s", info);

    FILE *fOut = fopen(outputStr, "wb+");
    if (fOut == NULL) {
        LOGE("%s", "不能够打开输出文件");
        return -1;
    }

    int frameCount = 0;
    long timerStart = clock();
    AVPacket *avPacket = (AVPacket *) av_malloc(sizeof(AVPacket));;
    int ret;
    int size;
    struct SwsContext *swsContext;
    int got_picture;

    swsContext = sws_getContext(avCodecContext->width, avCodecContext->height,
                                avCodecContext->pix_fmt, avCodecContext->width,
                                avCodecContext->height, AV_PIX_FMT_YUV420P,
                                SWS_BICUBIC, NULL, NULL, NULL);

    while (av_read_frame(avFormatContext, avPacket) >= 0) {
        if (avPacket->stream_index == videoIndex) {
            ret = avcodec_decode_video2(avCodecContext, pFrame, &got_picture, avPacket);
            if (ret < 0) {
                LOGE("Decode Error.\n");
                return -1;
            }
            if (got_picture) {
                sws_scale(swsContext, (const uint8_t *const *) pFrame->data, pFrame->linesize,
                          0, avCodecContext->height,
                          pFrameYUV->data, pFrameYUV->linesize);
                size = avCodecContext->width * avCodecContext->height;
                fwrite(pFrameYUV->data[0], 1, size, fOut);    //Y
                fwrite(pFrameYUV->data[1], 1, size / 4, fOut);  //U
                fwrite(pFrameYUV->data[2], 1, size / 4, fOut);  //V
                //Output info
                char pictype_str[10] = {0};
                switch (pFrame->pict_type) {
                    case AV_PICTURE_TYPE_I:
                        sprintf(pictype_str, "I");
                        break;
                    case AV_PICTURE_TYPE_P:
                        sprintf(pictype_str, "P");
                        break;
                    case AV_PICTURE_TYPE_B:
                        sprintf(pictype_str, "B");
                        break;
                    default:
                        sprintf(pictype_str, "Other");
                        break;
                }
                LOGI("Frame Index: %5d. Type:%s", frameCount, pictype_str);
                frameCount++;
            }
        }
        av_free_packet(avPacket);
    }

    LOGI("%s", "开始刷帧");
    //flush decoder
    //FIX: Flush Frames remained in Codec
    while (1) {
        ret = avcodec_decode_video2(avCodecContext, pFrame, &got_picture, avPacket);
        if (ret < 0)
            break;
        if (!got_picture)
            break;
        sws_scale(swsContext, (const uint8_t *const *) pFrame->data, pFrame->linesize, 0,
                  avCodecContext->height,
                  pFrameYUV->data, pFrameYUV->linesize);
        int y_size = avCodecContext->width * avCodecContext->height;
        fwrite(pFrameYUV->data[0], 1, y_size, fOut);    //Y
        fwrite(pFrameYUV->data[1], 1, y_size / 4, fOut);  //U
        fwrite(pFrameYUV->data[2], 1, y_size / 4, fOut);  //V
        //Output info
        char pictype_str[10] = {0};
        switch (pFrame->pict_type) {
            case AV_PICTURE_TYPE_I:
                sprintf(pictype_str, "I");
                break;
            case AV_PICTURE_TYPE_P:
                sprintf(pictype_str, "P");
                break;
            case AV_PICTURE_TYPE_B:
                sprintf(pictype_str, "B");
                break;
            default:
                sprintf(pictype_str, "Other");
                break;
        }
        LOGI("Frame Index: %5d. Type:%s", frameCount, pictype_str);
        frameCount++;
    }

    long timeFinish = clock();
    long timeDuration = timeFinish - timerStart;
    sprintf(info, "%s[Time      ]%dms\n", info, timeDuration);
    sprintf(info, "%s[Count     ]%d\n", info, frameCount);
    LOGI("%s", info);
    sws_freeContext(swsContext);
    fclose(fOut);
    av_frame_free(&pFrame);
    av_frame_free(&pFrameYUV);
    avcodec_close(avCodecContext);
    avformat_close_input(&avFormatContext);
    //142133515ms
    return 0;
}

/**
 * 解码
 */
int decode2(const char *inputStr, const char *outputStr) {
    AVFormatContext *avFormatContext;
    //添加log
    av_log_set_callback(logCallBack);
    // 初始初始化ffmpeg库，如果ffmpeg没配置好这里会出错
    av_register_all();
    //  需要播放网路视频
    avformat_network_init();
    avFormatContext = avformat_alloc_context();
    int errorCode;
    LOGI("输入流:%s", inputStr);
    if ((errorCode = avformat_open_input(&avFormatContext, inputStr, NULL, NULL)) != 0) {
        LOGE("%s ,%d", "打开输入流失败!!! ", errorCode);
        char info[10000] = {0};
        av_strerror(errorCode, info, 1000);
        LOGE("%s", info);
        return -1;
    }

    if (avformat_find_stream_info(avFormatContext, NULL) < 0) {
        LOGE("%s", "找不得流信息 !!!");
        return -1;
    }

    int videoIndex = -1;
    int i;
    for (i = 0; i < videoIndex < avFormatContext->nb_streams; i++) {
        if (avFormatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            videoIndex = i;
            break;
        }
    }

    if (videoIndex == -1) {
        LOGE("%s", "找不到视频流!!!");
        return -1;
    }
    LOGI("%s", "找到了视频流，开始获得编码流");
    //使用avcodec----------------

    AVCodecContext *avCodecContext = avcodec_alloc_context3(NULL);
    avcodec_parameters_to_context(avCodecContext, avFormatContext->streams[videoIndex]->codecpar);
    AVCodec *avCodec = avcodec_find_decoder(avCodecContext->codec_id);
    if (avCodec == NULL) {
        LOGE("%s", "找不到codec");
        return -1;
    }
    if (avcodec_open2(avCodecContext, avCodec, NULL) < 0) {
        LOGE("%s", "不能打开codec");
        return -1;
    };

    LOGI("%s", "打开了codec");
    //-------------------

    AVFrame *pFrame = av_frame_alloc();
    AVFrame *pFrameYUV = av_frame_alloc();
    unsigned char *outBuffer = (unsigned char *)
            av_malloc(av_image_get_buffer_size(AV_PIX_FMT_YUV420P,
                                               avCodecContext->width, avCodecContext->height, 1));
    av_image_fill_arrays(pFrameYUV->data, pFrameYUV->linesize, outBuffer,
                         AV_PIX_FMT_YUV420P, avCodecContext->width, avCodecContext->height, 1);

    char info[40000] = {0};
    sprintf(info, "  [Input     ]%s\n", inputStr);
    sprintf(info, "%s[Output    ]%s\n", info, outputStr);
    sprintf(info, "%s[Format    ]%s\n", info, avFormatContext->iformat->name);
    sprintf(info, "%s[Codec     ]%s\n", info, avCodecContext->codec->name);
    sprintf(info, "%s[Resolution]%dx%d\n", info, avCodecContext->width, avCodecContext->height);

    LOGI("%s", info);

    FILE *fOut = fopen(outputStr, "wb+");
    if (fOut == NULL) {
        LOGE("%s", "不能够打开输出文件");
        return -1;
    }

    int frameCount = 0;
    long timerStart = clock();
    int size, ret;
    struct SwsContext *swsContext;
    swsContext = sws_getContext(avCodecContext->width, avCodecContext->height,
                                avCodecContext->pix_fmt, avCodecContext->width,
                                avCodecContext->height, AV_PIX_FMT_YUV420P,
                                SWS_BICUBIC, NULL, NULL, NULL);
    AVPacket *avPacket = (AVPacket *) av_malloc(sizeof(AVPacket));
    LOGI("%s", "开始读帧");
    while (av_read_frame(avFormatContext, avPacket) >= 0) {
        if (avPacket->stream_index == videoIndex) {
            if (!avcodec_send_packet(avCodecContext, avPacket)) {
                if (!avcodec_receive_frame(avCodecContext, pFrame)) {
                    sws_scale(swsContext, (const uint8_t *const *) pFrame->data, pFrame->linesize,
                              0, avCodecContext->height,
                              pFrameYUV->data, pFrameYUV->linesize);
                    size = avCodecContext->width * avCodecContext->height;
                    fwrite(pFrameYUV->data[0], 1, size, fOut);    //Y
                    fwrite(pFrameYUV->data[1], 1, size / 4, fOut);  //U
                    fwrite(pFrameYUV->data[2], 1, size / 4, fOut);  //V
                    //Output info
                    char pictype_str[10] = {0};
                    switch (pFrame->pict_type) {
                        case AV_PICTURE_TYPE_I:
                            sprintf(pictype_str, "I");
                            break;
                        case AV_PICTURE_TYPE_P:
                            sprintf(pictype_str, "P");
                            break;
                        case AV_PICTURE_TYPE_B:
                            sprintf(pictype_str, "B");
                            break;
                        default:
                            sprintf(pictype_str, "Other");
                            break;
                    }
                    LOGI("Frame Index: %5d. Type:%s", frameCount, pictype_str);
                    frameCount++;
                }
                av_frame_unref(pFrame);
            }
        }
        av_packet_unref(avPacket);
    }


    LOGI("%s", "开始刷帧");
    //flush decoder
    //FIX: Flush Frames remained in Codec
    while (1) {
        if (!avcodec_send_packet(avCodecContext, avPacket)) {
            if (!avcodec_receive_frame(avCodecContext, pFrame)) {
                sws_scale(swsContext, (const uint8_t *const *) pFrame->data, pFrame->linesize,
                          0, avCodecContext->height,
                          pFrameYUV->data, pFrameYUV->linesize);
                size = avCodecContext->width * avCodecContext->height;
                fwrite(pFrameYUV->data[0], 1, size, fOut);    //Y
                fwrite(pFrameYUV->data[1], 1, size / 4, fOut);  //U
                fwrite(pFrameYUV->data[2], 1, size / 4, fOut);  //V
                //Output info
                char pictype_str[10] = {0};
                switch (pFrame->pict_type) {
                    case AV_PICTURE_TYPE_I:
                        sprintf(pictype_str, "I");
                        break;
                    case AV_PICTURE_TYPE_P:
                        sprintf(pictype_str, "P");
                        break;
                    case AV_PICTURE_TYPE_B:
                        sprintf(pictype_str, "B");
                        break;
                    default:
                        sprintf(pictype_str, "Other");
                        break;
                }
                LOGI("Frame Index: %5d. Type:%s", frameCount, pictype_str);
                frameCount++;
            } else {
                break;
            }
            av_frame_unref(pFrame);
        } else {
            break;
        }
    }

    long timeFinish = clock();
    long timeDuration = timeFinish - timerStart;
    sprintf(info,
            "%s[Time      ]%dms\n", info, timeDuration);
    sprintf(info,
            "%s[Count     ]%d\n", info, frameCount);
    LOGI("%s", info);
    sws_freeContext(swsContext);
    fclose(fOut);
    av_frame_free(&pFrame);
    av_frame_free(&pFrameYUV);
    avcodec_close(avCodecContext);
    avformat_close_input(&avFormatContext);
    return 0;
}

#ifdef cplusplus
}
#endif
