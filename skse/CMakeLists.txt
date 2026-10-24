cmake_minimum_required(VERSION 3.25)

project(SkyCraft VERSION 0.1.2 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL")

# CommonLibSSE-NG (SE + AE; VR off to keep the build smaller)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ENABLE_SKYRIM_VR OFF CACHE BOOL "" FORCE)
set(SKSE_SUPPORT_XBYAK ON CACHE BOOL "" FORCE)
add_subdirectory(extern/CommonLibSSE-NG CommonLibSSE EXCLUDE_FROM_ALL)
if(NOT COMMAND add_commonlibsse_plugin)
	include(extern/CommonLibSSE-NG/cmake/CommonLibSSE.cmake)
endif()

file(GLOB_RECURSE SKYCRAFT_SOURCES CONFIGURE_DEPENDS src/*.cpp src/*.h)

add_commonlibsse_plugin(
	SkyCraft
	NAME "SkyCraft"
	AUTHOR "chasmlol"
	USE_ADDRESS_LIBRARY
	SOURCES ${SKYCRAFT_SOURCES}
)

target_include_directories(SkyCraft PRIVATE src ${CMAKE_CURRENT_SOURCE_DIR}/../protocol)
target_precompile_headers(SkyCraft PRIVATE src/PCH.h)
target_link_libraries(SkyCraft PRIVATE d3d11.lib d3dcompiler.lib dxgi.lib ole32.lib oleaut32.lib)
target_compile_definitions(SkyCraft PRIVATE NOMINMAX WIN32_LEAN_AND_MEAN)
if(MSVC)
	target_compile_options(SkyCraft PRIVATE /W4 /permissive- /Zc:preprocessor /utf-8 /MP /wd4200)
	target_link_options(SkyCraft PRIVATE /DEBUG:FULL)
endif()

# Development: copy the built DLL into an MO2 mod folder (-DSKYCRAFT_DEPLOY_DIR=...), when it exists.
if(SKYCRAFT_DEPLOY_DIR AND EXISTS "${SKYCRAFT_DEPLOY_DIR}")
	add_custom_command(TARGET SkyCraft POST_BUILD
		COMMAND ${CMAKE_COMMAND} -E make_directory "${SKYCRAFT_DEPLOY_DIR}/SKSE/Plugins"
		COMMAND ${CMAKE_COMMAND} "-DDLL=$<TARGET_FILE:SkyCraft>" "-DPDB=$<TARGET_PDB_FILE:SkyCraft>" "-DDEST=${SKYCRAFT_DEPLOY_DIR}/SKSE/Plugins" -P "${CMAKE_CURRENT_SOURCE_DIR}/cmake/deploy.cmake"
		VERBATIM)
endif()

# Pure logic tests (no game, any compiler): -DSKYCRAFT_TESTS=ON, then ctest.
option(SKYCRAFT_TESTS "Build the pure logic tests" OFF)
if(SKYCRAFT_TESTS)
	enable_testing()
	add_executable(SkyCraftStripsTest tests/StripsTest.cpp)
	target_include_directories(SkyCraftStripsTest PRIVATE src)
	add_test(NAME StripsTest COMMAND SkyCraftStripsTest)
	add_executable(SkyCraftAnimalKindsTest tests/AnimalKindsTest.cpp)
	target_include_directories(SkyCraftAnimalKindsTest PRIVATE src ${CMAKE_CURRENT_SOURCE_DIR}/../protocol)
	add_test(NAME AnimalKindsTest COMMAND SkyCraftAnimalKindsTest)
	add_executable(SkyCraftStealthTest tests/StealthTest.cpp)
	target_include_directories(SkyCraftStealthTest PRIVATE src ${CMAKE_CURRENT_SOURCE_DIR}/../protocol)
	add_test(NAME StealthTest COMMAND SkyCraftStealthTest)
	add_executable(SkyCraftWeatherKindsTest tests/WeatherKindsTest.cpp)
	target_include_directories(SkyCraftWeatherKindsTest PRIVATE src ${CMAKE_CURRENT_SOURCE_DIR}/../protocol)
	add_test(NAME WeatherKindsTest COMMAND SkyCraftWeatherKindsTest)
	add_executable(SkyCraftBlessingKindsTest tests/BlessingKindsTest.cpp)
	target_include_directories(SkyCraftBlessingKindsTest PRIVATE src ${CMAKE_CURRENT_SOURCE_DIR}/../protocol)
	add_test(NAME BlessingKindsTest COMMAND SkyCraftBlessingKindsTest)
	add_executable(SkyCraftFoodValuesTest tests/FoodValuesTest.cpp)
	target_include_directories(SkyCraftFoodValuesTest PRIVATE src ${CMAKE_CURRENT_SOURCE_DIR}/../protocol)
	add_test(NAME FoodValuesTest COMMAND SkyCraftFoodValuesTest)
	add_executable(SkyCraftTownKindsTest tests/TownKindsTest.cpp)
	target_include_directories(SkyCraftTownKindsTest PRIVATE src ${CMAKE_CURRENT_SOURCE_DIR}/../protocol)
	add_test(NAME TownKindsTest COMMAND SkyCraftTownKindsTest)
	add_executable(SkyCraftCreatureKindsTest tests/CreatureKindsTest.cpp)
	target_include_directories(SkyCraftCreatureKindsTest PRIVATE src ${CMAKE_CURRENT_SOURCE_DIR}/../protocol)
	add_test(NAME CreatureKindsTest COMMAND SkyCraftCreatureKindsTest)
	add_executable(SkyCraftBackupNamesTest tests/BackupNamesTest.cpp)
	target_include_directories(SkyCraftBackupNamesTest PRIVATE src)
	add_test(NAME BackupNamesTest COMMAND SkyCraftBackupNamesTest)
	add_executable(SkyCraftQuestTargetsTest tests/QuestTargetsTest.cpp)
	target_include_directories(SkyCraftQuestTargetsTest PRIVATE src ${CMAKE_CURRENT_SOURCE_DIR}/../protocol)
	add_test(NAME QuestTargetsTest COMMAND SkyCraftQuestTargetsTest)
endif()
