#include <jni.h>

#include "rtmp.h"
#include "rtmp_sys.h"
#include "log.h"
#include "android/log.h"
#include "time.h"

#define TAG  "RTMP"
#define LOGD(fmt, ...) \
        __android_log_print(ANDROID_LOG_DEBUG,TAG,fmt,##__VA_ARGS__);

//http://blog.163.com/zhujiatc@126/blog/static/183463820132135378895/
//http://blog.csdn.net/wangxing1231/article/details/44809183
//http://blog.csdn.net/ZhaDeNianQu/article/details/29209839
//http://blog.csdn.net/firehood_/article/details/8783589
//https://my.oschina.net/jerikc/blog/501948
//http://blog.csdn.net/alleraparis/article/details/50131367

#define RTMP_HEAD_SIZE (sizeof(RTMPPacket)+RTMP_MAX_HEADER_SIZE)

#define NAL_SLICE  1
#define NAL_SLICE_DPA  2
#define NAL_SLICE_DPB  3
#define NAL_SLICE_DPC  4
#define NAL_SLICE_IDR  5
#define NAL_SEI  6
#define NAL_SPS  7
#define NAL_PPS  8
#define NAL_AUD  9
#define NAL_FILLER  12

RTMP *rtmp = NULL;
char *rtmpUrl = "rtmp://192.168.155.1:1935/live/test";
RTMPPacket *packet = NULL;
unsigned char *body;
long start_time;

//int send(const char *buf, int buflen, int type, unsigned int timestamp);
int send_video_sps_pps(unsigned char *sps, int sps_len, unsigned char *pps, int pps_len);

int send_rtmp_video(unsigned char *buf, int len);

int stop();

JNIEXPORT jstring JNICALL
Java_com_blueberry_hellortmp_MainActivity_stringFromJNI(JNIEnv *env, jobject instance) {

    int ret = 0;

    RTMP_LogSetLevel(RTMP_LOGDEBUG);
    rtmp = RTMP_Alloc(); //申请rtmp空间
    RTMP_Init(rtmp);
    rtmp->Link.timeout = 5;//单位秒

    RTMP_SetupURL(rtmp, rtmpUrl);
    RTMP_EnableWrite(rtmp);

    //握手
    if ((ret = RTMP_Connect(rtmp, NULL)) <= 0) {
        LOGD("rtmp connet error");
        return (*env)->NewStringUTF(env, "error");
    }

    if ((ret = RTMP_ConnectStream(rtmp, 0)) <= 0) {
        LOGD("rtmp connect stream error");
    }


    return (*env)->NewStringUTF(env, "hello");
}


int stop() {
    RTMP_Close(rtmp);
    RTMP_Free(rtmp);
}


/**
 * H.264 的编码信息帧是发送给 RTMP 服务器称为 AVC sequence header，
 * RTMP 服务器只有收到 AVC sequence header 中的 sps, pps 才能解析后续发送的 H264 帧。
 */
int send_video_sps_pps(unsigned char *sps, int sps_len, unsigned char *pps, int pps_len) {
    int i;
    packet = (RTMPPacket *) malloc(RTMP_HEAD_SIZE + 1024);
    memset(packet, 0, RTMP_HEAD_SIZE);

    packet->m_body = (char *) packet + RTMP_HEAD_SIZE;
    body = (unsigned char *) packet->m_body;
//winsys

    i = 0;
    body[i++] = 0x17; //1:keyframe 7:AVC
    body[i++] = 0x00; // AVC sequence header

    body[i++] = 0x00;
    body[i++] = 0x00;
    body[i++] = 0x00; //fill in 0

/*AVCDecoderConfigurationRecord*/
    body[i++] = 0x01;
    body[i++] = sps[1]; //AVCProfileIndecation
    body[i++] = sps[2]; //profile_compatibilty
    body[i++] = sps[3]; //AVCLevelIndication
    body[i++] = 0xff;//lengthSizeMinusOne

    /*SPS*/
    body[i++] = 0xe1;
    body[i++] = (sps_len >> 8) & 0xff;
    body[i++] = sps_len & 0xff;
    /*sps data*/
    memcpy(&body[i], sps, sps_len);

    i += sps_len;

    /*PPS*/
    body[i++] = 0x01;
    /*sps data length*/
    body[i++] = (pps_len >> 8) & 0xff;
    body[i++] = pps_len & 0xff;
    memcpy(&body[i], pps, pps_len);
    i += pps_len;

    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = i;
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet->m_nInfoField2 = rtmp->m_stream_id;

    /*发送*/
    if (RTMP_IsConnected(rtmp)) {
        RTMP_SendPacket(rtmp, packet, TRUE);
    }

    free(packet);

    start_time = time(0);
    return 0;

}

//sps 与 pps 的帧界定符都是 00 00 00 01，而普通帧可能是 00 00 00 01 也有可能 00 00 01

int send_rtmp_video(unsigned char *buf, int len) {
    int type;
    long timeOffset;

    timeOffset = time(0) - start_time;/*start_time为开始直播的时间戳*/

    /*去掉帧界定符*/
    if (buf[2] == 0x00) {/*00 00 00 01*/
        buf += 4;
        len -= 4;
    } else if (buf[2] == 0x01) {
        buf += 3;
        len - 3;
    }

    type = buf[0] & 0x1f;

    packet = (RTMPPacket *) malloc(RTMP_HEAD_SIZE + len + 9);
    memset(packet, 0, RTMP_HEAD_SIZE);
    packet->m_body = (char *) packet + RTMP_HEAD_SIZE;
    packet->m_nBodySize = len + 9;

    /* send video packet*/
    body = (unsigned char *) packet->m_body;
    memset(body, 0, len + 9);

    /*key frame*/
    body[0] = 0x27;
    if (type == NAL_SLICE_IDR) {
        body[0] = 0x17; //关键帧
    }

    body[1] = 0x01;/*nal unit*/
    body[2] = 0x00;
    body[3] = 0x00;
    body[4] = 0x00;

    body[5] = (len >> 24) & 0xff;
    body[6] = (len >> 16) & 0xff;
    body[7] = (len >> 8) & 0xff;
    body[8] = (len) & 0xff;

    /*copy data*/
    memcpy(&body[9], buf, len);

    packet->m_hasAbsTimestamp = 0;
    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nInfoField2 = rtmp->m_stream_id;
    packet->m_nChannel = 0x04;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nTimeStamp = timeOffset;

    if (RTMP_IsConnected(rtmp)) {
        RTMP_SendPacket(rtmp, packet, TRUE);
    }
    free(packet);
}

