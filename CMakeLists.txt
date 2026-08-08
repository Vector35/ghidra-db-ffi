cmake_minimum_required(VERSION 3.19)
project(ghidra-db-ffi)

include(FetchContent)

# Define variables for the GraalVM download
set(GRAALVM_JDK_VERSION "24.0.2")

option(GRADLE_BUILD_CACHE "Enable using gradle's build cache" ON)
option(GRADLE_DAEMON "Use a long-running daemon for gradle build" OFF)
option(GRAALVM_DOWNLOAD "Download and use a GraalVM installation" OFF)


if(DEFINED ENV{GRAALVM_INSTALL_DIR})
    if(UNIVERSAL)
        message(FATAL_ERROR "Universal builds do not support specifying GRAALVM_INSTALL_DIR")
    endif()

    set(GRAALVM_HOME "$ENV{GRAALVM_INSTALL_DIR}")
elseif(GRAALVM_DOWNLOAD)
    if(UNIVERSAL)
        message(FATAL_ERROR "Universal builds do not support GRAALVM_DOWNLOAD")
    endif()

    if(CMAKE_SYSTEM_NAME STREQUAL "Windows")
        set(GRAALVM_OS "windows")
        set(GRAALVM_ARCHIVE_EXTENSION ".zip")
    elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
        set(GRAALVM_OS "macos")
        set(GRAALVM_ARCHIVE_EXTENSION ".tar.gz")
    elseif(CMAKE_SYSTEM_NAME STREQUAL "Linux")
        set(GRAALVM_OS "linux")
        set(GRAALVM_ARCHIVE_EXTENSION ".tar.gz")
    else()
        message(FATAL_ERROR "Unknown target OS: ${CMAKE_SYSTEM_NAME}")
    endif()

    if (CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64")
        set(GRAALVM_ARCH "x64")
    elseif (CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
        set(GRAALVM_ARCH "aarch64")
    else()
        message(FATAL_ERROR "Unknown target architecture: ${CMAKE_SYSTEM_PROCESSOR}")
    endif()

    set(GRAALVM_FILENAME "graalvm-community-jdk-${GRAALVM_JDK_VERSION}_${GRAALVM_OS}-${GRAALVM_ARCH}_bin")
    set(GRAALVM_URL "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_JDK_VERSION}/${GRAALVM_FILENAME}${GRAALVM_ARCHIVE_EXTENSION}")

    FetchContent_Declare(
        graalvm
        URL "${GRAALVM_URL}"
        DOWNLOAD_DIR "${CMAKE_BINARY_DIR}/graalvm-downloads"
        SOURCE_DIR "${CMAKE_BINARY_DIR}/graalvm-install"
        # Since the archive contains a top-level directory,
        # we set the source directory to the extracted path.
        SOURCE_SUBDIR "${GRAALVM_FILENAME}"
    )

    # Populate the content, which downloads and extracts the archive
    FetchContent_MakeAvailable(graalvm)

    # Get the path to the GraalVM binaries
    if(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
        set(GRAALVM_HOME "${graalvm_SOURCE_DIR}/Contents/Home/")
    else()
        set(GRAALVM_HOME "${graalvm_SOURCE_DIR}")
    endif()
else()
    set(GRAALVM_VERSION_DIR "graalvm-community-openjdk-${GRAALVM_JDK_VERSION}+11.1")
    if(APPLE)
        if(UNIVERSAL)
            set(GRAALVM_HOME_X64 "$ENV{HOME}/graalvm/${GRAALVM_VERSION_DIR}_x64/Contents/Home/")
            set(GRAALVM_HOME_AARCH64 "$ENV{HOME}/graalvm/${GRAALVM_VERSION_DIR}_aarch64/Contents/Home/")
            set(JAVA_HOME_X64 "${GRAALVM_HOME_X64}")
            set(JAVA_HOME_AARCH64 "${GRAALVM_HOME_AARCH64}")
        else()
            set(GRAALVM_HOME "$ENV{HOME}/graalvm/${GRAALVM_VERSION_DIR}/Contents/Home/")
            set(JAVA_HOME "${GRAALVM_HOME}")
        endif()
    elseif(WIN32)
        set(GRAALVM_HOME "$ENV{HOMEDRIVE}$ENV{HOMEPATH}/graalvm/${GRAALVM_VERSION_DIR}")
        set(JAVA_HOME "${GRAALVM_HOME}")
    else()
        set(GRAALVM_HOME "$ENV{HOME}/graalvm/${GRAALVM_VERSION_DIR}")
        set(JAVA_HOME "${GRAALVM_HOME}")
    endif()
endif()

if(GRAALVM_HOME)
    message(STATUS "GraalVM: using install at ${GRAALVM_HOME}")
else()
    message(STATUS "GraalVM: using x64 install at ${GRAALVM_HOME_X64}")
    message(STATUS "GraalVM: using aarch64 install at ${GRAALVM_HOME_AARCH64}")
endif()

set(GRADLE_ARG_LIST "")
set(GRADLE_OUTPUT_BASE "${PROJECT_BINARY_DIR}/gradle-build")
set(GRADLE_PROJECT_CACHE_DIR "${PROJECT_BINARY_DIR}/gradle-project-cache")
set(GHIDRA_BINDINGS_HEADER "${PROJECT_SOURCE_DIR}/../java-bindings.h")
set(GRADLE_BINDINGS_HEADER "${PROJECT_BINARY_DIR}/../java-bindings.h")
list(APPEND GRADLE_ARG_LIST
    "-PgradleOutputBase=${GRADLE_OUTPUT_BASE}"
    "--project-cache-dir"
    "${GRADLE_PROJECT_CACHE_DIR}"
)

if(NOT GRADLE_DAEMON)
    list(APPEND GRADLE_ARG_LIST "--no-daemon")
endif()

if(GRADLE_BUILD_CACHE)
    list(APPEND GRADLE_ARG_LIST "--build-cache")
endif()

if(CMAKE_BUILD_TYPE MATCHES Debug)
    list(APPEND GRADLE_ARG_LIST "-PbuildDebug=true")
else()
    list(APPEND GRADLE_ARG_LIST "-PbuildDebug=false")
endif()

if(CMAKE_SYSTEM_NAME STREQUAL "Windows")
set(GRADLEW_COMMAND gradleww.bat)
else()
set(GRADLEW_COMMAND ./gradlew)
endif()


file(GLOB_RECURSE DEPENDS_ON CONFIGURE_DEPENDS
    ${PROJECT_SOURCE_DIR}/build.gradle
    ${PROJECT_SOURCE_DIR}/gradle*
    ${PROJECT_SOURCE_DIR}/settings.gradle
    ${PROJECT_SOURCE_DIR}/src/*.java
    ${PROJECT_SOURCE_DIR}/../java-bindings.h)


set(OUTPUT_FILE_NAME "libghidra${CMAKE_SHARED_LIBRARY_SUFFIX}")
set(OUTPUT_PDB_NAME "libghidra.pdb")
set(OUTPUT_LIB_NAME "libghidra.lib")

if(BN_INTERNAL_BUILD)
    # Demo on windows needs the dll in the same folder as the executable
    if(DEMO AND WIN32)
        set(OUTPUT_FILE_PATH "${BN_CORE_OUTPUT_DIR}/${OUTPUT_FILE_NAME}")
        set(OUTPUT_PDB_PATH "${BN_CORE_OUTPUT_DIR}/${OUTPUT_PDB_NAME}")
        set(OUTPUT_LIB_PATH "${BN_CORE_OUTPUT_DIR}/${OUTPUT_LIB_NAME}")
    else()
        set(OUTPUT_FILE_PATH "${BN_CORE_PLUGIN_DIR}/${OUTPUT_FILE_NAME}")
        set(OUTPUT_PDB_PATH "${BN_CORE_PLUGIN_DIR}/${OUTPUT_PDB_NAME}")
        set(OUTPUT_LIB_PATH "${BN_CORE_PLUGIN_DIR}/${OUTPUT_LIB_NAME}")
    endif()
else()
    set(OUTPUT_FILE_PATH "${PROJECT_BINARY_DIR}/${OUTPUT_FILE_NAME}")
    set(OUTPUT_PDB_PATH "${PROJECT_BINARY_DIR}/${OUTPUT_PDB_NAME}")
    set(OUTPUT_LIB_PATH "${PROJECT_BINARY_DIR}/${OUTPUT_LIB_NAME}")
endif()

if(APPLE AND UNIVERSAL)
    get_filename_component(AARCH64_BUILD_OUTPUT_PATH "${GRADLE_OUTPUT_BASE}/aarch64/native/nativeCompile" ABSOLUTE)
    get_filename_component(X64_BUILD_OUTPUT_PATH "${GRADLE_OUTPUT_BASE}/x64/native/nativeCompile" ABSOLUTE)
else()
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64")
        get_filename_component(BUILD_OUTPUT_PATH "${GRADLE_OUTPUT_BASE}/x64/native/nativeCompile" ABSOLUTE)
    elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
        get_filename_component(BUILD_OUTPUT_PATH "${GRADLE_OUTPUT_BASE}/aarch64/native/nativeCompile" ABSOLUTE)
    else()
        message(FATAL_ERROR "Unknown or unsupported architecture: ${CMAKE_SYSTEM_PROCESSOR}")
    endif()
endif()

add_custom_target(
    ${PROJECT_NAME} ALL DEPENDS
    ${OUTPUT_FILE_PATH}
)

set_property(TARGET ${PROJECT_NAME} PROPERTY OUTPUT_FILE_PATH ${OUTPUT_FILE_PATH})
if(APPLE AND UNIVERSAL)
    set_property(TARGET ${PROJECT_NAME} PROPERTY GHIDRA_DB_FFI_NATIVE_DIR_AARCH64 "${AARCH64_BUILD_OUTPUT_PATH}")
    set_property(TARGET ${PROJECT_NAME} PROPERTY GHIDRA_DB_FFI_NATIVE_DIR_X86_64 "${X64_BUILD_OUTPUT_PATH}")
else()
    set_property(TARGET ${PROJECT_NAME} PROPERTY GHIDRA_DB_FFI_NATIVE_DIR "${BUILD_OUTPUT_PATH}")
endif()
if(WIN32)
    set_property(TARGET ${PROJECT_NAME} PROPERTY OUTPUT_LIB_PATH ${OUTPUT_LIB_PATH})
endif()

if(APPLE)
    if(UNIVERSAL)
        add_custom_command(
            OUTPUT ${OUTPUT_FILE_PATH}
            COMMENT "Building x64 and aarch64 native images with GraalVM"
            COMMAND ${CMAKE_COMMAND} -E copy_if_different "${GHIDRA_BINDINGS_HEADER}" "${GRADLE_BINDINGS_HEADER}"
            COMMAND ${CMAKE_COMMAND} -E env "MACOSX_DEPLOYMENT_TARGET=10.14" "JAVA_HOME=${JAVA_HOME_AARCH64}" "GRAALVM_HOME=${GRAALVM_HOME_AARCH64}" ${GRADLEW_COMMAND} nativeCompile ${GRADLE_ARG_LIST}
            COMMAND ${CMAKE_COMMAND} -E env "MACOSX_DEPLOYMENT_TARGET=10.14" "JAVA_HOME=${JAVA_HOME_X64}" "GRAALVM_HOME=${GRAALVM_HOME_X64}" ${GRADLEW_COMMAND} nativeCompile ${GRADLE_ARG_LIST}
            COMMAND install_name_tool -id "@rpath/${OUTPUT_FILE_NAME}" "${AARCH64_BUILD_OUTPUT_PATH}/${OUTPUT_FILE_NAME}"
            COMMAND install_name_tool -id "@rpath/${OUTPUT_FILE_NAME}" "${X64_BUILD_OUTPUT_PATH}/${OUTPUT_FILE_NAME}"
            COMMAND lipo -create "${AARCH64_BUILD_OUTPUT_PATH}/${OUTPUT_FILE_NAME}" "${X64_BUILD_OUTPUT_PATH}/${OUTPUT_FILE_NAME}" -output "${OUTPUT_FILE_PATH}"
            WORKING_DIRECTORY "${PROJECT_SOURCE_DIR}"
            DEPENDS ${DEPENDS_ON}
            USES_TERMINAL
        )
    else()
        add_custom_command(
            OUTPUT ${OUTPUT_FILE_PATH}
            COMMENT "Building native image with GraalVM"
            COMMAND ${CMAKE_COMMAND} -E copy_if_different "${GHIDRA_BINDINGS_HEADER}" "${GRADLE_BINDINGS_HEADER}"
            COMMAND ${CMAKE_COMMAND} -E env "MACOSX_DEPLOYMENT_TARGET=10.14" "JAVA_HOME=${JAVA_HOME}" "GRAALVM_HOME=${GRAALVM_HOME}" ${GRADLEW_COMMAND} nativeCompile ${GRADLE_ARG_LIST}
            COMMAND install_name_tool -id "@rpath/${OUTPUT_FILE_NAME}" "${BUILD_OUTPUT_PATH}/${OUTPUT_FILE_NAME}"
            COMMAND ${CMAKE_COMMAND} -E copy "${BUILD_OUTPUT_PATH}/${OUTPUT_FILE_NAME}" "${OUTPUT_FILE_PATH}"
            WORKING_DIRECTORY "${PROJECT_SOURCE_DIR}"
            DEPENDS ${DEPENDS_ON}
            USES_TERMINAL
        )
    endif()
elseif(WIN32)
    add_custom_command(
        OUTPUT ${OUTPUT_FILE_PATH} ${OUTPUT_LIB_PATH}
        COMMENT "Building native image with GraalVM"
        COMMAND ${CMAKE_COMMAND} -E copy_if_different "${GHIDRA_BINDINGS_HEADER}" "${GRADLE_BINDINGS_HEADER}"
        COMMAND ${CMAKE_COMMAND} -E env "MACOSX_DEPLOYMENT_TARGET=10.14" "JAVA_HOME=${JAVA_HOME}" "GRAALVM_HOME=${GRAALVM_HOME}" ${GRADLEW_COMMAND} nativeCompile ${GRADLE_ARG_LIST}
        COMMAND ${CMAKE_COMMAND} -E copy "${BUILD_OUTPUT_PATH}/${OUTPUT_FILE_NAME}" "${OUTPUT_FILE_PATH}"
        COMMAND ${CMAKE_COMMAND} -E copy "${BUILD_OUTPUT_PATH}/${OUTPUT_LIB_NAME}" "${OUTPUT_LIB_PATH}"
        WORKING_DIRECTORY "${PROJECT_SOURCE_DIR}"
        DEPENDS ${DEPENDS_ON}
        USES_TERMINAL
    )
else()
    add_custom_command(
        OUTPUT ${OUTPUT_FILE_PATH}
        COMMENT "Building native image with GraalVM"
        COMMAND ${CMAKE_COMMAND} -E copy_if_different "${GHIDRA_BINDINGS_HEADER}" "${GRADLE_BINDINGS_HEADER}"
        COMMAND ${CMAKE_COMMAND} -E env "MACOSX_DEPLOYMENT_TARGET=10.14" "JAVA_HOME=${JAVA_HOME}" "GRAALVM_HOME=${GRAALVM_HOME}" ${GRADLEW_COMMAND} nativeCompile ${GRADLE_ARG_LIST}
        COMMAND ${CMAKE_COMMAND} -E copy "${BUILD_OUTPUT_PATH}/${OUTPUT_FILE_NAME}" "${OUTPUT_FILE_PATH}"
        WORKING_DIRECTORY "${PROJECT_SOURCE_DIR}"
        DEPENDS ${DEPENDS_ON}
        USES_TERMINAL
    )
endif()
