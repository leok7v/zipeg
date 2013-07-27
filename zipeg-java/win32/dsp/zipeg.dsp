# Microsoft Developer Studio Project File - Name="zipeg" - Package Owner=<4>
# Microsoft Developer Studio Generated Build File, Format Version 5.00
# ** DO NOT EDIT **

# TARGTYPE "Win32 (x86) Application" 0x0101

CFG=zipeg - Win32 Debug
!MESSAGE This is not a valid makefile. To build this project using NMAKE,
!MESSAGE use the Export Makefile command and run
!MESSAGE 
!MESSAGE NMAKE /f "zipeg.mak".
!MESSAGE 
!MESSAGE You can specify a configuration when running NMAKE
!MESSAGE by defining the macro CFG on the command line. For example:
!MESSAGE 
!MESSAGE NMAKE /f "zipeg.mak" CFG="zipeg - Win32 Debug"
!MESSAGE 
!MESSAGE Possible choices for configuration are:
!MESSAGE 
!MESSAGE "zipeg - Win32 Release" (based on "Win32 (x86) Application")
!MESSAGE "zipeg - Win32 Debug" (based on "Win32 (x86) Application")
!MESSAGE 

# Begin Project
# PROP Scc_ProjName ""
# PROP Scc_LocalPath ""
CPP=cl.exe
MTL=midl.exe
RSC=rc.exe

!IF  "$(CFG)" == "zipeg - Win32 Release"

# PROP BASE Use_MFC 0
# PROP BASE Use_Debug_Libraries 0
# PROP BASE Output_Dir "Release"
# PROP BASE Intermediate_Dir "Release"
# PROP BASE Target_Dir ""
# PROP Use_MFC 0
# PROP Use_Debug_Libraries 0
# PROP Output_Dir "../tmp/Release"
# PROP Intermediate_Dir "../tmp/Release"
# PROP Ignore_Export_Lib 0
# PROP Target_Dir ""
# ADD BASE CPP /nologo /W3 /GX /O2 /D "WIN32" /D "NDEBUG" /D "_WINDOWS" /YX /FD /c
# ADD CPP /nologo /G6 /MD /W3 /O1 /I "../include" /I "../include/win32" /I "../zlib1.2.2" /D "NDEBUG" /D "WIN32" /D "_WINDOWS" /D "EXPAND_CLASSPATH_WILDCARDS" /D "JAVAW" /D _WIN32_WINNT=0x0501 /FR /YX /FD /c
# ADD BASE MTL /nologo /D "NDEBUG" /mktyplib203 /o NUL /win32
# ADD MTL /nologo /D "NDEBUG" /mktyplib203 /o NUL /win32
# ADD BASE RSC /l 0x409 /d "NDEBUG"
# ADD RSC /l 0x409 /d "NDEBUG"
BSC32=bscmake.exe
# ADD BASE BSC32 /nologo
# ADD BSC32 /nologo
LINK32=link.exe
# ADD BASE LINK32 kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib odbc32.lib odbccp32.lib /nologo /subsystem:windows /machine:I386
# ADD LINK32 kernel32.lib user32.lib gdi32.lib advapi32.lib comctl32.lib ole32.lib ../lib/shell32.lib ../lib/uuid.lib /nologo /subsystem:windows /pdb:none /machine:I386 /out:"../bin/zipeg-setup.exe" /opt:icf /opt:ref
# SUBTRACT LINK32 /debug
# Begin Special Build Tool
SOURCE=$(InputPath)
PostBuild_Cmds=call sign-code.bat
# End Special Build Tool

!ELSEIF  "$(CFG)" == "zipeg - Win32 Debug"

# PROP BASE Use_MFC 0
# PROP BASE Use_Debug_Libraries 1
# PROP BASE Output_Dir "Debug"
# PROP BASE Intermediate_Dir "Debug"
# PROP BASE Target_Dir ""
# PROP Use_MFC 0
# PROP Use_Debug_Libraries 1
# PROP Output_Dir "../tmp/Debug"
# PROP Intermediate_Dir "../tmp/Debug"
# PROP Ignore_Export_Lib 0
# PROP Target_Dir ""
# ADD BASE CPP /nologo /W3 /Gm /GX /Zi /Od /D "WIN32" /D "_DEBUG" /D "_WINDOWS" /YX /FD /c
# ADD CPP /nologo /MDd /W3 /GX /Z7 /Od /I "../include" /I "../include/win32" /I "../zlib1.2.2" /D "_DEBUG" /D "WIN32" /D "_WINDOWS" /D "EXPAND_CLASSPATH_WILDCARDS" /D "JAVAW" /D _WIN32_WINNT=0x0501 /FR /YX /FD /c
# ADD BASE MTL /nologo /D "_DEBUG" /mktyplib203 /o NUL /win32
# ADD MTL /nologo /D "_DEBUG" /mktyplib203 /o NUL /win32
# ADD BASE RSC /l 0x409 /d "_DEBUG"
# ADD RSC /l 0x409 /d "_DEBUG"
BSC32=bscmake.exe
# ADD BASE BSC32 /nologo
# ADD BSC32 /nologo
LINK32=link.exe
# ADD BASE LINK32 kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib odbc32.lib odbccp32.lib /nologo /subsystem:windows /debug /machine:I386 /pdbtype:sept
# ADD LINK32 kernel32.lib user32.lib gdi32.lib advapi32.lib comctl32.lib ole32.lib ../lib/shell32.lib ../lib/uuid.lib /nologo /subsystem:windows /pdb:none /debug /machine:I386 /out:"../bin/zipeg-dbg-setup.exe"
# SUBTRACT LINK32 /verbose /nodefaultlib
# Begin Special Build Tool
SOURCE=$(InputPath)
PostBuild_Cmds=call sign-code.bat
# End Special Build Tool

!ENDIF 

# Begin Target

# Name "zipeg - Win32 Release"
# Name "zipeg - Win32 Debug"
# Begin Group "zlib1.2.2"

# PROP Default_Filter ""
# Begin Source File

SOURCE=..\zlib1.2.2\adler32.c
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\compress.c
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\crc32.c
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\crc32.h
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\deflate.c
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\deflate.h
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\gzio.c
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\infback.c
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\inffast.c
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\inffast.h
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\inffixed.h
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\inflate.c
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\inflate.h
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\inftrees.c
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\inftrees.h
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\trees.c
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\trees.h
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\uncompr.c
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\zconf.h
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\zlib.h
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\zutil.c
# End Source File
# Begin Source File

SOURCE=..\zlib1.2.2\zutil.h
# End Source File
# End Group
# Begin Group "src"

# PROP Default_Filter ""
# Begin Source File

SOURCE=..\src\installer.cpp
# End Source File
# Begin Source File

SOURCE=..\src\java.c
# End Source File
# Begin Source File

SOURCE=..\src\java.h
# End Source File
# Begin Source File

SOURCE=..\src\java_md.c
# End Source File
# Begin Source File

SOURCE=..\src\java_md.h
# End Source File
# Begin Source File

SOURCE=..\src\jli_util.c
# End Source File
# Begin Source File

SOURCE=..\src\jli_util.h
# End Source File
# Begin Source File

SOURCE=..\src\manifest_info.h
# End Source File
# Begin Source File

SOURCE=..\src\parse_manifest.c
# End Source File
# Begin Source File

SOURCE=..\src\resource.h
# End Source File
# Begin Source File

SOURCE=..\src\splashscreen.h
# End Source File
# Begin Source File

SOURCE=..\src\splashscreen_stubs.c
# End Source File
# Begin Source File

SOURCE=..\src\version_comp.c
# End Source File
# Begin Source File

SOURCE=..\src\version_comp.h
# End Source File
# Begin Source File

SOURCE=..\src\wildcard.c
# End Source File
# Begin Source File

SOURCE=..\src\wildcard.h
# End Source File
# End Group
# Begin Group "res"

# PROP Default_Filter ""
# Begin Source File

SOURCE="..\7za-win-i386.zip"
# End Source File
# Begin Source File

SOURCE=..\src\error.ico
# End Source File
# Begin Source File

SOURCE=..\src\getjava.bmp
# End Source File
# Begin Source File

SOURCE="..\jre-6-windows-i586-iftw.zip"
# End Source File
# Begin Source File

SOURCE=..\win32reg.zip
# End Source File
# Begin Source File

SOURCE=..\src\zipeg.ico
# End Source File
# Begin Source File

SOURCE=..\src\zipeg.rc

!IF  "$(CFG)" == "zipeg - Win32 Release"

# PROP Ignore_Default_Tool 1
# Begin Custom Build
OutDir=.\../tmp/Release
InputPath=..\src\zipeg.rc

"$(OutDir)/zipeg.res" : $(SOURCE) "$(INTDIR)" "$(OUTDIR)"
	compile-rc.bat $(OutDir)/zipeg.res _DEBUG $(InputPath)

# End Custom Build

!ELSEIF  "$(CFG)" == "zipeg - Win32 Debug"

# PROP Ignore_Default_Tool 1
# Begin Custom Build - compile resources
OutDir=.\../tmp/Debug
InputPath=..\src\zipeg.rc

"$(OutDir)/zipeg.res" : $(SOURCE) "$(INTDIR)" "$(OUTDIR)"
	compile-rc.bat $(OutDir)/zipeg.res _DEBUG $(InputPath)

# End Custom Build

!ENDIF 

# End Source File
# Begin Source File

SOURCE=..\src\zipeg.vista.ico
# End Source File
# Begin Source File

SOURCE=..\src\zipeg.xp.ico
# End Source File
# Begin Source File

SOURCE=..\zipeg.zip
# End Source File
# End Group
# Begin Source File

SOURCE=".\compile-rc.bat"
# End Source File
# Begin Source File

SOURCE=".\sign-code.bat"
# End Source File
# End Target
# End Project
