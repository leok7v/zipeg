echo on
svn up ../../..
rm -rf %3win32reg.rc.rev
@for /F "usebackq delims==" %%a in (`bash.exe ../../../get_svn_rev.sh`) do set WCREV=%%a
..\..\..\tools\envsub.exe %3win32reg.rc >%3win32reg.rc.rev
rc -i "../src" -l 0x409 /fo"%1" /d "%2" %3win32reg.rc.rev
