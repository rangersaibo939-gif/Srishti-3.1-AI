cmake_minimum_required(VERSION 3.22.1)
project("opendroid_llama" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Configure arm64-v8a optimization flags
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_definitions(-D__ARM_NEON)
    add_definitions(-D__ARM_FEATURE_DOTPROD)
    add_definitions(-D__ARM_FEATURE_FP16_VECTOR_ARITHMETIC)
endif()

# Configure llama.cpp build for embedded Android arm64
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Do not build tests" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Do not build examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Do not build server" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "Do not build with curl" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static llama/ggml libraries" FORCE)

# Include the pinned llama.cpp source distribution (revision b3600)
add_subdirectory(llama.cpp)

# Shared JNI bridge library
add_library(opendroid_llama SHARED
    llama-jni.cpp
    llama.cpp/common/grammar-parser.cpp
)

target_include_directories(opendroid_llama PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/src
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common
)

# Target link libraries: link core llama and ggml static engines
target_link_libraries(opendroid_llama
    llama
    ggml
    android
    log
)
