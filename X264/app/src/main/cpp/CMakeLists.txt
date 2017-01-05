
cmake_minimum_required(VERSION 3.4.1)

set(ffmpeg_dir ${CMAKE_SOURCE_DIR}/../../../../ffmpeg)
set(ffmpeg_lib_dir ${ffmpeg_dir}/lib)

#添加头文件目录
include_directories(${ffmpeg_dir}/include)



add_library(encode SHARED
encode.c
push.c
            )

find_library(log-lib log )

#ffmpeg

add_library(ffmpeg SHARED IMPORTED)
set_target_properties(ffmpeg PROPERTIES IMPORTED_LOCATION
                      ${ffmpeg_lib_dir}/${ANDROID_ABI}/libffmpeg.so)

target_link_libraries(encode
                       ${log-lib}
                       ffmpeg
                       )
