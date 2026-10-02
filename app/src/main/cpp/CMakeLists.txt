cmake_minimum_required(VERSION 3.22.1)
project(seedless_core C CXX ASM)

file(GLOB RCHEEVOS_SRC
        rcheevos/src/rcheevos/*.c
        rcheevos/src/rapi/*.c
        rcheevos/src/rhash/*.c
        rcheevos/src/rc_client.c
        rcheevos/src/rc_util.c
        rcheevos/src/rc_compat.c
        rcheevos/src/rc_version.c)
add_library(rcheevos STATIC ${RCHEEVOS_SRC})
target_include_directories(rcheevos PUBLIC rcheevos/include PRIVATE rcheevos/src)

target_compile_definitions(rcheevos PRIVATE RC_DISABLE_LUA=1 PUBLIC RC_CLIENT_SUPPORTS_HASH=1)
find_library(z-lib z)
target_link_libraries(rcheevos ${z-lib})

add_library(seedless_bridge SHARED
        seedless_native.c
        ra_client.c)

find_library(log-lib log)
target_include_directories(seedless_bridge PRIVATE rcheevos/include)
target_link_libraries(seedless_bridge ${log-lib} dl rcheevos)
target_link_options(seedless_bridge PRIVATE -Wl,-z,max-page-size=16384)

set(SEEDLESS_CORE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../core" CACHE PATH
    "Path to the SeedlessDS core source tree")
if(NOT EXISTS "${SEEDLESS_CORE_DIR}/src/hires_runtime.h")
    message(FATAL_ERROR "Core source tree not found: ${SEEDLESS_CORE_DIR}. Initialize the core submodule or set SEEDLESS_CORE_DIR.")
endif()

add_subdirectory("${SEEDLESS_CORE_DIR}/third_party" "${CMAKE_CURRENT_BINARY_DIR}/third_party")

add_library(recon_fn SHARED
        ${SEEDLESS_CORE_DIR}/src/platform/crash_report.c)
target_include_directories(recon_fn PRIVATE
        "${SEEDLESS_CORE_DIR}/include" "${CMAKE_CURRENT_BINARY_DIR}"
        "${SEEDLESS_CORE_DIR}/third_party/lua")
target_link_libraries(recon_fn seedless_lua seedless_unrar seedless_lzma z)
include("${SEEDLESS_CORE_DIR}/src/CMakeLists.txt")

add_library(recon SHARED
        recon_android.c
        jni/core_system.c
        jni/runtime_control.c
        jni/core_frame.c
        jni/core_state.c
        jni/core_cheats.c
        jni/core_fx.c)
target_include_directories(recon PRIVATE
        "${SEEDLESS_CORE_DIR}/recon" "${SEEDLESS_CORE_DIR}/include" "${CMAKE_CURRENT_BINARY_DIR}")
target_link_options(recon PRIVATE -Wl,-z,max-page-size=16384)

target_compile_options(recon_fn PRIVATE
        -O2 -fno-strict-aliasing
        $<$<NOT:$<CONFIG:Debug>>:-fno-stack-protector>
        -Werror=int-conversion -Werror=incompatible-pointer-types
        -Werror=pointer-sign -Werror=unused-variable -Werror=unused-function -Werror=unused-const-variable)
target_link_libraries(recon_fn ${log-lib} dl m OpenSLES GLESv2)
target_link_options(recon_fn PRIVATE -Wl,-Bsymbolic -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384)
target_link_libraries(recon ${log-lib} dl recon_fn)
