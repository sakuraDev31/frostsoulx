cmake_minimum_required(VERSION 3.22.1)
project(frostsoulx_immersive_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(FROSTSOULX_IMMERSIVE_INCLUDE_DIR
    ${CMAKE_CURRENT_SOURCE_DIR}/frostsoulx_immersive/include)

add_library(frostsoulx_immersive_engine STATIC
    frostsoulx_immersive/src/ImmersiveAudioEngine.cpp)
target_include_directories(frostsoulx_immersive_engine PUBLIC
    ${FROSTSOULX_IMMERSIVE_INCLUDE_DIR})
target_compile_features(frostsoulx_immersive_engine PUBLIC cxx_std_17)
target_compile_options(frostsoulx_immersive_engine PRIVATE
    -Wall -Wextra -Wpedantic -Wconversion -Wshadow)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(FROSTSOULX_STEAMAUDIO_ROOT
        ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/steamaudio_sdk)
    add_library(phonon SHARED IMPORTED)
    set_target_properties(phonon PROPERTIES
        IMPORTED_LOCATION
            ${FROSTSOULX_STEAMAUDIO_ROOT}/lib/android-armv8/libphonon.so
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
