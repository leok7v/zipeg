@rm -rf tmp/zipeg
@rm -rf bin/*
@rm -rf classes/*
@rm -rf www/downloads/zipeg*.exe
@rm -rf bin/*.dll
@rm -rf bin/*.jar
@rm -rf src/src/com/zipeg/version.txt
@rm -rf tmp/7za/Release/*.res
@rm -rf tmp/7za/Debug/*.res
@rm -rf tmp/win32reg/*
@rm -rf tmp/zipeg/*
@rm -rf jni/7z/dsp/svn.rev
@rm -rf jni/reg/src/win32reg.rc.rev
@touch jni/reg/src/win32reg.rc
@touch jni/7z/dsp/svn.rev.in
@touch jni/7z/dsp/7za.rc
