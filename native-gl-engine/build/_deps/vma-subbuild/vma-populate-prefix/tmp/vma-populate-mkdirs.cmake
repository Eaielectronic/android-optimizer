# Distributed under the OSI-approved BSD 3-Clause License.  See accompanying
# file Copyright.txt or https://cmake.org/licensing for details.

cmake_minimum_required(VERSION 3.5)

file(MAKE_DIRECTORY
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/vma-src"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/vma-build"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/vma-subbuild/vma-populate-prefix"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/vma-subbuild/vma-populate-prefix/tmp"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/vma-subbuild/vma-populate-prefix/src/vma-populate-stamp"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/vma-subbuild/vma-populate-prefix/src"
  "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/vma-subbuild/vma-populate-prefix/src/vma-populate-stamp"
)

set(configSubDirs )
foreach(subDir IN LISTS configSubDirs)
    file(MAKE_DIRECTORY "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/vma-subbuild/vma-populate-prefix/src/vma-populate-stamp/${subDir}")
endforeach()
if(cfgdir)
  file(MAKE_DIRECTORY "/home/ubuntu/android-optimizer/native-gl-engine/build/_deps/vma-subbuild/vma-populate-prefix/src/vma-populate-stamp${cfgdir}") # cfgdir has leading slash
endif()
