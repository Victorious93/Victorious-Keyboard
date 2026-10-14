# llama.cpp b11624 is fetched at build time (MIT). No model weights are compiled in.
cmake_minimum_required(VERSION 3.22.1)
project(victorious_llama)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_HTML OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_NUMBER 11624 CACHE STRING "" FORCE)
set(LLAMA_BUILD_COMMIT "b11624" CACHE STRING "" FORCE)

include(FetchContent)
FetchContent_Declare(
    llama_cpp
    URL https://github.com/ggml-org/llama.cpp/archive/refs/tags/b11624.tar.gz
    URL_HASH SHA256=9a44be53ab3c76ab958131e8d9f2e14ab1e885cf1841afdcd77ca936c066249f
)
FetchContent_MakeAvailable(llama_cpp)

add_library(victorious_llama SHARED llama_jni.cpp)
target_compile_features(victorious_llama PRIVATE cxx_std_17)
target_compile_options(victorious_llama PRIVATE -fexceptions -fno-rtti)
target_link_options(victorious_llama PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--gc-sections")
target_link_libraries(victorious_llama llama log)
