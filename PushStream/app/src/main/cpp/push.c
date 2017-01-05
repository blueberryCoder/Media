//http://blog.csdn.net/leixiaohua1020/article/details/47056051
// http://blog.csdn.net/leixiaohua1020/article/details/39803457
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

#define TAG "Pusher"
#define null NULL

int stream(const char *inputStr, const char *outputStr);

void callback(void *, int, const char *, va_list);

JNIEXPORT jint JNICALL
Java_com_blueberry_pushstream_PushUtil_stream(JNIEnv *env, jclass jclazz, jstring inputStr_,
                                              jstring outputStr_) {
    const char *inputStr = (*env)->GetStringUTFChars(env, inputStr_, 0);
    const char *outputStr = (*env)->GetStringUTFChars(env, outputStr_, 0);

    int ret = stream(inputStr, outputStr);
    (*env)->ReleaseStringUTFChars(env, inputStr_, inputStr);
    (*env)->ReleaseStringUTFChars(env, outputStr_, outputStr);
    return ret;
}

void callback(void *ptr, int level, const char *fmt, va_list vl) {
    FILE *f = fopen("/storage/emulated/0/log.txt", "a+");
    if (f) {
        vfprintf(f, fmt, vl);
        fflush(f);
        fclose(f);
    }

}


int stream(const char *inputStr, const char *outputStr) {
    LOGD("inputUrl:%s,outputUrl:%s", inputStr, outputStr);

    AVFormatContext *ifmt_ctx = null, *ofmt_ctx = null;
    int ret;
    av_log_set_callback(callback);//设置错误回调;
    av_register_all();
    avformat_network_init();

    ifmt_ctx = avformat_alloc_context();
    if ((ret = avformat_open_input(&ifmt_ctx, inputStr, NULL, NULL)) < 0) {
        LOGE("Could not open input file.");
        goto end;
    }
    if ((ret = avformat_find_stream_info(ifmt_ctx, NULL)) < 0) {
        LOGE("Could not find stream info");
        goto end;
    }

    int videoIndex;
    int i;
    for (i = 0; i < ifmt_ctx->nb_streams; i++) {
        if (ifmt_ctx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            videoIndex = i;
            break;
        }
    }

    //Output
    /**
     * 初始化一个用于输出的AVFormatContext结构体
     */
    avformat_alloc_output_context2(&ofmt_ctx, NULL, "flv", outputStr);

    if (!ofmt_ctx) {
        ret = AVERROR_UNKNOWN;
        LOGE("Could not create output context\n");
        goto end;
    }

    AVOutputFormat *ofmt = ofmt_ctx->oformat;

    for (i = 0; i < ifmt_ctx->nb_streams; i++) {
        //Create output AVStream according to input AVStream
        AVStream *in_stream = ifmt_ctx->streams[i];
        AVStream *out_stream = avformat_new_stream(ofmt_ctx, avcodec_find_decoder(
                in_stream->codecpar->codec_id));
        if (!out_stream) {
            LOGE("Failed allocating output stream\n");
            ret = AVERROR_UNKNOWN;
            goto end;
        }
        ret = avcodec_parameters_copy(out_stream->codecpar, in_stream->codecpar);
        if (ret < 0) {
            LOGE("Failed to copy context from input to output stream codec context\n");
            goto end;
        }
        out_stream->codecpar->codec_tag = 0;
        AVCodecContext avCodecContext;
        if (ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER)
            avcodec_parameters_to_context(&avCodecContext, out_stream->codecpar);
        avCodecContext.flags |= CODEC_FLAG_GLOBAL_HEADER;
    }

    //Open output URL
    if (!(ofmt->flags & AVFMT_NOFILE)) {
        ret = avio_open(&ofmt_ctx->pb, outputStr, AVIO_FLAG_WRITE);
        if (ret < 0) {
            LOGE("Could not open output URL '%s'", outputStr);
            goto end;
        }
    }

    // Write file header
    ret = avformat_write_header(ofmt_ctx, NULL);
    if (ret < 0) {
        LOGE("Error occurred when opening output  URL \n");
        char error[1000] = {0};
        av_strerror(ret, error, 1000);
        LOGE("code:%d,errorCode %s", ret, error);
        goto end;
    }

    int frame_index = 0;
    int64_t start_time = av_gettime();
    AVPacket avPacket;


    /**
     * 没有封装格式的裸流(例如H.264)是不包含PTS、DTS这些参数的，在发送这种数据的时候，需要自己计算并写入AVPacket
     * 的PTS,DTS,duration等参数
     */
    while (1) {

        AVStream *inStream, *outStream;
        // Get an Packet;
        ret = av_read_frame(ifmt_ctx, &avPacket);
        if (ret < 0) {
            break;
        }

        // avPacket.pts AvPacket中的显示时间戳
        // avPacket.dts AvPacket中的解码时间戳
        // AvStream.timebase 时间基准
        // AVRational表示一个分数 num为分子，den为分母
        //
        if (avPacket.pts == AV_NOPTS_VALUE) {
            //Write PTS
            AVRational time_base1 = ifmt_ctx->streams[videoIndex]->time_base;
            int64_t calc_duration =
                    (double) AV_TIME_BASE / av_q2d(ifmt_ctx->streams[videoIndex]->r_frame_rate);

            avPacket.pts = (double) (frame_index * calc_duration) /
                           (double) (av_q2d(time_base1) * AV_TIME_BASE);
            avPacket.dts = avPacket.pts;
            avPacket.duration =
                    (double) calc_duration / (double) (av_q2d(time_base1) * AV_TIME_BASE);
        }

        // Import:Delay
        if (avPacket.stream_index == videoIndex) {
            AVRational time_base = ifmt_ctx->streams[videoIndex]->time_base;
            AVRational time_base_q = {1, AV_TIME_BASE};
            // a*b/c
            int64_t pts_time = av_rescale_q(avPacket.dts, time_base, time_base_q);
            int64_t now_time = av_gettime() - start_time;
            if (pts_time > now_time) {
                av_usleep(pts_time - now_time);
            }
        }

        inStream = ifmt_ctx->streams[avPacket.stream_index];
        outStream = ofmt_ctx->streams[avPacket.stream_index];

        // Copy packet
        // Convert PTS/DTS
        avPacket.pts = av_rescale_q_rnd(avPacket.pts, inStream->time_base, outStream->time_base,
                                        AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX);
        avPacket.dts = av_rescale_q_rnd(avPacket.dts, inStream->time_base, outStream->time_base,
                                        AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX);
        avPacket.duration = av_rescale_q(avPacket.duration, inStream->time_base,
                                         outStream->time_base);

        //Print to Screen
        if (avPacket.stream_index == videoIndex) {
            LOGI("Send %8d video frames to output URL \n", frame_index);
            frame_index++;
        }

        ret = av_interleaved_write_frame(ofmt_ctx, &avPacket);

        if (ret < 0) {
            LOGE("Error muxing packet");
            break;
        }

        av_packet_unref(&avPacket);
    }
    av_write_trailer(ofmt_ctx);

    end:
    avformat_close_input(ifmt_ctx);

    if (ofmt_ctx && !(ofmt->flags & AVFMT_NOFILE))
        avio_close(ofmt_ctx->pb);
    avformat_free_context(ofmt_ctx);
    if (ret < 0 && ret != AVERROR_EOF) {
        LOGE("Error occurred");
        return -1;
    }
    return 0;
}





