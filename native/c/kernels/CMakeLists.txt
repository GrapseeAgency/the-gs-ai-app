# GS AI CPU kernels. Rule 11: warnings are errors.
cmake_minimum_required(VERSION 3.16)
project(gs_kernels C)

add_library(gs_kernels STATIC simd.c)
target_include_directories(gs_kernels PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(gs_kernels PRIVATE -Wall -Wextra -Werror -O3)

add_executable(gs_test_kernels test_kernels.c)
target_link_libraries(gs_test_kernels PRIVATE gs_kernels m)

enable_testing()
add_test(NAME kernels COMMAND gs_test_kernels)
