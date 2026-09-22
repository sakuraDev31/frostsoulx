cmake_minimum_required(VERSION 3.22)
project(frostsoulx_closed_car_host LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(STEAMAUDIO_SDK_ROOT "" CACHE PATH "Steam Audio SDK root containing include/ and lib/linux-x64/")
if(NOT STEAMAUDIO_SDK_ROOT)
    message(FATAL_ERROR "Set -DSTEAMAUDIO_SDK_ROOT=/path/to/steamaudio")
endif()

set(STEAMAUDIO_INCLUDE_DIR "${STEAMAUDIO_SDK_ROOT}/include")
set(STEAMAUDIO_LIBRARY "${STEAMAUDIO_SDK_ROOT}/lib/linux-x64/libphonon.so")
if(NOT EXISTS "${STEAMAUDIO_INCLUDE_DIR}/phonon.h")
    message(FATAL_ERROR "Steam Audio header not found: ${STEAMAUDIO_INCLUDE_DIR}/phonon.h")
endif()
if(NOT EXISTS "${STEAMAUDIO_LIBRARY}")
    message(FATAL_ERROR "Linux Steam Audio library not found: ${STEAMAUDIO_LIBRARY}")
endif()

add_library(frostsoulx_immersive_engine STATIC
    ${CMAKE_CURRENT_LIST_DIR}/../../app/src/main/cpp/frostsoulx_immersive/src/ImmersiveAudioEngine.cpp)
target_include_directories(frostsoulx_immersive_engine PUBLIC
    ${CMAKE_CURRENT_LIST_DIR}/../../app/src/main/cpp/frostsoulx_immersive/include
    ${STEAMAUDIO_INCLUDE_DIR})
target_compile_definitions(frostsoulx_immersive_engine PRIVATE
    FROSTSOULX_STEAM_AUDIO_AVAILABLE=1)
target_compile_options(frostsoulx_immersive_engine PRIVATE
    -Wall -Wextra -Wpedantic -Wconversion -Wshadow)
target_link_libraries(frostsoulx_immersive_engine PUBLIC ${STEAMAUDIO_LIBRARY} m dl pthread)

add_executable(frostsoulx_closed_car_test closed_car_host_test.cpp)
target_link_libraries(frostsoulx_closed_car_test PRIVATE frostsoulx_immersive_engine)
target_compile_options(frostsoulx_closed_car_test PRIVATE -Wall -Wextra -Wpedantic)
set_target_properties(frostsoulx_closed_car_test PROPERTIES
    BUILD_RPATH "${STEAMAUDIO_SDK_ROOT}/lib/linux-x64"
    INSTALL_RPATH "${STEAMAUDIO_SDK_ROOT}/lib/linux-x64")

enable_testing()
add_test(NAME closed_car_controls COMMAND frostsoulx_closed_car_test)
