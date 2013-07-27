#pragma warning(disable :4615) // unknown user warning type
#pragma warning(disable :4018) // signed/unsigned mismatch
#pragma warning(disable :4146) // unary minus operator applied to unsigned type, result still unsigned
#pragma warning(disable :4996) // This function or variable may be unsafe

#include <Windows.h>
#include <shlobj.h>
#include <time.h>
#include <psapi.h>
#include "resource.h"

#define null NULL
const char license[] = "Copyright (c) 2006-2012, Leo Kuznetsov, www.zipeg.com\r\n"
"All rights reserved.\r\n\r\n"
"Redistribution and use in any forms, without modification, is permitted provided that the following conditions are met:\r\n\r\n"
"Redistributions must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.\r\n\r\n"
"Neither the name of the www.zipeg.com nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.\r\n\r\n"
"THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.";

extern "C" {

void trace(const char * fmt, ...);
int IsDirectory(const char * dir);
int FileExists(const char * file);
void* ReadFileIntoMemory(const char * filename, int * size);
int WriteMemoryToFile(const char * filename, void * p, int size);
int CopyFileFromResource(const char* filename, int id, const char* type);
void * JLI_JarUnpackFile(const char *jarfile, const char *filename, int *size);

char* getSpecialFolderLocation(int location) {
    char buf[MAX_PATH];
    LPITEMIDLIST idl = NULL;
    HRESULT hRes = SHGetSpecialFolderLocation(NULL, location, &idl);
    SHGetPathFromIDListA(idl, buf);
    return strdup(buf);
}

char* getSystemDirectory() {
    char buf[MAX_PATH];
    GetSystemDirectory(buf, MAX_PATH);
    return strdup(buf);
}

}

static bool install_local = true;

static
char* getInstallationFolder() {
//  Google Chrome installs to:
//  C:\Documents and Settings\<user>\Local Settings\Application Data\Google\Chrome\Application
//  Zipeg will install into:
//  C:\Documents and Settings\<user>\Local Settings\Application Data\Zipeg
//  char* programfiles = getSpecialFolderLocation(CSIDL_PROGRAM_FILES);
    return getSpecialFolderLocation(CSIDL_LOCAL_APPDATA);
}

static
DWORD* getProcessesIds(int &n) {
    DWORD processIds0[16*1024] = {0};
    DWORD bytes = 0;
    EnumProcesses(processIds0, sizeof(processIds0), &bytes);
    n = bytes / 4;
    DWORD* processIds = new DWORD[n*2]; // for sudden burst of number of processes
    EnumProcesses(processIds, bytes, &bytes);
    n = bytes / 4;
    return processIds;
}

static
HMODULE* getModules(HANDLE process, int &n) {
    HMODULE modules0[1] = {0};
    DWORD bytes = 0;
    EnumProcessModules(process, modules0, sizeof(modules0), &bytes);
    n = bytes / sizeof(HMODULE);
    HMODULE* modules = new HMODULE[n*2]; // for sudden burst of number of modules
    EnumProcessModules(process, modules, bytes, &bytes);
    n = bytes / sizeof(HMODULE);
    return modules;
}

static
int killProcess(const wchar_t* name) {
    int count = 0;
    int namelen = wcslen(name);
    int n = 0;
    DWORD* processIds = getProcessesIds(n);
    for (int i = 0; i < n; i++) {
        if (GetCurrentProcessId() == processIds[i]) {
            continue;
        }
        HANDLE process = OpenProcess(PROCESS_ALL_ACCESS, false, processIds[i]);
        if (process == null) {
            continue;
        }
        int m = 0;
        HMODULE* modules = getModules(process, m);
        for (int k = 0; k < m; k++) {
            wchar_t path[1024] = {0};
            GetModuleFileNameExW(process, modules[k], path, sizeof(path));
            int len = wcslen(path);
            if (len > namelen && path[len - namelen - 1] == L'\\' && wcsicmp(&path[len - namelen], name) == 0) {
                if (::TerminateProcess(process, 0x1)) {
                    count++;
                }
            }
        }
        delete [] modules;
        CloseHandle(process);
    }
    delete [] processIds;
    return count;
}

static void killAll() {
    while (killProcess(L"zipeg.exe") > 0) {
        Sleep(1000);
    }
}

static
void deleteTree(const char* dir) {
    char path[16*1024];
    strcpy(path, dir);
    strcat(path, "\\*");
    WIN32_FIND_DATA fd = {0};
    HANDLE h = FindFirstFile(path, &fd);
    if (h == INVALID_HANDLE_VALUE) {
        return;
    }
    for (;;) {
        if (strcmp(fd.cFileName, ".") == 0 || strcmp(fd.cFileName, "..") == 0) {
            // skip
        } else {
            strcpy(path, dir);
            strcat(path, "\\");
            strcat(path, fd.cFileName);
            if (IsDirectory(path)) {
                deleteTree(path);
            } else {
                DeleteFile(path);
            }
        }
        if (!FindNextFile(h, &fd)) {
            break;
        }
    }
    FindClose(h);
    RemoveDirectory(dir);
}

static
void removeTree(const char* dir) {
    deleteTree(dir);
    Sleep(1000); // looks like there is racing in NTSF
}

static char version[128];

static
const char* getVersion() {
    char file_name[MAX_PATH];
    GetModuleFileName(GetModuleHandle(null), file_name, MAX_PATH);
    DWORD dwDummyHandle = 0;
    DWORD len = GetFileVersionInfoSize(file_name, &dwDummyHandle);
    byte* buf = new byte[len];
    GetFileVersionInfo(file_name, 0, len, buf);
    unsigned int ver_length;
    void* lpvi;
    ::VerQueryValue(buf, "\\", &lpvi, &ver_length );
    VS_FIXEDFILEINFO fileInfo;
    fileInfo = *(VS_FIXEDFILEINFO*)lpvi;
    wsprintf(version, "%d.%d.%d.%d",
        HIWORD(fileInfo.dwFileVersionMS),
        LOWORD(fileInfo.dwFileVersionMS),
        HIWORD(fileInfo.dwFileVersionLS),
        LOWORD(fileInfo.dwFileVersionLS));
    return version;
}

static
bool insertShortcut(const char* folder,
                    const char* dir, const char* path,
                    const char* label, const char* description) {
    IShellLink* psl = null;
    // Get a pointer to the IShellLink interface
    HRESULT hRes = ::CoCreateInstance(CLSID_ShellLink, null, CLSCTX_INPROC_SERVER,
                                      IID_IShellLink, (void**)&psl);
    if (FAILED(hRes)) {
        trace("insertShortcut() cannot create link\n");
        return false;
    }
    hRes = psl->SetPath(path);
    hRes = psl->SetIconLocation(path, 0);
    hRes = psl->SetDescription(description);
    hRes = psl->SetWorkingDirectory(dir);
    // Query IShellLink for the IPersistFile interface for saving
    // the shortcut in persistent storage.
    IPersistFile* ppf = null;
    hRes = psl->QueryInterface(IID_IPersistFile, (void**)&ppf);
    if (FAILED(hRes))  {
        psl->Release();
        trace("insertShortcut() cannot get IPersistFile interface");
        return false;
    }
/*
    char link[MAX_PATH + 1024] = {0};
    strcat(link, folder);
    strcat(link, "\\");
    strcat(link, label);
    strcat(link, ".lnk");
    wchar_t wsz[MAX_PATH+1024] = {0};
    MultiByteToWideChar(CP_ACP, 0, link, -1, wsz, MAX_PATH);
*/
    char link[MAX_PATH + 1024] = {0};
    strcat(link, path);
    char* last = strrchr(link, '\\');
    *last = 0;
    strcat(last, "\\");
    strcat(link, label);
    strcat(link, ".lnk");
    wchar_t wsz[MAX_PATH+1024] = {0};
    MultiByteToWideChar(CP_ACP, 0, link, -1, wsz, MAX_PATH);
    hRes = ppf->Save((LPCWSTR)wsz, FALSE);
    ppf->Release();
    psl->Release();
    if (FAILED(hRes)) {
        trace("insertShortcut(\"%s\", \"%s\") cannot save link\n", folder, link);
        return false;
    }
    char dest[MAX_PATH + 1024] = {0};
    strcat(dest, folder);
    strcat(dest, "\\");
    strcat(dest, label);
    strcat(dest, ".lnk");
    bool b = MoveFileEx(link, dest,
        MOVEFILE_REPLACE_EXISTING|MOVEFILE_COPY_ALLOWED) != false;
    if (!b) {
        trace("Error: failed to MoveFile(%s, %s)\n", link, dest);
    }
    return b;
}

static
bool insertShortcut(int common_csidl,
                    int csidl,
                    const char* dest, const char* path,
                    const char* label, const char* desc) {
    bool b = false;
    char* folder = null;
    if (!install_local) {
        folder = getSpecialFolderLocation(common_csidl);
        if (folder != null) {
            b = insertShortcut(folder, dest, path, label, desc);
            if (!b) {
                trace("failed common insertShortcut: %s\n", folder);
            }
        }
    } else {
        folder = getSpecialFolderLocation(csidl);
        if (folder != null) {
            b = insertShortcut(folder, dest, path, label, desc);
            if (!b) {
                trace("failed local insertShortcut: %s\n", folder);
            }
        }
    }
    return b;
}

bool insertShortcuts(const char *dest, const char* exe,
                     const char* label, const char* desc) {
    bool b = true;
    b = insertShortcut(CSIDL_COMMON_PROGRAMS, CSIDL_PROGRAMS, dest, exe, label, desc) && b;
    b = insertShortcut(CSIDL_COMMON_DESKTOPDIRECTORY, CSIDL_DESKTOP, dest, exe, label, desc) && b;
    char* appdata = getSpecialFolderLocation(CSIDL_APPDATA);
    if (appdata != null) {
        char quicklaunch[MAX_PATH+1024] = {0};
        strcat(quicklaunch, appdata);
        strcat(quicklaunch, "\\Microsoft\\Internet Explorer\\Quick Launch");
        if (IsDirectory(quicklaunch)) {
            b = insertShortcut(quicklaunch, dest, exe, label, desc) && b;
            if (!b) {
                trace("Quick Launch failed insertShortcut: %s\n", quicklaunch);
            }
        }
    }
    return b;
}

static
bool removeShortcut(const char* folder,
                    const char* label) {
    char link[MAX_PATH + 1024] = {0};
    strcat(link, folder);
    strcat(link, "\\");
    strcat(link, label);
    strcat(link, ".lnk");
    return DeleteFile(link) != 0;
}

static
bool removeShortcut(int common_csidl,
                    int csidl,
                    const char* label) {
    bool b = false;
    char* folder = null;
    if (!install_local) {
        folder = getSpecialFolderLocation(common_csidl);
        if (folder != null) {
            b = removeShortcut(folder, label);
        }
    } else {
        folder = getSpecialFolderLocation(csidl);
        if (folder != null) {
            b = removeShortcut(folder, label);
        }
    }
    return b;
}

static
bool removeShortcuts(const char* label) {
    bool b = true;
    b = removeShortcut(CSIDL_COMMON_PROGRAMS, CSIDL_PROGRAMS, label) && b;
    b = removeShortcut(CSIDL_COMMON_DESKTOPDIRECTORY, CSIDL_DESKTOP, label) && b;
    char* appdata = getSpecialFolderLocation(CSIDL_APPDATA);
    if (appdata != null) {
        char quicklaunch[MAX_PATH+1024] = {0};
        strcat(quicklaunch, appdata);
        strcat(quicklaunch, "\\Microsoft\\Internet Explorer\\Quick Launch");
        if (IsDirectory(quicklaunch)) {
            b = removeShortcut(quicklaunch, label) && b;
            if (!b) {
                trace("failed quick launch insertShortcut: %s\n", quicklaunch);
            }
        }
    }
    return b;
}

static
void removeUninstall(boolean local) {
    HKEY key = null;
    if (RegCreateKey(local ? HKEY_CURRENT_USER: HKEY_LOCAL_MACHINE,
        "Software\\Microsoft\\Windows\\"
        "CurrentVersion\\Uninstall", &key) != 0) {
        trace("Error: failed to open uninstall key\n");
        return;
    }
    HKEY zipeg = null;
    int r = RegOpenKeyEx(key, "Zipeg", 0, KEY_ALL_ACCESS, &zipeg);
    if (r == 0 && zipeg != null) {
        RegDeleteValue(zipeg, "DisplayName");
        RegDeleteValue(zipeg, "UninstallString");
        RegCloseKey(zipeg);
    }
    if (RegDeleteKey(key, "Zipeg") != 0) {
        trace("Error deleting uninstall key Zipeg\n");
    }
    RegCloseKey(key);
}

static bool copyFile(const char* from, const char* to) {
    int size;
    void * p = ReadFileIntoMemory(from, &size);
    if (p == null) {
        return false;
    } else {
        bool b = WriteMemoryToFile(to, p, size) != 0;
        free(p);
        return b;
    }
}

static bool unzipFile(const char * zipped, const char * unzipped, int id, const char* unpack) {
    char dest[MAX_PATH + 1024] = {0};
    strcat(dest, zipped);
    strrchr(dest, '\\')[1] = 0;
    strcat(strrchr(dest, '\\'), unpack);
    DeleteFile(zipped);
    DeleteFile(dest);
    if (!CopyFileFromResource(zipped, id, RT_RCDATA)) {
        return false;
    }
    int size = 0;
    void * buf = JLI_JarUnpackFile(zipped, unzipped, &size);
    if (buf == NULL || size <= 0) {
        DeleteFile(zipped);
        return false;
    }
    if (!WriteMemoryToFile(dest, buf, size)) {
        DeleteFile(zipped);
        DeleteFile(dest);
        return false;
    }
    DeleteFile(zipped);
    return true;
}


static
bool runProcess(const char* exe, const char* args, bool wait) {
    STARTUPINFO si = {sizeof(STARTUPINFO)};
    PROCESS_INFORMATION pi = {0};
    if (!CreateProcessA(exe,    /* executable name */
        (LPSTR)args,
        (LPSECURITY_ATTRIBUTES)NULL,            /* process security attr. */
        (LPSECURITY_ATTRIBUTES)NULL,            /* thread security attr. */
        (BOOL)FALSE,                            /* inherits system handles */
        (DWORD)0,                               /* creation flags */
        (LPVOID)NULL,                           /* environment block */
        (LPCTSTR)NULL,                          /* current directory */
        (LPSTARTUPINFO)&si,                     /* (in) startup information */
        (LPPROCESS_INFORMATION)&pi)) {          /* (out) process information */
        trace("CreateProcess(%s, ...) failed: %d\n", exe, GetLastError());
        return false;
    }
    if (wait) {
        WaitForSingleObject(pi.hProcess, 20*1000);
    }
    CloseHandle(pi.hProcess);
    return true;
}

static void registerZipFolders(bool reg) {
    // see: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6372808 (search for "regsvr32")
    // "regsvr32 -s -u %windir%\system32\zipfldr.dll"
    char* sys = getSystemDirectory();
    char regsvr32[MAX_PATH] = {0};
    strcat(regsvr32, sys);
    strcat(regsvr32, "\\regsvr32.exe");
    char zipfldr[MAX_PATH] = {0};
    strcat(zipfldr, " -s ");
    if (!reg) {
        strcat(zipfldr, " -u ");
    }
    strcat(zipfldr, sys);
    strcat(zipfldr, "\\zipfldr.dll");
    runProcess(regsvr32, zipfldr, true);
}

static void registerZipFoldersSendTo() {
    char* sys = getSystemDirectory();
    // rundll32 zipfldr.dll,RegisterSendto
    char rundll32[MAX_PATH] = {0};
    strcat(rundll32, sys);
    strcat(rundll32, "\\rundll32.exe");
    runProcess(rundll32, "zipfldr.dll,RegisterSendto", true);
}

static HWND wnd;
static HWND pb;

static
bool doInstall(const char *filename) {
    char* label = "Zipeg";
    char* programfiles = getInstallationFolder();
    killAll();
//  trace("doInstall %s\n", programfiles);
    if (programfiles == null) {
        trace("failed to open Program Files\n");
        return false;
    }
    char dest[MAX_PATH + 1024] = {0};
    strcat(dest, programfiles);
    strcat(dest, "\\Zipeg");
    removeTree(dest);
    if (!IsDirectory(dest) && !CreateDirectory(dest, null)) {
        trace("Error: failed to create directory %s\n", dest);
        return false;
    }
    strcat(dest, "\\Application");
    if (!IsDirectory(dest) && !CreateDirectory(dest, null)) {
        trace("failed to create directory %s\n", dest);
        return false;
    }
    char exe[MAX_PATH + 1024] = {0};
    strcat(exe, dest);
    strcat(exe, "\\zipeg.exe");
    if (!copyFile(filename, exe)) {
        trace("failed to copy executable\n");
        return false;
    }
    char zipped[MAX_PATH + 1024] = {0};
    strcat(zipped, dest);
    strcat(zipped, "\\zipeg.zip");
    if (!unzipFile(zipped, "zipeg.jar", idr_zipeg, "zipeg.jar")) {
        trace("failed to copy zipeg.jar\n");
        return false;
    }
    char sample[MAX_PATH + 1024] = {0};
    strcat(sample, dest);
    strcat(sample, "\\sample.zip");
    if (!CopyFileFromResource(sample, idr_sample, RT_RCDATA)) {
        trace("failed to copy sample.zip\n");
        return false;
    }
    char splash[MAX_PATH + 1024] = {0};
    strcat(splash, dest);
    strcat(splash, "\\win.splash.gif");
    if (!CopyFileFromResource(splash, idr_splash, RT_RCDATA)) {
        trace("failed to copy win.splash.gif\n");
        return false;
    }
    zipped[0] = 0;
    strcat(zipped, dest);
    strcat(zipped, "\\7za-win-i386.zip");
    const char* z7a_dll =
#ifdef _DEBUG
    "7za-win-i386-dbg.dll";
#else
    "7za-win-i386.dll";
#endif
    if (!unzipFile(zipped, z7a_dll, idr_z7a, "7za-win-i386.dll")) {
        trace("failed to unzip %s\n", z7a_dll);
        return false;
    }
    zipped[0] = 0;
    strcat(zipped, dest);
    strcat(zipped, "\\win32reg.zip");
    const char* reg_dll =
#ifdef _DEBUG
    "win32reg-dbg.dll";
#else
    "win32reg.dll";
#endif
    if (!unzipFile(zipped, reg_dll, idr_win32reg, "win32reg.dll")) {
        trace("failed to unzip %\n", reg_dll);
        return false;
    }
    {
        bool saved = install_local;
        install_local = false;
        removeShortcuts("Zipeg"); // remove old shortcuts
        install_local = saved;
        removeShortcuts("Zipeg"); // remove old shortcuts
    }
    removeUninstall(true); // remove old uninstall
    removeUninstall(false); // remove old uninstall
    insertShortcuts(dest, exe, label, "Zipeg");
    HKEY key;
    if (RegCreateKey(install_local ? HKEY_CURRENT_USER: HKEY_LOCAL_MACHINE,
        "Software\\Microsoft\\Windows\\"
        "CurrentVersion\\Uninstall\\Zipeg", &key) != 0) {
        trace("Error: failed to open uninstall key\n");
        return false;
    }
    char uninstall[MAX_PATH + 1024] = {0};
    strcat(uninstall, "\"");
    strcat(uninstall, exe);
    strcat(uninstall, "\"");
    strcat(uninstall, " -uninstall");
    if (RegSetValueEx(key, "UninstallString", null, REG_SZ,
        (BYTE*)uninstall, strlen(uninstall) + 1) != 0) {
        trace("Error setting UninstallString\n");
        RegCloseKey(key);
        return false;
    }
    if (RegSetValueEx(key, "DisplayName", null, REG_SZ,
        (BYTE*)label, strlen(label) + 1) != 0) {
        trace("Error setting UninstallString\n");
        RegCloseKey(key);
        return false;
    }
    if (RegSetValueEx(key, "Version", null, REG_SZ,
        (BYTE*)getVersion(), strlen(getVersion()) + 1) != 0) {
        trace("Error setting UninstallString Version\n");
        RegCloseKey(key);
        return false;
    }
    if (RegSetValueEx(key, "DisplayVersion", null, REG_SZ,
        (BYTE*)getVersion(), strlen(getVersion()) + 1) != 0) {
        trace("Error setting UninstallString DisplayVersion\n");
        RegCloseKey(key);
        return false;
    }
    time_t t = 0;
    t = time(&t);
    struct tm* now = localtime(&t);
    char timebuf[128] = {0};
    wsprintf(timebuf, "%04d%02d%02d", 1900 + now->tm_year, now->tm_mon + 1, now->tm_mday);
    if (RegSetValueEx(key, "InstallDate", null, REG_SZ,
        (BYTE*)timebuf, strlen(timebuf) + 1) != 0) {
        trace("Error setting UninstallString InstallDate\n");
        RegCloseKey(key);
        return false;
    }
    if (RegSetValueEx(key, "InstallLocation", null, REG_SZ,
        (BYTE*)dest, strlen(dest) + 1) != 0) {
        trace("Error setting UninstallString InstallLocation\n");
        RegCloseKey(key);
        return false;
    }
    if (RegSetValueEx(key, "DisplayIcon", null, REG_SZ,
        (BYTE*)exe, strlen(exe) + 1) != 0) {
        trace("Error setting UninstallString DisplayIcon\n");
        RegCloseKey(key);
        return false;
    }
    int one = 1;
    if (RegSetValueEx(key, "NoModify", null, REG_DWORD,
        (BYTE*)&one, sizeof(one)) != 0) {
        trace("Error setting UninstallString NoModify\n");
        RegCloseKey(key);
        return false;
    }
    if (RegSetValueEx(key, "NoRepair", null, REG_DWORD,
        (BYTE*)&one, sizeof(one)) != 0) {
        trace("Error setting UninstallString NoRepair\n");
        RegCloseKey(key);
        return false;
    }
    const char* publisher = "http://zipeg.com";
    if (RegSetValueEx(key, "Publisher", null, REG_SZ,
        (BYTE*)publisher, strlen(publisher) + 1) != 0) {
        trace("Error setting UninstallString Publisher\n");
        RegCloseKey(key);
        return false;
    }
    RegCloseKey(key);

    registerZipFolders(true); // because Shell SendTo Compressed Folders has to work
    registerZipFoldersSendTo();

    STARTUPINFO si = {sizeof(STARTUPINFO)};
    PROCESS_INFORMATION pi = {0};
    char run[MAX_PATH] = {0};
    strcpy(run, "\"");
    strcat(run, exe);
    strcat(run, "\" ");
    strcat(run, "--first-run");
    BOOL b = CreateProcessA(exe,    /* executable name */
        run,
        (LPSECURITY_ATTRIBUTES)NULL,            /* process security attr. */
        (LPSECURITY_ATTRIBUTES)NULL,            /* thread security attr. */
        (BOOL)FALSE,                            /* inherits system handles */
        (DWORD)0,                               /* creation flags */
        (LPVOID)NULL,                           /* environment block */
        (LPCTSTR)dest,                          /* current directory */
        (LPSTARTUPINFO)&si,                     /* (in) startup information */
        (LPPROCESS_INFORMATION)&pi);            /* (out) process information */
    //HINSTANCE instance = ShellExecute(NULL, "open", exe, "--first-run", NULL, SW_SHOW);
    if (b) {
        MSG msg = {0};
        int when = GetTickCount() + 3 * 1000;
        DWORD r = 0xFF;
        while (GetMessage(&msg, NULL, 0, 0) && GetTickCount() < when) {
            if (!IsDialogMessage(wnd, &msg)) {
                DispatchMessage(&msg);
                if (r != 0) {
                    r = WaitForInputIdle(pi.hProcess, 10);
                    if (r == 0) {
                        when = GetTickCount() + 1 * 1000;
                    }
                }
            }
        }
    } else {
        trace("CreateProcess(%s, %s) failed: %d\n", exe, run, GetLastError());
    }
    SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, null, null);
    SHChangeNotify(SHCNE_ALLEVENTS, null, null, null);
    return b != 0;
}

static
void appendVersion(HWND hwnd) {
    char buf[128];
    GetWindowText(hwnd, buf, sizeof(buf));
    strcat(buf, " ");
    strcat(buf, getVersion());
    SetWindowText(hwnd, buf);
}

static
int CALLBACK dlgProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
        case WM_INITDIALOG: {
            SendMessage(hwnd, WM_SETICON, ICON_BIG, (LPARAM)LoadIcon(GetModuleHandle(NULL),
                              MAKEINTRESOURCE(idi_zipeg)));
            SetWindowText(GetDlgItem(hwnd, idc_edit), license);
            SendMessage(GetDlgItem(hwnd, idc_edit), EM_SETREADONLY, 1, 0);
            SendMessage(GetDlgItem(hwnd, idc_edit), EM_SETSEL, 0, 0);
            SetFocus(GetDlgItem(hwnd, IDNO));
            appendVersion(hwnd);
            return FALSE;
        }
        case WM_CTLCOLOREDIT: {
            SetTextColor((HDC)wp, 0);
            SetBkColor((HDC)wp, 0xFFFFFF);
            return (int)GetStockObject(WHITE_BRUSH);
        }
        case WM_CTLCOLORSTATIC: {
            if ((HWND)lp == GetDlgItem(hwnd, idc_edit)) {
                SetTextColor((HDC)wp, 0);
                SetBkColor((HDC)wp, 0xFFFFFF);
                return (int)GetStockObject(WHITE_BRUSH);
            }
        }
        case WM_COMMAND:
            if (LOWORD(wp) == IDYES || LOWORD(wp) == IDNO) {
                EndDialog(hwnd, LOWORD(wp));
            }
            break;
        case WM_CLOSE:
            EndDialog(hwnd, IDNO);
            break;
        default:
            break;
    }
    return FALSE;
}

static
int CALLBACK dlgProcFailed(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
        case WM_INITDIALOG: {
            SendMessage(hwnd, WM_SETICON, ICON_BIG, (LPARAM)LoadIcon(GetModuleHandle(NULL),
                              MAKEINTRESOURCE(idi_zipeg)));
            appendVersion(hwnd);
            return TRUE;
        }
        case WM_COMMAND:
            EndDialog(hwnd, IDOK);
            break;
        case WM_CLOSE:
            EndDialog(hwnd, IDCANCEL);
            break;
        default:
            break;
    }
    return FALSE;
}

static
void showLicense() {
    char exename[MAX_PATH];
    GetModuleFileName(GetModuleHandle(null), exename, MAX_PATH);
    if (strstr(exename, "update") != null) {
        return;
    }
    int res = DialogBox(GetModuleHandle(NULL), MAKEINTRESOURCE(idd_license), NULL, dlgProc);
    if (res != IDYES) {
        ExitProcess(1);
    }
}

extern "C" {

INT_PTR CALLBACK dlgProcProgress(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
        case WM_INITDIALOG:
            SendMessage(hwnd, WM_SETICON, ICON_BIG, (LPARAM)LoadIcon(GetModuleHandle(NULL),
                              MAKEINTRESOURCE(idi_zipeg)));
            appendVersion(hwnd);
            return TRUE;
        case WM_TIMER: {
            HWND pb = GetDlgItem(wnd, idc_progress);
            SendMessage(pb, PBM_STEPIT, 0, 0);
            InvalidateRect(hwnd, NULL, FALSE);
        }
    }
    return 0;
}

void install(const char *filename) {
    Sleep(1000);
    killAll();
    showLicense();
    wnd = CreateDialog(GetModuleHandle(NULL),
                       MAKEINTRESOURCE(idd_progress),
                       NULL,
                       dlgProcProgress);
    HWND pb = GetDlgItem(wnd, idc_progress);
    SetWindowLong(pb, GWL_STYLE, GetWindowLong(pb, GWL_STYLE) | PBS_MARQUEE);
    SendMessage(pb, PBM_SETMARQUEE, 1, 10);
    SendMessage(pb, PBM_SETRANGE, 0, MAKELPARAM(0, 20));
    SendMessage(pb, PBM_SETSTEP, 1, 0);
    SendMessage(pb, PBM_STEPIT, 0, 0);
    SendMessage(pb, PBM_STEPIT, 0, 0);
    ShowWindow(wnd, SW_SHOW);
    UpdateWindow(wnd);
    SetTimer(wnd, 0xFFFF, 50, NULL);
    if (!doInstall(filename)) {
        DialogBox(GetModuleHandle(NULL), MAKEINTRESOURCE(idd_failed), NULL, dlgProcFailed);
    }
    DestroyWindow(wnd);
    CoUninitialize();
}

static void deleteValue(HKEY root, const char* path, const char* name) {
    HKEY key = null;
    int r = RegOpenKeyEx(root, path, 0, KEY_ALL_ACCESS, &key);
    if (r == 0) {
        r = RegDeleteValue(key, name);
        if (r != 0) {
//          trace("deletedValue::RegDeleteValue(\"%s\") %d\n", name, r);
        }
        r = RegCloseKey(key);
        if (r != 0) {
            trace("deletedKey::RegCloseKey %d\n", r);
        }
    }
}

static void deleteKey(HKEY root, const char* path) {
    HKEY key = null;
    int r = RegOpenKeyEx(root, path, 0, KEY_ALL_ACCESS, &key);
    if (r == 0) {
        for (int i = 0; ; i++) {
            FILETIME ft = {0};
            char name[1024] = {0};
            char cls[1024] = {0};
            unsigned long len = sizeof(name);
            unsigned long size = sizeof(cls);
            r = RegEnumKeyEx(key, i, name, &len, null, cls, &size, &ft);
            if (r != 0) {
                break;
            }
            r = RegDeleteValue(key, name);
//          trace("deletedKey::RegDeleteValue %s %d\n", name, r);
            if (r != 0) {
                deleteKey(key, name);
            }
        }
        r = RegDeleteValue(key, ""); // default value
//      trace("deletedKey::RegDeleteValue %s %d -- default\n", path, r);
        r = RegDeleteValue(root, path); // default value
//      trace("deletedKey::RegDeleteValue %s %d -- default\n", path, r);
        r = RegCloseKey(key);
        if (r != 0) {
            trace("deletedKey::RegCloseKey %d\n", r);
        }
        r = RegDeleteKey(root, path);
//      trace("deleted %s %d\n", path, r);
    }
}

static void restoreCompressedFolder(HKEY root, const char* path) {
    HKEY key = null;
    int r = RegOpenKeyEx(root, path, 0, KEY_ALL_ACCESS, &key);
    if (r == 0) {
        DWORD rtype = 0;
        char value[1024] = {0};
        DWORD len = sizeof(value);
        long r = ::RegQueryValueEx(key, "", 0, &rtype, (byte*)value, &len);
        if (r == 0 && strstr(value, "Zipeg") != null) {
            r = ::RegSetValueEx(key, "", 0, rtype, (byte*)"CompressedFolder", strlen("CompressedFolder"));
//          trace("::RegSetValueEx CompressedFolder %d\n", r);
        }
    }
    RegCloseKey(key);
}

static char* ext[] = {"zip","7z","rar","bz2","gz","tgz","tar","arj","lzh","z","cab","chm","cpio", "ear", "war", "zap", "zpg", "zap", "cbr", "cbz", null};

static void cleanRegistry() {
    deleteKey(HKEY_CLASSES_ROOT, "Applications\\Zipeg.exe");
    deleteKey(HKEY_CLASSES_ROOT, "Zipeg");
    deleteKey(HKEY_CLASSES_ROOT, "Zipeg.AssocFile.ZIP");
    deleteKey(HKEY_CURRENT_USER, "Software\\Classes\\Applications\\Zipeg.exe");
    deleteKey(HKEY_CURRENT_USER, "Software\\Classes\\Zipeg");
    deleteKey(HKEY_CURRENT_USER, "Software\\Classes\\Zipeg.AssocFile.ZIP");

    deleteKey(HKEY_CURRENT_USER, "Software\\Zipeg");
    deleteKey(HKEY_LOCAL_MACHINE, "SOFTWARE\\Classes\\Applications\\Zipeg.exe");

    deleteKey(HKEY_CLASSES_ROOT, "SystemFileAssociations\\compressed\\shell\\open.zipeg.extract.here");
    deleteKey(HKEY_CLASSES_ROOT, "SystemFileAssociations\\compressed\\shell\\open.zipeg.extract.to");
    deleteKey(HKEY_LOCAL_MACHINE, "Classes\\SystemFileAssociations\\compressed\\shell\\open.zipeg.extract.here");
    deleteKey(HKEY_LOCAL_MACHINE, "Classes\\SystemFileAssociations\\compressed\\shell\\open.zipeg.extract.to");
    deleteKey(HKEY_LOCAL_MACHINE, "SOFTWARE\\Classes\\Zipeg");
    deleteKey(HKEY_LOCAL_MACHINE, "SOFTWARE\\Classes\\Zipeg.AssocFile.ZIP");
    deleteKey(HKEY_LOCAL_MACHINE, "SOFTWARE\\Zipeg");
    deleteKey(HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\Zipeg.exe");

    deleteKey(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs\\com\\zipeg");
    deleteKey(HKEY_CURRENT_USER, "Software\\RegisteredApplications\\Zipeg");
    deleteKey(HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\Zipeg.exe");

    restoreCompressedFolder(HKEY_CLASSES_ROOT, "SystemFileAssociations\\compressed\\shell\\open");
    restoreCompressedFolder(HKEY_CLASSES_ROOT, "SystemFileAssociations\\compressed\\shell\\open\\command");

    restoreCompressedFolder(HKEY_LOCAL_MACHINE, "SOFTWARE\\Classes\\SystemFileAssociations\\compressed\\shell\\open");
    restoreCompressedFolder(HKEY_LOCAL_MACHINE, "SOFTWARE\\Classes\\SystemFileAssociations\\compressed\\shell\\open\\command");
    for (int i = 0; ext[i] != null; i++) {
        char path[1024] = {0};
        wsprintf(path, ".%s\\OpenWithList\\Zipeg.exe", ext[i]);
        deleteKey(HKEY_CLASSES_ROOT, path);
        wsprintf(path, "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.%s\\OpenWithProgids", ext[i]);
        deleteValue(HKEY_CURRENT_USER, path, "Zipeg");
    }
}

void removeAll(const char *filename) {
    char tmp[MAX_PATH];
    Sleep(1000);
    killAll();
    GetTempPath(sizeof(tmp), tmp);
    SetCurrentDirectory(tmp);
    char* programfiles = getInstallationFolder();
    if (programfiles == null) {
        trace("failed to getInstallationFolder()\n");
        return;
    }
    char dest[MAX_PATH + 1024] = {0};
    strcat(dest, programfiles);
    strcat(dest, "\\Zipeg");
    if (IsDirectory(dest)) {
        removeTree(dest);
    }
    removeShortcuts("Zipeg");
    removeUninstall(install_local);
    cleanRegistry();
    registerZipFolders(true);
    MessageBox(NULL, "Zipeg has been uninstalled from your computer", "Zipeg",
	       (MB_OK|MB_ICONINFORMATION|MB_APPLMODAL));
    char buf[1024*4] = {0};
    wsprintf(buf,
        "start /min cmd /c \" "
        "sleep 01 & "
        "taskkill /f /im zipeg.exe & "
        "sleep 01 & "
        "del /q \"%s\" & "
        "del /q \"%s\\Application\\zipeg.exe\" \"",
        filename, dest);
//  MessageBox(null, buf, "x", MB_OK);
    system(buf);
}

void uninstall(const char *filename) {
    killAll();
    char tmp[MAX_PATH];
    char exe[MAX_PATH];
    SetErrorMode(SEM_FAILCRITICALERRORS|SEM_NOOPENFILEERRORBOX);
    cleanRegistry();

    char run[MAX_PATH] = {0};
    GetModuleFileName(GetModuleHandle(null), exe, sizeof(exe));
    strcpy(run, "\"");
    strcat(run, exe);
    strcat(run, "\" ");
    strcat(run, " ");
    strcat(run, "-uninstall-cleanup");
    runProcess(exe, run, true);
    killAll();
    GetTempPath(sizeof(tmp), tmp);
    GetTempFileName(tmp, "uninstall", rand(), exe);
    if (!copyFile(filename, exe)) {
        trace("failed to copy executable\n");
        return;
    }
    memset(run, 0, sizeof(run));
    strcpy(run, "\"");
    strcat(run, exe);
    strcat(run, "\" ");
    strcat(run, " ");
    strcat(run, "-uninstall-remove");
    runProcess(exe, run, false);
}

} // extern "C"
