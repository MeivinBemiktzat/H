cmake_minimum_required(VERSION 3.22.1)
project(ailocal_llama)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp is fetched as source at configure time (CI has network access;
# the Claude build environment that generated this project does not, which
# is why this step is deferred to the GitHub Actions build machine).
include(FetchContent)

set(LLAMA_CPP_TAG "b4304" CACHE STRING "llama.cpp release tag to build against")

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG ${LLAMA_CPP_TAG}
    GIT_SHALLOW TRUE
)

# Keep llama.cpp's own build minimal - we only need the core library.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama_cpp)

add_library(ailocal_llama SHARED
    llama_bridge.cpp
)

target_include_directories(ailocal_llama PRIVATE
    ${llama_cpp_SOURCE_DIR}
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
)

find_library(log-lib log)

target_link_libraries(ailocal_llama
    llama
    ggml
    ${log-lib}
    android
)

target_compile_options(ailocal_llama PRIVATE -O3 -fvisibility=hidden)
