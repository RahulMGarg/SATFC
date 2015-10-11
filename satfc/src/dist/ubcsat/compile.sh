#!/bin/bash -e
# Compiles the UBCSAT/SATenstein JNA library .so file for SATFC.

cd ubcsat_stein
rm -rf build
mkdir build
cd build
echo "Building UBCSAT ..."
cmake ..
make jnaubcsat
echo "Moving UBCSAT library ..."
mkdir -p ../../jna
mv libjnaubcsat.so ../../jna
cd ../..