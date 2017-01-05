
cmake_minimum_required(VERSION 3.4.1)

set(ffmpeg_dir ${CMAKE_SOURCE_DIR}/../../../../ffmpeg)
set(ffmpeg_lib_dir ${ffmpeg_dir}/lib)

#添加头文件目录
include_directories(${ffmpeg_dir}/include)



add_library(decode SHARED
            decode.c)

#link_directories(${ffmpeg_lib_dir}/${ANDROID_ABI})

find_library(log-lib log )

#ffmpeg

add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION
                      ${ffmpeg_lib_dir}/${ANDROID_ABI}/libavcodec-57.so)

add_library(avdevice SHARED IMPORTED)
set_target_properties(avdevice PROPERTIES IMPORTED_LOCATION
                      ${ffmpeg_lib_dir}/${ANDROID_ABI}/libavdevice-57.so)

add_library(avfilter SHARED IMPORTED)
set_target_properties(avfilter PROPERTIES IMPORTED_LOCATION
                      ${ffmpeg_lib_dir}/${ANDROID_ABI}/libavfilter-6.so)

add_library(avformat SHARED IMPORTED)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION
                      ${ffmpeg_lib_dir}/${ANDROID_ABI}/libavformat-57.so)

add_library(avutil SHARED IMPORTED)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION
                      ${ffmpeg_lib_dir}/${ANDROID_ABI}/libavutil-55.so)

add_library(postproc SHARED IMPORTED)
set_target_properties(postproc PROPERTIES IMPORTED_LOCATION
                      ${ffmpeg_lib_dir}/${ANDROID_ABI}/libpostproc-54.so)

add_library(swresample SHARED IMPORTED)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION
                      ${ffmpeg_lib_dir}/${ANDROID_ABI}/libswresample-2.so)

add_library(swscale SHARED IMPORTED)
set_target_properties(swscale PROPERTIES IMPORTED_LOCATION
                      ${ffmpeg_lib_dir}/${ANDROID_ABI}/libswscale-4.so)

target_link_libraries(decode
                       ${log-lib}
                        avcodec
                        avdevice
                        avfilter
                        avformat
                        avutil
                        postproc
                        swresample
                        swscale
                       )
