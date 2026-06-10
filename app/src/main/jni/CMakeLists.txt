cmake_minimum_required(VERSION 3.18)
project("kfjni")

add_library(kfjni SHARED kfjni.c)

target_link_libraries(kfjni log)
