export cpu=`uname -p`
if [[ "$cpu" == "powerpc" || "$cpu" == "ppc" ]]; then
  export cpu="ppc"
else
  export cpu="i386"
fi
echo arch=$cpu
rm libzipeg_osx.jnilib
rm libzipeg-osx-$cpu.jnilib

export OS_VERSION_MAJOR=`sw_vers -productVersion | cut -f1 -d'.'`
export OS_VERSION_MINOR=`sw_vers -productVersion | cut -f2 -d'.'`
echo OS_VERSION_MAJOR="$OS_VERSION_MAJOR"
echo OS_VERSION_MINOR="$OS_VERSION_MINOR"
export X_LDFLAGS=
export X_CCFLAGS=
if [ "$OS_VERSION_MAJOR" -ge "10" ]; then
	if [ "$OS_VERSION_MINOR" -ge "7" ]; then
              export X_LDFLAGS="-Xlinker -no_compact_linkedit -Xlinker -flat_namespace"
              export X_CCFLAGS="-iwithsysroot /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.4u.sdk"
	else
          if [ "$OS_VERSION_MINOR" -ge "6" ]; then
              export X_LDFLAGS="-Xlinker -no_compact_linkedit -Xlinker -flat_namespace"
              export X_CCFLAGS="-iwithsysroot /Developer/SDKs/MacOSX10.4u.sdk"
          fi
        fi
fi
if [ "$OS_VERSION_MINOR" -ge "7" ]; then
      #echo X_LDFLAGS="$X_LDFLAGS"
      #echo X_CCFLAGS="$X_CCFLAGS"
      c++ ${X_LDFLAGS} -arch i386 ${X_CCFLAGS} \
      -dynamiclib -single_module \
      -mmacosx-version-min=10.4 -Oz -o libzipeg-osx-$cpu.jnilib \
      -framework JavaVM -framework Cocoa -framework CoreFoundation -framework ApplicationServices \
      -I/System/Library/Frameworks/JavaVM.framework/Headers \
      DefaultRoleHandler.cpp CocoaSetContentMinSize.m
else
    if [ "$OS_VERSION_MINOR" -ge "5" ]; then
      #echo X_LDFLAGS="$X_LDFLAGS"
      #echo X_CCFLAGS="$X_CCFLAGS"
      c++ ${X_LDFLAGS} -arch i386 -arch ppc ${X_CCFLAGS} \
      -dynamiclib -single_module \
      -mmacosx-version-min=10.4 -Oz -o libzipeg-osx-$cpu.jnilib \
      -framework JavaVM -framework Cocoa -framework CoreFoundation -framework ApplicationServices \
      -I/System/Library/Frameworks/JavaVM.framework/Headers \
      DefaultRoleHandler.cpp CocoaSetContentMinSize.m
    else
      c++ -arch i386 -arch ppc -mmacosx-version-min=10.4 -dynamiclib -single_module \
      -Wl,-syslibroot,/Developer/SDKs/MacOSX10.4u.sdk \
      -framework CoreFoundation -framework ApplicationServices -framework JavaVM -framework Cocoa \
      -Oz -o libzipeg-osx-$cpu.jnilib \
      -I/System/Library/Frameworks/JavaVM.framework/Headers DefaultRoleHandler.cpp CocoaSetContentMinSize.m
    fi
fi
if [ -f libzipeg-osx-$cpu.jnilib ]; then
   cp libzipeg-osx-$cpu.jnilib ../../Zipeg.app/Contents/Resources/Java/
else
   echo "failed to build libzipeg-osx-$cpu.jnilib"
   exit 1
fi
