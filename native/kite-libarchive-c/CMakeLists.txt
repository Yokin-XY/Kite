cmake_minimum_required(VERSION 3.18)
project(kite_libarchive_benchmark C)

include(FetchContent)

set(ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(ENABLE_STATIC ON CACHE BOOL "" FORCE)
set(ENABLE_TEST OFF CACHE BOOL "" FORCE)
set(ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
set(ENABLE_TAR OFF CACHE BOOL "" FORCE)
set(ENABLE_CPIO OFF CACHE BOOL "" FORCE)
set(ENABLE_CAT OFF CACHE BOOL "" FORCE)
set(ENABLE_UNZIP OFF CACHE BOOL "" FORCE)
set(ENABLE_OPENSSL OFF CACHE BOOL "" FORCE)
set(ENABLE_MBEDTLS OFF CACHE BOOL "" FORCE)
set(ENABLE_NETTLE OFF CACHE BOOL "" FORCE)
set(ENABLE_LIBB2 OFF CACHE BOOL "" FORCE)
set(ENABLE_LZ4 OFF CACHE BOOL "" FORCE)
set(ENABLE_LZO OFF CACHE BOOL "" FORCE)
set(ENABLE_LZMA OFF CACHE BOOL "" FORCE)
set(ENABLE_ZSTD OFF CACHE BOOL "" FORCE)
set(ENABLE_BZip2 OFF CACHE BOOL "" FORCE)
set(ENABLE_ZLIB ON CACHE BOOL "" FORCE)

FetchContent_Declare(
    libarchive
    URL https://github.com/libarchive/libarchive/releases/download/v3.8.9/libarchive-3.8.9.zip
    URL_HASH SHA256=7b20200203d08866b62373dd51417798427ec67852928d71f142bfe578e9ea75
)
FetchContent_MakeAvailable(libarchive)

add_executable(kite_libarchive_benchmark kite_libarchive_benchmark.c)
target_compile_features(kite_libarchive_benchmark PRIVATE c_std_11)
target_compile_options(kite_libarchive_benchmark PRIVATE -Wall -Wextra -Werror)
target_link_libraries(kite_libarchive_benchmark PRIVATE archive_static)
