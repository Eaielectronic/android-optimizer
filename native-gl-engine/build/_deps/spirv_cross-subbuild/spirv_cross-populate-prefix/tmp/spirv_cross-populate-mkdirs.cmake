# Distributed under the OSI-approved BSD 3-Clause License.  See accompanying
# file Copyright.txt or https://cmake.org/licensing for details.

cmake_minimum_required(VERSION 3.5)

file(MAKE_DIRECTORY
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/spirv_cross-src"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/spirv_cross-build"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/spirv_cross-subbuild/spirv_cross-populate-prefix"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/spirv_cross-subbuild/spirv_cross-populate-prefix/tmp"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/spirv_cross-subbuild/spirv_cross-populate-prefix/src/spirv_cross-populate-stamp"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/spirv_cross-subbuild/spirv_cross-populate-prefix/src"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/spirv_cross-subbuild/spirv_cross-populate-prefix/src/spirv_cross-populate-stamp"
)

set(configSubDirs )
foreach(subDir IN LISTS configSubDirs)
    file(MAKE_DIRECTORY "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/spirv_cross-subbuild/spirv_cross-populate-prefix/src/spirv_cross-populate-stamp/${subDir}")
endforeach()
if(cfgdir)
  file(MAKE_DIRECTORY "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/spirv_cross-subbuild/spirv_cross-populate-prefix/src/spirv_cross-populate-stamp${cfgdir}") # cfgdir has leading slash
endif()
