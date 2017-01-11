#include "publish.h"
#include "lang.h"

#ifdef __cplusplus
extern "C"
{
#endif

#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavutil/imgutils.h"
#include "libavutil/time.h"
#ifdef __cplusplus
}
#endif

#define FPS 10

AVFormatContext *ofmt_ctx;
AVOutputFormat *ofmt;
AVStream *video_st;
AVCodecContext *avCodecContext;

static int flush_encoder(AVFormatContext *fmt_ctx, int streamIndex);

JNIEXPORT jint JNICALL
Java_com_blueberry_ffps_Publisher_push(JNIEnv *env, jclass type, jbyteArray data_, jint w,
                                       jint h) {

    jbyte *byte = env->GetByteArrayElements(data_, NULL);
    int ret = push((BYTE *) byte, w, h);
    env->ReleaseByteArrayElements(data_, byte, 0);
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_blueberry_ffps_Publisher_init(JNIEnv *env, jclass type, jstring outUrl_, jint w,
                                       jint h) {
    const char *url = env->GetStringUTFChars(outUrl_, NULL);
    int ret = init(url, w, h);
    env->ReleaseStringUTFChars(outUrl_, url);

    return ret;
}

JNIEXPORT jint JNICALL
Java_com_blueberry_ffps_Publisher_stop(JNIEnv *env, jclass type) {
    int ret = stop();
    return ret;
}

void callback(void *pt, int level, const char *fmt, va_list vl) {
    FILE *file = fopen("/sdcard/av_log.txt", "r+");
    if (file) {
        fprintf(file, fmt, vl);
        fflush(file);
        fclose(file);
    }


}

/**
 * 初始化
 */
int init(const char *url, int w, int h) {
    LOGD("输出url:%s", url);
    av_log_set_callback(callback);
    av_register_all();
    avformat_network_init();

    int ret;
    ofmt_ctx = avformat_alloc_context();
    if ((ret = avformat_alloc_output_context2(&ofmt_ctx, 0, "flv", url)) < 0) {
        LOGE("alloc output context failed");
        char info[1000] = {0};
        av_strerror(ret, info, 1000);
        LOGE("error code %d,info: %s", ret, info);
        return ret;
    }
    ofmt = ofmt_ctx->oformat;

    // open
    if ((ret = avio_open(&ofmt_ctx->pb, url, AVIO_FLAG_READ_WRITE)) < 0) {
        LOGE("open url failed");
        return ret;
    }
    LOGD("open url success");

    // new stream
    video_st = avformat_new_stream(ofmt_ctx, NULL);
    if (video_st == NULL) {
        LOGE("new stream failed");
        return -1;
    }

    LOGD("new stream finished");

    avCodecContext = video_st->codec;

    avCodecContext->codec_id = AV_CODEC_ID_H264;
    avCodecContext->codec_type = AVMEDIA_TYPE_VIDEO;
    avCodecContext->pix_fmt = AV_PIX_FMT_YUV420P;
    avCodecContext->width = w;
    avCodecContext->height = h;
    avCodecContext->bit_rate = 400000;
    avCodecContext->gop_size = 250;
    avCodecContext->time_base.num = 1;
    avCodecContext->time_base.den = FPS;

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
        LOGE("寻找编码器失败");
        return -1;
    }
    LOGD("find encoder finished");

    if ((ret = avcodec_open2(avCodecContext, avCodec, &param))) {
        LOGE("avcodec_open failed");
        return -1;
    }

    LOGD("avcodec open finished");


    // Write file header;
    avformat_write_header(ofmt_ctx, NULL);

    LOGD("avcodec write header success");

    return 0;
}

int64_t start_time;
AVFrame *avFrame;
AVPacket avPacket;
int size, frameCount = 0;
int i = 0;

int push(BYTE *data, int w, int h) {
    start_time = av_gettime();
    int got_picture = 0;
    int j = 0;
    avFrame = av_frame_alloc();
    int picure_size = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, w, h, 1);
    BYTE buffer[picure_size];
    av_image_fill_arrays(avFrame->data, avFrame->linesize, buffer, AV_PIX_FMT_YUV420P, w, h, 1);

    av_new_packet(&avPacket, picure_size);
    size = w * h;

    // Nv21 To I420
    memcpy(avFrame->data[0], data, size);//Y
    for (j = 0; j < size / 4; ++j) {
        *(avFrame->data[2] + j) = *(data + size + j * 2);//V
        *(avFrame->data[0] + j) = *(data + size + j * 2 + 1);//U
    }

    int ret = avcodec_encode_video2(avCodecContext, &avPacket, avFrame, &got_picture);
    LOGD("avcodec_encode_video2 spend time %ld", (int) ((av_gettime() - start_time) / 1000));

    if (ret < 0) {
        LOGE("Failed to encode! code:%d", ret);
        return -1;
    }

    if (got_picture == 1) {
        avPacket.pts = i++ * (video_st->time_base.den) / ((video_st->time_base.num) * FPS);
        LOGI("Succeed to encode frame: %5d\tsize:%5d\n", frameCount, avPacket.size);

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
        av_free_packet(&avPacket);
        frameCount++;
    }
    av_frame_free(&avFrame);
    return 0;
}

int stop() {
    i = 0;
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
