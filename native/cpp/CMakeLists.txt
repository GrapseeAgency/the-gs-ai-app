# GS AI C++ wrappers. Every target is a C ABI surface: extern "C" only, no
# exceptions across the boundary.
cmake_minimum_required(VERSION 3.16)
project(gs_cpp CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_library(gs_abi STATIC src/gs_abi.cpp)
target_include_directories(gs_abi PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)

# ---- llama wrapper -------------------------------------------------------
# libllama is vendored, not committed. Point GS_LLAMA_ROOT at a build tree, or
# leave it unset to compile the ABI surface standalone (every call then reports
# GS_ERR_UNAVAILABLE, which is the honest state).
add_library(gs_llama STATIC llama_wrapper/llama_wrapper.cpp)
target_link_libraries(gs_llama PUBLIC gs_abi)
target_include_directories(gs_llama PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_wrapper)
if(DEFINED GS_LLAMA_ROOT AND EXISTS "${GS_LLAMA_ROOT}/include/llama.h")
  target_compile_definitions(gs_llama PRIVATE GS_LLAMA_HAVE_LLAMA)
  # llama.h does `#include "ggml.h"`, and ggml headers live in a SEPARATE
  # tree from llama's own. Both must be on the include path or the compile
  # dies with "fatal error: ggml.h: No such file or directory".
  target_include_directories(gs_llama PRIVATE
      ${GS_LLAMA_ROOT}/include
      ${GS_LLAMA_ROOT}/ggml/include)
  # Prefer the versioned .so over the bare symlink so the runtime loader keeps
  # a resolvable SONAME.
  find_library(GS_LLAMA_SO
    NAMES llama PATHS ${GS_LLAMA_ROOT}/build/bin ${GS_LLAMA_ROOT}/bin NO_DEFAULT_PATH)
  if(GS_LLAMA_SO)
    target_link_libraries(gs_llama PUBLIC ${GS_LLAMA_SO})
  else()
    target_link_libraries(gs_llama PUBLIC ${GS_LLAMA_ROOT}/bin/libllama.so)
  endif()
  message(STATUS "gs_llama: linked against libllama at ${GS_LLAMA_ROOT}")
else()
  message(WARNING "gs_llama: GS_LLAMA_ROOT not set - ABI surface only, generation BLOCKED")
endif()

# ---- sd + ocr wrappers ---------------------------------------------------
add_library(gs_sd STATIC sd_wrapper/gs_sd_wrapper.cpp)
target_link_libraries(gs_sd PUBLIC gs_abi)
target_include_directories(gs_sd PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/sd_wrapper)

add_library(gs_ocr STATIC ocr_wrapper/ocr_wrapper.cpp)
target_link_libraries(gs_ocr PUBLIC gs_abi)
target_include_directories(gs_ocr PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/ocr_wrapper)
