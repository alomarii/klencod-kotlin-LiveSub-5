cmake_minimum_required(VERSION 3.22.1)
project(livesub_whisper)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)
if(NOT EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    message(STATUS "Cloning whisper.cpp ...")
    execute_process(
        COMMAND git clone --depth 1 --branch v1.7.4 https://github.com/ggerganov/whisper.cpp.git ${WHISPER_DIR}
        RESULT_VARIABLE GIT_RC)
    if(NOT GIT_RC EQUAL 0 OR NOT EXISTS ${WHISPER_DIR}/CMakeLists.txt)
        message(FATAL_ERROR "فشل تنزيل whisper.cpp (git clone). نزّله يدوياً إلى app/src/main/cpp/whisper.cpp")
    endif()
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")
add_subdirectory(${WHISPER_DIR} whisper_build)

add_library(livesub_whisper SHARED jni.cpp)
target_include_directories(livesub_whisper PRIVATE ${WHISPER_DIR}/include ${WHISPER_DIR}/ggml/include)
target_link_libraries(livesub_whisper whisper log android)
