#include <jni.h>
#include <stdio.h>
#include <android/log.h>
#include "libavutil/imgutils.h"
#include "libavformat/avformat.h"
#include "libavutil/time.h"
//http://blog.csdn.net/leixiaohua1020/article/details/25430425

#define LOGI(format, ...) \
    __android_log_print(ANDROID_LOG_INFO, TAG,  format, ##__VA_ARGS__)

#define LOGD(format, ...) \
    __android_log_print(ANDROID_LOG_DEBUG,TAG,format,##__VA_ARGS__)

#define LOGE(format, ...) \
    __android_log_print(ANDROID_LOG_ERROR,TAG,format,##__VA_ARGS__)

#define TAG "Encoder"

static int encode(const char *, const char *, int, int);

void callback(void *, int, const char *, va_list);

static int flush_encoder(AVFormatContext *pContext, int s);

JNIEXPORT jint JNICALL
Java_com_blueberry_x264_Encoder_encode(JNIEnv *env, jclass type, jstring inputUrl_,
                                       jstring outputUrl_) {
    const char *inputUrl = (*env)->GetStringUTFChars(env, inputUrl_, 0);
    const char *outputUrl = (*env)->GetStringUTFChars(env, outputUrl_, 0);

    // TODO 1280x720
    int ret = 0;
    ret = encode(inputUrl, outputUrl, 1280, 720);

    (*env)->ReleaseStringUTFChars(env, inputUrl_, inputUrl);
    (*env)->ReleaseStringUTFChars(env, outputUrl_, outputUrl);
    return ret;
}



static int encode(const char *inputUrl, const char *outputUrl, int w, int h) {
    int ret;
    LOGI("inputUrl:%s\noutputUrl:%s", inputUrl, outputUrl);
    // input YUV
    FILE *in_file = fopen(inputUrl, "rb");
    if (!in_file) {
        LOGE("open input file fail");
        goto end;
    }
    int framenum = 1000000;

    av_log_set_callback(callback);
    av_register_all();

    AVFormatContext *avFormatContext;
    AVOutputFormat *fmt;

    //Method 1
//    avFormatContext = avformat_alloc_context();
//    // Guess fomat
//    AVOutputFormat *fmt;
//    fmt = av_guess_format(NULL,outputUrl,NULL);
//    avFormatContext->oformat = fmt;

    // Method 2
    if ((ret = avformat_alloc_output_context2(&avFormatContext, NULL, NULL, outputUrl)) < 0) {
        LOGE("Alloc ouput context fail errorcode=%d", ret);
        char info[1000] = {0};
        av_strerror(ret, info, 1000);
        LOGE("info:%s", info);
        goto end;
    }
    fmt = avFormatContext->oformat;
    LOGI("format:%x", fmt);

    // Open output URl
    if ((ret = avio_open(&avFormatContext->pb, outputUrl, AVIO_FLAG_READ_WRITE)) < 0) {
        LOGE("fail to open output file");
        goto end;
    }

    AVStream *video_st;
    video_st = avformat_new_stream(avFormatContext, 0);
    if (video_st == NULL) {
        ret = -1;
        goto end;
    }

    AVCodecContext *avCodecContext;

    avCodecContext = video_st->codec;
    LOGI("AvCodecContext:%x", avCodecContext);
    LOGI("get avCodecContext success");
    avCodecContext->codec_id = fmt->video_codec;
    avCodecContext->codec_type = AVMEDIA_TYPE_VIDEO;
    avCodecContext->pix_fmt = AV_PIX_FMT_YUV420P;
    avCodecContext->width = w;
    avCodecContext->height = h;
    avCodecContext->bit_rate = 400000;
    avCodecContext->gop_size = 250;
    avCodecContext->time_base.num = 1;
    avCodecContext->time_base.den = 25;

    // H264
    avCodecContext->qmin = 10;
    avCodecContext->qmax = 51;

    avCodecContext->max_b_frames = 3;

    // Set Option
    AVDictionary *param = 0;

    //H.264
    if (avCodecContext->codec_id == AV_CODEC_ID_H264) {
        av_dict_set(&param, "preset", "slow", 0);
        av_dict_set(&param, "tune", "zerolatency", 0);
        LOGI("set h264 param finished");
    }
    //H.265
    if (avCodecContext->codec_id == AV_CODEC_ID_H265) {
        av_dict_set(&param, "preset", "ultrafast", 0);
        av_dict_set(&param, "tune", "zero-latency", 0);
        LOGI("set h265 param");
    }
    LOGI("set param finish");
    // show some information
    av_dump_format(avFormatContext, 0, outputUrl, 1);

    LOGI("find decocder");
    AVCodec *avCodec;
    LOGI("codecId=%x", avCodecContext->codec_id);
    avCodec = avcodec_find_encoder(avCodecContext->codec_id);
    LOGI("codc:%x", avCodec);

    if (!avCodec) {
        LOGE("Could not find encoder!");
        ret = -1;
        goto end;
    }
    if ((ret = avcodec_open2(avCodecContext, avCodec, &param)) < 0) {
        LOGE("Failed to open encoder!");
        goto end;
    }

    AVFrame *pFrame = av_frame_alloc();
    int picture_size;
    picture_size = av_image_get_buffer_size(avCodecContext->pix_fmt, avCodecContext->width,
                                            avCodecContext->height, 1);
    uint8_t *picture_buf;
    picture_buf = av_malloc(picture_size);
    av_image_fill_arrays(pFrame->data, pFrame->linesize, picture_buf, avCodecContext->pix_fmt,
                         avCodecContext->width,
                         avCodecContext->height, 1);
    // Write File Header
    avformat_write_header(avFormatContext, NULL);
    AVPacket avPacket;
    av_new_packet(&avPacket, picture_size);
    int frameCount = 0;
    int size = avCodecContext->width * avCodecContext->height;
    int i;
    int got_picture = 0;
    for (i = 0; i < framenum; i++) {
        //Read raw YUV data

        if (fread(picture_buf, 1, size * 3 / 2, in_file) <= 0) {
            LOGE("Failed to read raw data !");
            ret = -1;
            goto end;
        } else if (feof(in_file)) {
            break;
        }

        pFrame->data[0] = picture_buf;                  // Y   1
        pFrame->data[1] = picture_buf + size;           // U   1/4
        pFrame->data[2] = picture_buf + size * 5 / 4;   // V   1/4


        // PTS
        // PFrame->pts =i;
        pFrame->pts = i * (video_st->time_base.den) / ((video_st->time_base.num) * 25);

//        Encode
        int ret = avcodec_encode_video2(avCodecContext, &avPacket, pFrame, &got_picture);


        if (ret < 0) {
            LOGE("Fail to avcodec_encode ! code:%d", ret);
            goto end;
        }
        if (got_picture == 1) {
            LOGI("Succeed to encode frame: %5d\tsize:%5d\n", frameCount, avPacket.size);
            frameCount++;
            avPacket.stream_index = video_st->index;
            ret = av_write_frame(avFormatContext, &avPacket);
            av_free_packet(&avPacket);
        }
    }

    // Flush Encoder

    ret = flush_encoder(avFormatContext, 0);
    if (ret < 0) {
        LOGE("Flush Encoder failed");
        goto end;
    }

    av_write_trailer(avFormatContext);

    end:

    //Clean
    if (video_st) {
        avcodec_close(video_st->codec);
        av_free(pFrame);
        av_free(picture_buf);
    }
    avio_close(avFormatContext->pb);
    avformat_free_context(avFormatContext);
    fclose(in_file);
    return 0;
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
