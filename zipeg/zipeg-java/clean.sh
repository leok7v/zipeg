rm -rf classes
rm -rf com/zipeg/version.txt
rm -rf tmp
rm -rf Zipeg.app/Contents/Resources/Java/zipeg.jar
rm -rf  www/downloads/zipeg-update.zip
find jni -name "*.o" -print0 | xargs -0 rm -rf
