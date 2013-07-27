call "C:\Program Files\Microsoft Visual Studio 9.0\VC\bin\vcvars32.bat"
cl envsub.cpp
rm envsub.obj
mv envsub.exe ..
cat test.txt
echo *** envsub.exe test.txt ***
..\envsub.exe test.txt
