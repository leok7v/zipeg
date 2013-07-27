@echo off
@set ver=2.9.5
@pushd ..\..
@for /F "usebackq delims==" %%a in (`bash.exe ./get_svn_rev.sh`) do set rev=%%a
@cd bin
@cp -f ../win32/src/sample.zip .
@cp -f zipeg-setup.exe zipeg-update.exe
@cp -f zipeg-setup.exe zipeg.%ver%.%rev%.exe
@mv zipeg-setup.exe zipeg.win.exe
@cp zipeg.win.exe ZipegForWindows.exe
@cp -f zipeg*.exe ../www/downloads/latest
@cp -f zipeg-update.exe ../www/downloads/latest
@popd