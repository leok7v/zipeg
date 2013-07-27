@echo off
pushd ..\..\classes
rm -rf zipeg.jar
rm -rf zipeg.zip
"c:\Program Files\Java\jdk1.6.0\bin\jar.exe" -cvfe0 zipeg.jar com.zipeg.Zipeg . >nul
zip -9 zipeg.zip zipeg.jar >nul
rm -rf ..\bin\zipeg.zip
mv zipeg.zip ..\bin
rm zipeg.jar
cd ..\bin
rm -rf 7za-win-i386.zip
rm -rf win32reg.zip
zip -9 7za-win-i386.zip 7za-win-i386.dll >nul
zip -9 win32reg.zip win32reg.dll >nul
popd
