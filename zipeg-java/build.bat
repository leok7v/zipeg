@echo off
@set JAVA_HOME=C:\Program Files\Java\jdk1.5.0_19
@svn cleanup
@call clean.bat
@svn up
@if "%DevEnvDir%" == "" call "C:\Program Files\Microsoft Visual Studio 9.0\VC\vcvarsall.bat"
@set SavedPath=%Path%
@set Path="%JAVA_HOME%\bin";%Path%
@msbuild all.sln /p:Configuration=Release
@if exist "C:\Program Files\Common Files\Microsoft Shared\CAPICOM\capicom.dll" goto :capicom_ok
@echo download and install http://www.microsoft.com/en-us/download/details.aspx?id=25281
@goto :error
@rem mkdir "C:\Program Files\Common Files\Microsoft Shared\CAPICOM"
@rem cp tools/capicom.dll "C:/Program Files/Common Files/Microsoft Shared/CAPICOM/"
@:capicom_ok
@rem regsvr32 /s "C:\Program Files\Common Files\Microsoft Shared\CAPICOM\capicom.dll"
@if %errorlevel% neq 0 goto :error
@msbuild all.sln /p:Configuration=Debug
@if %errorlevel% neq 0 goto :error
@echo *** succeeded
@goto :end
@:error
@echo *** failed
@:end
@set Path=%SavedPath%
@set SavedPath

