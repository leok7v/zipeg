#!/bin/bash
export JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/1.5/Home
export PATH=$JAVA_HOME/bin:$JAVA_HOME/jre/bin:$PATH
export cpu=`uname -p`
if [[ "$cpu" == "powerpc" || "$cpu" == "ppc" ]]; then
  export cpu="ppc"
else
  export cpu="i386"
fi
echo arch=$cpu
svn up .
export rev=`./get_svn_rev.sh`
export ver=2.9.5
pushd jni/7z
./jni.build.sh
popd
pushd jni/macosx
./jni.build.sh
popd

echo "building zipeg.$ver.dmg from svn revision $rev"
echo version=$ver.$rev >./src/com/zipeg/version.txt

rm -rf classes
mkdir -p classes
javac -classpath ./src -g -source 1.5 -target 1.5 -d classes src/com/zipeg/*.java src/org/mozilla/universalchardet/*.java
cp -f src/com/zipeg/version.txt classes/com/zipeg/version.txt
mkdir -p classes/com/zipeg/resources
cp -f src/com/zipeg/resources/* classes/com/zipeg/resources
# June 2010: do not remove win.* resources - just asking for trouble... for a small space savings
# rm -rf classes/com/zipeg/resources/win.*

rm -rf Zipeg.app/Contents/Resources/Java/zipeg.jar
rm -rf www/downloads/zipeg.jar
jar cfm0 Zipeg.app/Contents/Resources/Java/zipeg.jar src/META-INF/MANIFEST.MF -C ./classes/ .
jar cfm0 www/downloads/zipeg.jar src/META-INF/MANIFEST.MF -C ./classes/ .

rm -f Zipeg.app/Contents/Resources/Java/*.log
rm -f Zipeg.app/Contents/Resources/Java/*.dll

echo "Building Zipeg.app with IconCR"
pushd Zipeg.app
rm -rf Icon*
cp -f ../IconCR `echo -e 'Icon\x0D'`
if [ "$cpu" == "ppc" ]; then
  echo "ppc: using G3/G4 compatible stub JavaApplicationStub.ppc as JavaApplicationStub"
  cp -f ../JavaApplicationStub.ppc Contents/MacOS/JavaApplicationStub
fi
popd
chmod 755 Zipeg.app/Contents/MacOS/JavaApplicationStub
ls -l Zipeg.app/Icon*
if [ -f /Developer/Tools/SetFile ]; then
/Developer/Tools/SetFile -a B Zipeg.app
/Developer/Tools/SetFile -a C Zipeg.app
/Developer/Tools/GetFileInfo Zipeg.app
fi
if [ -f /usr/bin/SetFile ]; then
/usr/bin/SetFile -a B Zipeg.app
/usr/bin/SetFile -a C Zipeg.app
/usr/bin/GetFileInfo Zipeg.app
fi

ls -l www/downloads/zipeg.jar Zipeg.app/Contents/Resources/Java/zipeg.jar

rm -rf zipeg.dmg
rm -rf tmp
mkdir tmp
cp -R Zipeg.app tmp/
pushd tmp
chmod -R 777 Zipeg.app
find . -name .svn -print0 | xargs -0 rm -rf
lipo -remove x86_64 Zipeg.app/Contents/MacOS/JavaApplicationStub -output Zipeg.app/Contents/MacOS/JavaApplicationStub
if [ -d /Volumes/Zipeg ]; then
  hdiutil eject /Volumes/Zipeg
fi
if [ -f zipeg.dmg ]; then
  rm -f zipeg.dmg
fi
if [ -f zipeg.rw.dmg ]; then
  rm -f zipeg.rw.dmg
fi

echo "Creating zipeg.dmg..."
hdiutil create -srcfolder Zipeg.app -volname Zipeg -ov zipeg.rw.dmg -format UDRW -quiet
hdiutil attach zipeg.rw.dmg -quiet
cp ../Zipeg.DS_Store /Volumes/Zipeg/.DS_Store
chmod 444 /Volumes/Zipeg/.DS_Store
mkdir -p /Volumes/Zipeg/.background
cp ../background.png /Volumes/Zipeg/.background/background.png
cp ../iconCR /Volumes/Zipeg/.VolumeIcon.icns
ln -s /Applications /Volumes/Zipeg/Applications
hdiutil eject /Volumes/Zipeg -quiet

echo "Compressing zipeg.dmg..."
hdiutil convert zipeg.rw.dmg -format UDZO -o zipeg.dmg -quiet -imagekey zlib-level=9
#do NOT internet enable the image. This will ensure that Safari will mount it instead of IDME processing it
hdiutil internet-enable -no zipeg.dmg -quiet

rm -rf ../www/downloads/zipeg.$ver.dmg
cp zipeg.dmg ../www/downloads/zipeg.$ver.$rev.dmg
cp zipeg.dmg ../www/downloads/zipeg.dmg
if [ "$cpu" == "ppc" ]; then
  cp zipeg.dmg ../www/downloads/ZipegForMacintoshPPC.dmg
  cp zipeg.dmg ../www/downloads/zipeg.ppc.$ver.$rev.dmg
else
  cp zipeg.dmg ../www/downloads/ZipegForMacintosh.dmg
fi
pushd ../Zipeg.app/Contents/Resources/Java/
pwd
if [ "$cpu" != "ppc" ]; then
  echo "i386 plugin: lib7za-osx-i386.jnilib"
  chmod 755 lib7za-osx-i386.jnilib
  zip -9q lib7za-osx-i386.zip lib7za-osx-i386.jnilib
else
  echo "ppc plugin: lib7za-osx-ppc.jnilib"
  chmod 755 lib7za-osx-ppc.jnilib
  zip -9q lib7za-osx-ppc.zip lib7za-osx-ppc.jnilib
fi
zip -9q -j zipeg-update.zip *.jnilib *.jar *.scpt ../Zipeg.icns ../Archive.icns ../../Info.plist ../../../Icon*
mv lib7za-osx-*.zip ../../../../www/downloads/
mv zipeg-update.zip ../../../../www/downloads/
popd
rm -rf ../www/downloads/latest/*.dmg
rm -rf ../www/downloads/latest/*.zip
mkdir -p ../www/downloads/latest
cp ../www/downloads/zipeg.$ver.$rev.dmg ../www/downloads/latest
cp ../www/downloads/zipeg.dmg           ../www/downloads/latest
cp ../www/downloads/zipeg-update.zip    ../www/downloads/latest
popd
ls -l www/downloads/*.dmg www/downloads/*.jar www/downloads/*.zip
