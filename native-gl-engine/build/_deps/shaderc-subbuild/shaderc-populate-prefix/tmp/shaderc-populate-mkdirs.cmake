# Distributed under the OSI-approved BSD 3-Clause License.  See accompanying
# file Copyright.txt or https://cmake.org/licensing for details.

cmake_minimum_required(VERSION 3.5)

file(MAKE_DIRECTORY
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/shaderc-src"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/shaderc-build"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/shaderc-subbuild/shaderc-populate-prefix"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/shaderc-subbuild/shaderc-populate-prefix/tmp"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/shaderc-subbuild/shaderc-populate-prefix/src/shaderc-populate-stamp"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/shaderc-subbuild/shaderc-populate-prefix/src"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/shaderc-subbuild/shaderc-populate-prefix/src/shaderc-populate-stamp"
)

set(configSubDirs )
foreach(subDir IN LISTS configSubDirs)
    file(MAKE_DIRECTORY "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/shaderc-subbuild/shaderc-populate-prefix/src/shaderc-populate-stamp/${subDir}")
endforeach()
if(cfgdir)
  file(MAKE_DIRECTORY "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/shaderc-subbuild/shaderc-populate-prefix/src/shaderc-populate-stamp${cfgdir}") # cfgdir has leading slash
endif()
