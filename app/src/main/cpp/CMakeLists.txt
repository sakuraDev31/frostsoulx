cmake_minimum_required(VERSION 3.22.1)
project(frostsoulx_dsp_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(frostsoulx_dsp STATIC
    frostsoulx_dsp/src/sound_field.cpp
    frostsoulx_dsp/src/hrtf_binaural.cpp
    frostsoulx_dsp/src/early_reflections.cpp
    frostsoulx_dsp/src/StereoSurroundProcessor.cpp
)
target_include_directories(frostsoulx_dsp PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/frostsoulx_dsp/include
)
target_compile_features(frostsoulx_dsp PUBLIC cxx_std_17)
target_compile_options(frostsoulx_dsp PRIVATE -Wall -Wextra -Wpedantic -Wconversion -Wshadow)

add_library(frostsoulx_dsp_jni SHARED frostsoulx_dsp_jni.cpp)
target_link_libraries(frostsoulx_dsp_jni PRIVATE frostsoulx_dsp log)
target_include_directories(frostsoulx_dsp_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/frostsoulx_dsp/include
)
target_compile_features(frostsoulx_dsp_jni PRIVATE cxx_std_17)
target_compile_options(frostsoulx_dsp_jni PRIVATE -Wall -Wextra -Wpedantic -Wconversion -Wshadow)
