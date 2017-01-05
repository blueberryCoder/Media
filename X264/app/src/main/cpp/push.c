#include <jni.h>
#include <stdio.h>
#include <android/log.h>
#include "libavutil/imgutils.h"
#include "libavformat/avformat.h"
#include "libavutil/time.h"


#define LOGI(format, ...) \
    __android_log_print(ANDROID_LOG_INFO, TAG,  format, ##__VA_ARGS__)

#define LOGD(format, ...) \
    __android_log_print(ANDROID_LOG_DEBUG,TAG,format,##__VA_ARGS__)

#define LOGE(format, ...) \
    __android_log_print(ANDROID_LOG_ERROR,TAG,format,##__VA_ARGS__)

#define TAG "Push"

#define FPS 10

AVPacket avPacket;
int size;
AVFrame *avFrame;
AVStream *video_st;
AVCodecContext *avCodecContext;
int fameCount = 0;
AVFormatContext *ofmt_ctx;
int64_t start_time;

static int stop();

static int init(const char *destUrl, int w, int h);

static int push(uint8_t *bytes);

void callback(void *ptr, int level, const char *fmt, va_list vl);

JNIEXPORT jint JNICALL
Java_com_blueberry_x264_Pusher_init(JNIEnv *env, jclass type, jstring destUrl_, jint w, jint h) {
    const char *destUrl = (*env)->GetStringUTFChars(env, destUrl_, 0);
    int ret = init(destUrl, w, h);
    (*env)->ReleaseStringUTFChars(env, destUrl_, destUrl);
    return ret;
}


JNIEXPORT jint JNICALL
Java_com_blueberry_x264_Pusher_push(JNIEnv *env, jclass type, jbyteArray bytes_, jint w, jint h) {
    jbyte *bytes = (*env)->GetByteArrayElements(env, bytes_, NULL);
//    I420: YYYYYYYY UU VV    =>YUV420P
//    YV12: YYYYYYYY VV UU    =>YUV420P
//    NV12: YYYYYYYY UVUV     =>YUV420SP
//    NV21: YYYYYYYY VUVU     =>YUV420SP
    int ret = push((uint8_t *) bytes);
    (*env)->ReleaseByteArrayElements(env, bytes_, bytes, 0);
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_blueberry_x264_Pusher_stop(JNIEnv *env, jclass type) {
    return stop();
}

void callback(void *ptr, int level, const char *fmt, va_list vl) {
    FILE *f = fopen("/storage/emulated/0/avlog.txt", "a+");
    if (f) {
        vfprintf(f, fmt, vl);
        fflush(f);
        fclose(f);
    }

}

static int flush_encoder(AVFormatContext *fmt_ctx, int streamIndex) {
    int ret;
    int got_frame;
    AVPacket enc_pkt;
    if (!(fmt_ctx->streams[streamIndex]->codec->codec->capabilities & CODEC_CAP_DELAY)) {
        return 0;
    }

    while (1) {
        enc_pkt.data = NULL;
        enc_pkt.size = 0;
        av_init_packet(&enc_pkt);
        ret = avcodec_encode_video2(fmt_ctx->streams[streamIndex]->codec, &enc_pkt, NULL,
                                    &got_frame);
        av_frame_free(NULL);
        if (ret < 0) {
            break;
        }
        if (!got_frame) {
            ret = 0;
            return ret;
        }
        LOGI("Flush Encoder : Succeed to encoder 1 frame! \tsize:%5d\n", enc_pkt.size);
        ret = av_write_frame(fmt_ctx, &enc_pkt);
        if (ret < 0) {
            break;
        }

    }
    return ret;
}

static int stop() {
    int ret;
    ret = flush_encoder(ofmt_ctx, 0);
    if (ret < 0) {
        LOGE("Flush Encoder failed");
        goto end;
    }

    av_write_trailer(ofmt_ctx);
    end:

    //Clean
    if (video_st) {
        avcodec_close(video_st->codec);
        av_free(avFrame);
    }
    avio_close(ofmt_ctx->pb);
    avformat_free_context(ofmt_ctx);
    LOGI("stop----------------------");
    return ret;
}

static int push(uint8_t *bytes) {
    start_time = av_gettime();

    int got_picture = 0;
    static int i = 0;

    int j = 0;

    avFrame = av_frame_alloc();
    int picture_size = av_image_get_buffer_size(avCodecContext->pix_fmt, avCodecContext->width,
                                                avCodecContext->height, 1);
    uint8_t buffers[picture_size];

    av_image_fill_arrays(avFrame->data, avFrame->linesize, buffers, avCodecContext->pix_fmt,
                         avCodecContext->width, avCodecContext->height, 1);

    av_new_packet(&avPacket, picture_size);
    size = avCodecContext->width * avCodecContext->height;

    //安卓摄像头数据为NV21格式，此处将其转换为YUV420P格式
    memcpy(avFrame->data[0], bytes, size); //Y
    for (j = 0; j < size / 4; j++) {
        *(avFrame->data[2] + j) = *(bytes + size + j * 2); // V
        *(avFrame->data[1] + j) = *(bytes + size + j * 2 + 1); //U
    }
    int ret = avcodec_encode_video2(avCodecContext, &avPacket, avFrame, &got_picture);
    LOGD("avcodec_encode_video2 spend time %ld", (int) ((av_gettime() - start_time) / 1000));
    if (ret < 0) {
        LOGE("Fail to avcodec_encode ! code:%d", ret);
        return -1;
    }
    if (got_picture == 1) {
        avPacket.pts = i++ * (video_st->time_base.den) / ((video_st->time_base.num) * FPS);
        LOGI("Succeed to encode frame: %5d\tsize:%5d\n", fameCount, avPacket.size);
        avPacket.stream_index = video_st->index;
        avPacket.dts = avPacket.pts;
        avPacket.duration = 1;
        int64_t pts_time = AV_TIME_BASE * av_q2d(video_st->time_base);

        int64_t now_time = av_gettime() - start_time;
        if (pts_time > now_time) {
            LOGD("等待");
            av_usleep(pts_time - now_time);
        }

        av_write_frame(ofmt_ctx, &avPacket);
        LOGD("av_write_frame spend time %ld", (int) (av_gettime() - start_time) / 1000);
        av_free_packet(&avPacket);
        fameCount++;
    } else {
        LOGE("唉~");
    }
    av_frame_free(&avFrame);
}

static int init(const char *destUrl, int w, int h) {
    av_log_set_callback(callback);
    av_register_all();
    LOGD("resister_all");
    AVOutputFormat *fmt;
    int ret;
    LOGI("ouput url: %s", destUrl);
    avformat_alloc_output_context2(&ofmt_ctx, NULL, "flv", destUrl);
    LOGD("allocl ofmt_ctx finished");
    fmt = ofmt_ctx->oformat;
    if ((ret = avio_open(&ofmt_ctx->pb, destUrl, AVIO_FLAG_READ_WRITE)) < 0) {
        LOGE("avio_open error");
        return -1;
    }
    video_st = avformat_new_stream(ofmt_ctx, NULL);
    if (video_st == NULL) {
        ret = -1;
        return -1;
    }
    LOGD("new stream finished");
    avCodecContext = video_st->codec;

//    avCodecContext->codec_id = fmt->video_codec;
    avCodecContext->codec_id = AV_CODEC_ID_H264;
    avCodecContext->codec_type = AVMEDIA_TYPE_VIDEO;
    avCodecContext->pix_fmt = AV_PIX_FMT_YUV420P;
    avCodecContext->width = w;
    avCodecContext->height = h;
    // 目标的码率，即采样码率；显然，采码率越大，视频大小越大
    avCodecContext->bit_rate = 400000; //400,000
    //每250帧插入一个I帧，I帧越少，视频越小
    avCodecContext->gop_size = 250;
    // 帧率的基本单位用分数表示
    avCodecContext->time_base.num = 1;
    avCodecContext->time_base.den = FPS;

    // 最大和最小量化系数
    avCodecContext->qmin = 10;
    avCodecContext->qmax = 51;

    avCodecContext->max_b_frames = 3;

    // Set Option
    AVDictionary *param = 0;

    //H.264
    if (avCodecContext->codec_id == AV_CODEC_ID_H264) {
        av_dict_set(&param, "preset", "veryfast", 0);
        av_dict_set(&param, "tune", "zerolatency", 0);
        LOGI("set h264 param finished");
    }
    //H.265
    if (avCodecContext->codec_id == AV_CODEC_ID_H265) {
        av_dict_set(&param, "preset", "ultrafast", 0);
        av_dict_set(&param, "tune", "zero-latency", 0);
        LOGI("set h265 param");
    }

    AVCodec *avCodec;
    avCodec = avcodec_find_encoder(avCodecContext->codec_id);
    if (NULL == avCodec) {
        LOGE("寻找编码器失败..");
        return -1;
    }

    if ((ret = avcodec_open2(avCodecContext, avCodec, &param)) < 0) {
        LOGE("avcodec_open2 fail!");
        return -1;
    }
    // Write File Header
    avformat_write_header(ofmt_ctx, NULL);

    return ret;
}

