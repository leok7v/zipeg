export cpu=`uname -p`
if [[ "$cpu" == "powerpc" || "$cpu" == "ppc" ]]; then
  export cpu="ppc"
else
  export cpu="i386"
fi
echo "cpu=$cpu"
make jni
if [ -f bin/lib7za.jnilib ]; then
   mv bin/lib7za.jnilib ../../Zipeg.app/Contents/Resources/Java/lib7za-osx-$cpu.jnilib
else
   echo *** exit ***
   exit 1
fi

