@for /F "usebackq delims==" %%a in (`bash.exe ../../../get_svn_rev.sh`) do set WCREV=%%a
..\..\..\tools\envsub.exe svn.rev.in >svn.rev
