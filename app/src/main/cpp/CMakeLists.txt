# Módulo nativo llama.cpp para Ona-MiCiclo (Fase 2 GGUF).
# Descarga llama.cpp pinnado (v0.5.0) vía FetchContent y compila solo el core
# `llama` (sin server/examples/tests). El puente JNI está en llama_bridge.cpp.
cmake_minimum_required(VERSION 3.22.1)
project(ona_llama)

set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

include(FetchContent)
FetchContent_Declare(
    llama
    URL https://github.com/ggml-org/llama.cpp/archive/refs/tags/v0.5.0.tar.gz
)
FetchContent_MakeAvailable(llama)

add_library(ona_llama SHARED llama_bridge.cpp)

target_link_libraries(ona_llama PRIVATE llama)
target_include_directories(ona_llama PRIVATE ${llama_SOURCE_DIR}/include)
