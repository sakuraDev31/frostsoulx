cmake_minimum_required(VERSION 3.22.1)
project(frostsoulx_surround_jni LANGUAGES CXX)

add_library(frostsoulx_stereo_surround STATIC
    frostsoulx_surround/src/StereoSurroundProcessor.cpp
)
target_include_directories(frostsoulx_stereo_surround PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/frostsoulx_surround/include
)
target_compile_features(frostsoulx_stereo_surround PUBLIC cxx_std_17)
target_compile_options(frostsoulx_stereo_surround PRIVATE -Wall -Wextra -Wpedantic -Wconversion -Wshadow)

add_library(frostsoulx_surround_jni SHARED frostsoulx_surround_jni.cpp)
target_include_directories(frostsoulx_surround_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/frostsoulx_surround/include
)
target_compile_features(frostsoulx_surround_jni PRIVATE cxx_std_17)
target_compile_options(frostsoulx_surround_jni PRIVATE -Wall -Wextra -Wpedantic -Wconversion -Wshadow)
target_link_libraries(frostsoulx_surround_jni PRIVATE frostsoulx_stereo_surround log)
