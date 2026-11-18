cmake_minimum_required(VERSION 3.22.1)
project(frostsoulx_audio LANGUAGES CXX)

set(FROSTSOULX_BUILD_TESTS OFF CACHE BOOL "Host tests" FORCE)
set(FROSTSOULX_BUILD_PLUGIN OFF CACHE BOOL "Standalone plugin" FORCE)
add_subdirectory(frostsoulx_immersive)
add_library(frostsoulx_immersive_jni SHARED frostsoulx_immersive_jni.cpp)
target_link_libraries(frostsoulx_immersive_jni PRIVATE frostsoulx_immersive_audio)
target_compile_features(frostsoulx_immersive_jni PRIVATE cxx_std_17)
# The supplied engine catches allocation failures on the control thread.
target_compile_options(frostsoulx_immersive_audio PRIVATE -fexceptions)
target_link_options(frostsoulx_immersive_jni PRIVATE "-Wl,-z,max-page-size=16384")
