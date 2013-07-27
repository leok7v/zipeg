rem next three lines create certificate using c:\Program Files\Microsoft Platform SDK\Bin\
rem makecert -sv "zipeg.pvk" -n "CN=http://www.zipeg.com;O=Zipeg;E=support@zipeg.com" zipeg.cer
rem cert2spc zipeg.cer zipeg.spc
rem pvk2pfx -pvk zipeg.pvk -pi Pass1 -spc zipeg.spc -pfx zipeg.pfx -po Pass2 -f
..\..\tools\signtool.6.0.6000.16384.exe sign /f zipeg.pfx /p Pass2 /v %1
..\..\tools\signtool.6.0.6000.16384.exe timestamp /t http://timestamp.verisign.com/scripts/timstamp.dll %1
