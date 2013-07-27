@echo off
@pushd ..\..
@svn up
@for /F "usebackq delims==" %%a in (`bash.exe ./get_svn_rev.sh`) do set rev=%%a
@set ver=2.9.5
@echo updating version.txt to %ver%.%rev%
@echo version=%ver%.%rev% >./src/com/zipeg/version.txt
@cat src/com/zipeg/version.txt
@rm -rf classes
@mkdir classes
@rem http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6476630 will not be able to suppress warnings :-(
set
@javac -source 1.5 -target 1.5 -g -cp src -d classes src\com\zipeg\*.java src\com\zipeg\win\*.java src\org\mozilla\universalchardet\*.java
@cp -f src/com/zipeg/version.txt classes/com/zipeg/version.txt
@mkdir classes\com\zipeg\resources
@cp -f src/com/zipeg/resources/* classes/com/zipeg/resources
@rm -rf classes/com/zipeg/resources/mac.*
@rm -rf Zipeg.app/Contents/Resources/Java/zipeg.jar
@jar cfm0 Zipeg.app/Contents/Resources/Java/zipeg.jar src/META-INF/MANIFEST.MF -C classes .
@rem update.jar can only be generated on Max OS X!!!

@cd bin
@rm -rf zipeg.jar
@cp -f ..\Zipeg.app\Contents\Resources\Java\zipeg.jar zipeg.jar
@rm -rf zipeg.zip
@rm -rf 7za-win-i386.zip
@rm -rf win32reg.zip
@zip -9 zipeg.zip zipeg.jar >nul
if "%2" == "NDEBUG" (
@zip -9 7za-win-i386.zip 7za-win-i386.dll >nul
@zip -9 win32reg.zip win32reg.dll >nul
) else (
@zip -9 7za-win-i386.zip 7za-win-i386-dbg.dll >nul
@zip -9 win32reg.zip win32reg-dbg.dll >nul
)
@popd

rm -rf %3zipeg.rc.rev
@for /F "usebackq delims==" %%a in (`bash.exe ../../get_svn_rev.sh`) do set WCREV=%%a
..\..\tools\envsub.exe %3zipeg.rc >%3zipeg.rc.rev
rc -i "../src" -l 0x409 /fo"%1" /d "%2" %3zipeg.rc.rev
