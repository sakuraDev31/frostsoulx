cmake_minimum_required(VERSION 3.22.1)
project(frostsoulx_immersive_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(FROSTSOULX_IMMERSIVE_ROOT
    ${CMAKE_CURRENT_SOURCE_DIR}/frostsoulx_immersive)
set(FROSTSOULX_IMMERSIVE_INCLUDE_DIR
    ${FROSTSOULX_IMMERSIVE_ROOT}/include)

set(FROSTSOULX_ENGINE_SOURCES
    ${FROSTSOULX_IMMERSIVE_ROOT}/src/immersive_audio_engine.cpp
    ${FROSTSOULX_IMMERSIVE_ROOT}/src/dsp/partitioned_convolver.cpp
    ${FROSTSOULX_IMMERSIVE_ROOT}/src/spatial/ambisonics.cpp
    ${FROSTSOULX_IMMERSIVE_ROOT}/src/spatial/hrtf.cpp
    ${FROSTSOULX_IMMERSIVE_ROOT}/src/spatial/spatial_renderer.cpp
    ${FROSTSOULX_IMMERSIVE_ROOT}/src/spatial/space_profile.cpp
    ${FROSTSOULX_IMMERSIVE_ROOT}/src/spatial/rir_generator.cpp
    ${FROSTSOULX_IMMERSIVE_ROOT}/src/spatial/spatial_source.cpp)

add_library(frostsoulx_immersive_engine STATIC ${FROSTSOULX_ENGINE_SOURCES})
set_target_properties(frostsoulx_immersive_engine PROPERTIES
    POSITION_INDEPENDENT_CODE ON)
target_include_directories(frostsoulx_immersive_engine PUBLIC
    ${FROSTSOULX_IMMERSIVE_INCLUDE_DIR})
target_compile_features(frostsoulx_immersive_engine PUBLIC cxx_std_17)
target_compile_options(frostsoulx_immersive_engine PRIVATE
    -Wall -Wextra -Wpedantic -Wconversion -Wshadow)

if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    set(FROSTSOULX_STEAMAUDIO_ROOT
        ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/steamaudio_sdk)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(FROSTSOULX_STEAMAUDIO_LIB_DIR "android-armv8")
    else()
        set(FROSTSOULX_STEAMAUDIO_LIB_DIR "android-x64")
    endif()
    add_library(phonon SHARED IMPORTED)
    set_target_properties(phonon PROPERTIES
        IMPORTED_LOCATION
            ${FROSTSOULX_STEAMAUDIO_ROOT}/lib/${FROSTSOULX_STEAMAUDIO_LIB_DIR}/libphonon.so
        INTERFACE_INCLUDE_DIRECTORIES
            ${FROSTSOULX_STEAMAUDIO_ROOT}/include)
    target_compile_definitions(frostsoulx_immersive_engine PRIVATE
        FROSTSOULX_STEAM_AUDIO_AVAILABLE=1)
    target_link_libraries(frostsoulx_immersive_engine PRIVATE phonon)
endif()

add_library(frostsoulx_immersive_jni SHARED
    frostsoulx_immersive_jni.cpp)
target_include_directories(frostsoulx_immersive_jni PRIVATE
    ${FROSTSOULX_IMMERSIVE_INCLUDE_DIR})
target_compile_features(frostsoulx_immersive_jni PRIVATE cxx_std_17)
target_compile_options(frostsoulx_immersive_jni PRIVATE
    -Wall -Wextra -Wpedantic -Wconversion -Wshadow)
target_link_libraries(frostsoulx_immersive_jni PRIVATE
    frostsoulx_immersive_engine log)
