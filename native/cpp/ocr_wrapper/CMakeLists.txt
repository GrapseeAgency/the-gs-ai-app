# gs_ocr — OCR C ABI, cross-compiled for Android.
#
# The Paddle-Lite runtime is OPTIONAL at configure time. Without it the
# wrapper still builds for arm64 and every entry point reports UNAVAILABLE,
# which is the honest state: it proves the FFI layer, the toolchain and the
# abiFilters wiring, and leaves only the runtime artefact to link in later.
#
# Usage:
#   cmake -B build-android-arm64 \
#     -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
#     -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 -DANDROID_STL=c++_shared
#   cmake --build build-android-arm64 -j$(nproc)
cmake_minimum_required(VERSION 3.18)
project(gs_ocr CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(PADDLE_LITE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/vendor" CACHE PATH
    "Root of the Paddle-Lite prebuilt tree (must contain include/ and libs/)")

set(PADDLE_LITE_SO
    "${PADDLE_LITE_DIR}/libs/arm64-v8a/libpaddle_light_api_shared.so")

add_library(gs_ocr SHARED src/ocr_wrapper.cpp)

target_include_directories(gs_ocr PRIVATE include)

# Only wire the real runtime in when it is actually present. Guessing here
# produces a link error that looks like a code bug.
if(EXISTS "${PADDLE_LITE_SO}")
  message(STATUS "gs_ocr: Paddle-Lite found at ${PADDLE_LITE_SO} - linking real runtime")
  target_compile_definitions(gs_ocr PRIVATE GS_OCR_HAVE_PADDLELITE)
  target_include_directories(gs_ocr PRIVATE "${PADDLE_LITE_DIR}/include")
  target_link_libraries(gs_ocr "${PADDLE_LITE_SO}")
else()
  message(WARNING
    "gs_ocr: Paddle-Lite NOT found at ${PADDLE_LITE_SO}. Building the C ABI "
    "surface only. gs_ocr_available() will report 0 and gs_ocr_recognize() "
    "will return NULL with the reason in gs_ocr_last_error(). This is the "
    "expected state until the runtime artefact is obtained.")
endif()

# The consumer app must ship libc++_shared.so alongside this library, so the
# install RPATH is $ORIGIN rather than an absolute build path.
set_target_properties(gs_ocr PROPERTIES
  BUILD_RPATH "$ORIGIN"
  INSTALL_RPATH "$ORIGIN")

install(TARGETS gs_ocr LIBRARY DESTINATION lib/${ANDROID_ABI})
