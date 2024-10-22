/*
 * @(#)java_md.c	1.58 06/04/14
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
#ifdef WIN32
#pragma warning(disable :4615) // unknown user warning type
#pragma warning(disable :4018) // signed/unsigned mismatch
#pragma warning(disable :4146) // unary minus operator applied to unsigned type, result still unsigned
#pragma warning(disable :4996) // This function or variable may be unsafe
#endif

#include <windows.h>
#include <io.h>
#include <process.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>

#include <jni.h>
#include "java.h"
#include "version_comp.h"

#define JVM_DLL "jvm.dll"
#define JAVA_DLL "java.dll"
#define CRT_DLL "msvcr71.dll"

/*
 * Prototypes.
 */
static jboolean GetPublicJREHome(char *path, jint pathsize);
static jboolean GetJVMPath(const char *jrepath, const char *jvmtype,
			   char *jvmpath, jint jvmpathsize);
static jboolean GetJREPath(char *path, jint pathsize);

const char *
GetArch()
{

#ifdef _M_AMD64
    return "amd64";
#elif defined(_M_IA64)
    return "ia64";
#else
    return "i386";
#endif
}

extern void trace(const char *fmt, ...);
extern void exitProcess(int exitcode);

/*
 *
 */
void
CreateExecutionEnvironment(int *_argc,
			   char ***_argv,
			   char jrepath[],
			   jint so_jrepath,
			   char jvmpath[],
			   jint so_jvmpath,
			   char **original_argv) {
   char * jvmtype;
/*
    // Find out where the JRE is that we will be using.
    if (!GetJREPath(jrepath, so_jrepath)) {
	ReportErrorMessage("Error: could not find Java 2 Runtime Environment.",
			   JNI_TRUE);
	exitProcess(2);
    }
*/
    /* Find the specified JVM type */
    if (ReadKnownVMs(jrepath, (char*)GetArch(), JNI_FALSE) < 1) {
	ReportErrorMessage("Error: no known VMs. (check for corrupt jvm.cfg file)",
			   JNI_TRUE);
	exitProcess(1);
    }
    jvmtype = CheckJvmType(_argc, _argv, JNI_FALSE);

    jvmpath[0] = '\0';
    if (!GetJVMPath(jrepath, jvmtype, jvmpath, so_jvmpath)) {
        char * message=NULL;
	const char * format = "Error: no `%s' JVM at `%s'.";
	message = (char *)JLI_MemAlloc((strlen(format)+strlen(jvmtype)+
				    strlen(jvmpath)) * sizeof(char));
	sprintf(message,format, jvmtype, jvmpath);
	ReportErrorMessage(message, JNI_TRUE);
	exitProcess(4);
    }
    /* If we got here, jvmpath has been correctly initialized. */

}

/*
 * Find path to JRE based on .exe's location or registry settings.
 */
jboolean
GetJREPath(char *path, jint pathsize)
{
    char javadll[MAXPATHLEN];
    struct stat s;

    if (GetApplicationHome(path, pathsize)) {
	/* Is JRE co-located with the application? */
	sprintf(javadll, "%s\\bin\\" JAVA_DLL, path);
	if (stat(javadll, &s) == 0) {
	    goto found;
	}

	/* Does this app ship a private JRE in <apphome>\jre directory? */
	sprintf(javadll, "%s\\jre\\bin\\" JAVA_DLL, path);
	if (stat(javadll, &s) == 0) {
	    strcat(path, "\\jre");
	    goto found;
	}
    }

    /* Look for a public JRE on this machine. */
    if (GetPublicJREHome(path, pathsize)) {
	goto found;
    }

    trace("Error: could not find " JAVA_DLL "\n");
    return JNI_FALSE;

 found:
    if (_launcher_debug)
      printf("JRE path is %s\n", path);
    return JNI_TRUE;
}

/*
 * Given a JRE location and a JVM type, construct what the name the
 * JVM shared library will be.  Return true, if such a library
 * exists, false otherwise.
 */
static jboolean
GetJVMPath(const char *jrepath, const char *jvmtype,
	   char *jvmpath, jint jvmpathsize)
{
    struct stat s;
    if (strchr(jvmtype, '/') || strchr(jvmtype, '\\')) {
	sprintf(jvmpath, "%s\\" JVM_DLL, jvmtype);
    } else {
	sprintf(jvmpath, "%s\\bin\\%s\\" JVM_DLL, jrepath, jvmtype);
    }
    if (stat(jvmpath, &s) == 0) {
	return JNI_TRUE;
    } else {
	return JNI_FALSE;
    }
}

/*
 * Load a jvm from "jvmpath" and initialize the invocation functions.
 */
jboolean
LoadJavaVM(const char *jvmpath, InvocationFunctions *ifn)
{
    HINSTANCE handle = NULL;
	int i = 0;
	char crtpath[MAXPATHLEN] = {0};
	char path1[MAXPATHLEN] = {0};
	char path2[MAXPATHLEN] = {0};
	char* path = NULL;
	char* PATH = NULL;

    if (_launcher_debug) {
	printf("JVM path is %s\n", jvmpath);
    }

    /*
     * The Microsoft C Runtime Library needs to be loaded first.  A copy is
     * assumed to be present in the "JRE path" directory.  If it is not found
     * there (or "JRE path" fails to resolve), skip the explicit load and let
     * nature take its course, which is likely to be a failure to execute.
     */
    if (GetJREPath(crtpath, MAXPATHLEN)) {
	(void)strcat(crtpath, "\\bin\\" CRT_DLL);	/* Add crt dll */
	if (_launcher_debug) {
	     printf("CRT path is %s\n", crtpath);
	}
	if (_access(crtpath, 0) == 0) {
	    if (LoadLibrary(crtpath) == 0) {
		ReportErrorMessage2("Error loading: %s", crtpath, JNI_TRUE);
		return JNI_FALSE;
	    }
	}
    }

    strcpy(path1, jvmpath);
    i = strlen(path1);
    while (i > 0 && path1[i-1] != '/' && path1[i-1] != '\\') {
        i--;
    }
    if (i > 0) {
        path1[i - 1] = 0;
    }

    strcpy(path2, path1);
    i = strlen(path2);
    while (i > 0 && path2[i-1] != '/' && path2[i-1] != '\\') {
        i--;
    }
    if (i > 0) {
        path2[i - 1] = 0;
    }

    path = getenv("Path");
    PATH = malloc(strlen(path) + strlen(path1) + strlen(path2) + 16);
    strcpy(PATH, "Path=.;");
    strcat(PATH, path1);
    strcat(PATH, ";");
    strcat(PATH, path2);
    strcat(PATH, ";");
    strcat(PATH, path);
    putenv(PATH);
    free(PATH);
    PATH = NULL;
/*
    path = getenv("Path");
    MessageBox(NULL, path, "Path", (MB_OK|MB_ICONINFORMATION|MB_APPLMODAL));
*/
    /* Load the Java VM DLL */
    if ((handle = LoadLibrary(jvmpath)) == 0) {
	ReportErrorMessage2("Error loading: %s", (char *)jvmpath, JNI_TRUE);
	return JNI_FALSE;
    }

    /* Now get the function addresses */
    ifn->CreateJavaVM =
	(void *)GetProcAddress(handle, "JNI_CreateJavaVM");
    ifn->GetDefaultJavaVMInitArgs =
	(void *)GetProcAddress(handle, "JNI_GetDefaultJavaVMInitArgs");
    if (ifn->CreateJavaVM == 0 || ifn->GetDefaultJavaVMInitArgs == 0) {
	ReportErrorMessage2("Error: can't find JNI interfaces in: %s",
			    (char *)jvmpath, JNI_TRUE);
	return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
 * If app is "c:\foo\bin\javac", then put "c:\foo" into buf.
 */
jboolean
GetApplicationHome(char *buf, jint bufsize)
{
    char *cp;
    GetModuleFileName(0, buf, bufsize);
    *strrchr(buf, '\\') = '\0'; /* remove .exe file name */
    if ((cp = strrchr(buf, '\\')) == 0) {
	/* This happens if the application is in a drive root, and
	 * there is no bin directory. */
	buf[0] = '\0';
	return JNI_FALSE;
    }
    *cp = '\0';  /* remove the bin\ part */
    return JNI_TRUE;
}

#ifdef JAVAW
extern char **__initenv;

static
char** mergeUTF8(int argc, wchar_t** wargv) {
    int i;
    char** argv = (char**)malloc((argc + 1) * sizeof(char*));
    for (i = 0; i < argc; i++) {
        int n = wcslen(wargv[i]) * 3 + 2;
        char* dest = malloc(n);
        memset(dest, 0, n);
        WideCharToMultiByte(CP_UTF8, 0, wargv[i], -1, dest, n, NULL, NULL);
        argv[i] = dest;
    }
    argv[argc] = "--win32";
    return argv;
}

int WINAPI
WinMain(HINSTANCE inst, HINSTANCE previnst, LPSTR cmdline, int cmdshow)
{
    int   ret = 0, argc = 0;
    wchar_t ** wargv = CommandLineToArgvW(GetCommandLineW(), &argc);
    char** argv = mergeUTF8(argc, wargv);
    __initenv = _environ;
    ret = main(argc + 1, argv); // also appended --win32
    return ret;
}
#endif

/*
 * Helpers to look in the registry for a public JRE.
 */
                    /* Same for 1.5.0, 1.5.1, 1.5.2 etc. */
#define DOTRELEASE  "1.6"
#define JRE_KEY	    "Software\\JavaSoft\\Java Runtime Environment"

static jboolean
GetStringFromRegistry(HKEY key, const char *name, char *buf, jint bufsize)
{
    DWORD type, size;

    if (RegQueryValueEx(key, name, 0, &type, 0, &size) == 0
	&& type == REG_SZ
	&& (size < (unsigned int)bufsize)) {
	if (RegQueryValueEx(key, name, 0, 0, buf, &size) == 0) {
	    return JNI_TRUE;
	}
    }
    return JNI_FALSE;
}

static jboolean
GetPublicJREHome(char *buf, jint bufsize)
{
    HKEY key, subkey;
    char version[MAXPATHLEN];

    /*
     * Note: There is a very similar implementation of the following
     * registry reading code in the Windows java control panel (javacp.cpl).
     * If there are bugs here, a similar bug probably exists there.  Hence,
     * changes here require inspection there.
     */

    /* Find the current version of the JRE */
    if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, JRE_KEY, 0, KEY_READ, &key) != 0) {
	trace("Error opening registry key '" JRE_KEY "'\n");
	return JNI_FALSE;
    }

    if (!GetStringFromRegistry(key, "CurrentVersion",
			       version, sizeof(version))) {
	trace("Failed reading value of registry key:\n\t"
		JRE_KEY "\\CurrentVersion\n");
	RegCloseKey(key);
	return JNI_FALSE;
    }

    if (strcmp(version, DOTRELEASE) != 0) {
	trace("Registry key '" JRE_KEY "\\CurrentVersion'\nhas "
		"value '%s', but '" DOTRELEASE "' is required.\n", version);
	RegCloseKey(key);
	return JNI_FALSE;
    }

    /* Find directory where the current version is installed. */
    if (RegOpenKeyEx(key, version, 0, KEY_READ, &subkey) != 0) {
	trace("Error opening registry key '"
		JRE_KEY "\\%s'\n", version);
	RegCloseKey(key);
	return JNI_FALSE;
    }

    if (!GetStringFromRegistry(subkey, "JavaHome", buf, bufsize)) {
	trace("Failed reading value of registry key:\n\t"
		JRE_KEY "\\%s\\JavaHome\n", version);
	RegCloseKey(key);
	RegCloseKey(subkey);
	return JNI_FALSE;
    }

    if (_launcher_debug) {
	char micro[MAXPATHLEN];
	if (!GetStringFromRegistry(subkey, "MicroVersion", micro,
				   sizeof(micro))) {
	    printf("Warning: Can't read MicroVersion\n");
	    micro[0] = '\0';
	}
	printf("Version major.minor.micro = %s.%s\n", version, micro);
    }

    RegCloseKey(key);
    RegCloseKey(subkey);
    return JNI_TRUE;
}

/*
 * Support for doing cheap, accurate interval timing.
 */
static jboolean counterAvailable = JNI_FALSE;
static jboolean counterInitialized = JNI_FALSE;
static LARGE_INTEGER counterFrequency;

jlong CounterGet()
{
    LARGE_INTEGER count;

    if (!counterInitialized) {
	counterAvailable = QueryPerformanceFrequency(&counterFrequency);
	counterInitialized = JNI_TRUE;
    }
    if (!counterAvailable) {
	return 0;
    }
    QueryPerformanceCounter(&count);
    return (jlong)(count.QuadPart);
}

jlong Counter2Micros(jlong counts)
{
    if (!counterAvailable || !counterInitialized) {
	return 0;
    }
    return (counts * 1000 * 1000)/counterFrequency.QuadPart;
}

void ReportErrorMessage(char * message, jboolean always) {
#ifdef JAVAW
  if (message != NULL) {
    MessageBox(NULL, message, "Zipeg",
	       (MB_OK|MB_ICONSTOP|MB_APPLMODAL));
  }
#else
  if (always) {
    trace("%s\n", message);
  }
#endif
}

void ReportErrorMessage2(char * format, char * string, jboolean always) {
  /*
   * The format argument must be a printf format string with one %s
   * argument, which is passed the string argument.
   */
#ifdef JAVAW
  size_t size;
  char * message;
  size = strlen(format) + strlen(string);
  message = (char*)JLI_MemAlloc(size*sizeof(char));
  sprintf(message, (const char *)format, string);

  if (message != NULL) {
    MessageBox(NULL, message, "Zipeg",
	       (MB_OK|MB_ICONSTOP|MB_APPLMODAL));
    JLI_MemFree(message);
  }
#else
  if (always) {
    trace((const char *)format, string);
    trace("\n");
  }
#endif
}

/*
 * As ReportErrorMessage2 (above) except the system message (if any)
 * associated with this error is written to a second %s format specifier
 * in the format argument.
 */
void ReportSysErrorMessage2(char * format, char * string, jboolean always) {
  int	save_errno = errno;
  DWORD	errval;
  int	freeit = 0;
  char  *errtext = NULL;

  if ((errval = GetLastError()) != 0) {		/* Platform SDK / DOS Error */
    int n = FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM|
      FORMAT_MESSAGE_IGNORE_INSERTS|FORMAT_MESSAGE_ALLOCATE_BUFFER,
      NULL, errval, 0, (LPTSTR)&errtext, 0, NULL);
    if (errtext == NULL || n == 0) {		/* Paranoia check */
      errtext = "";
      n = 0;
    } else {
      freeit = 1;
      if (n > 2) {				/* Drop final CR, LF */
	if (errtext[n - 1] == '\n') n--;
	if (errtext[n - 1] == '\r') n--;
	errtext[n] = '\0';
      }
    }
  } else	/* C runtime error that has no corresponding DOS error code */
    errtext = strerror(save_errno);

#ifdef JAVAW
  {
    size_t size;
    char * message;
    size = strlen(format) + strlen(string) + strlen(errtext);
    message = (char*)JLI_MemAlloc(size*sizeof(char));
    sprintf(message, (const char *)format, string, errtext);

    if (message != NULL) {
      MessageBox(NULL, message, "Zipeg",
	       (MB_OK|MB_ICONSTOP|MB_APPLMODAL));
      JLI_MemFree(message);
    }
  }
#else
  if (always) {
    trace((const char *)format, string, errtext);
    trace("\n");
  }
#endif
  if (freeit)
    (void)LocalFree((HLOCAL)errtext);
}

void  ReportExceptionDescription(JNIEnv * env) {
#ifdef JAVAW
  /*
   * This code should be replaced by code which opens a window with
   * the exception detail message.
   */
  (*env)->ExceptionDescribe(env);
#else
  (*env)->ExceptionDescribe(env);
#endif
}


/*
 * Return JNI_TRUE for an option string that has no effect but should
 * _not_ be passed on to the vm; return JNI_FALSE otherwise. On
 * windows, there are no options that should be screened in this
 * manner.
 */
jboolean RemovableMachineDependentOption(char * option) {
  return JNI_FALSE;
}

void PrintMachineDependentOptions() {
  return;
}

jboolean
ServerClassMachine() {
  jboolean result = JNI_FALSE;
#if   defined(NEVER_ACT_AS_SERVER_CLASS_MACHINE)
  result = JNI_FALSE;
#elif defined(ALWAYS_ACT_AS_SERVER_CLASS_MACHINE)
  result = JNI_TRUE;
#endif
  return result;
}

/*
 * Determine if there is an acceptable JRE in the registry directory top_key.
 * Upon locating the "best" one, return a fully qualified path to it.
 * "Best" is defined as the most advanced JRE meeting the constraints
 * contained in the manifest_info. If no JRE in this directory meets the
 * constraints, return NULL.
 *
 * It doesn't matter if we get an error reading the registry, or we just
 * don't find anything interesting in the directory.  We just return NULL
 * in either case.
 */
static char *
ProcessDir(manifest_info* info, HKEY top_key) {
    DWORD   index = 0;
    HKEY    ver_key;
    char    name[MAXNAMELEN];
    int	    len;
    char    *best = NULL;

    /*
     * Enumerate "<top_key>/SOFTWARE/JavaSoft/Java Runtime Environment"
     * searching for the best available version.
     */
    while (RegEnumKey(top_key, index, name, MAXNAMELEN) == ERROR_SUCCESS) {
	index++;
	if (JLI_AcceptableRelease(name, info->jre_version))
	    if ((best == NULL) || (JLI_ExactVersionId(name, best) > 0)) {
		if (best != NULL)
		    JLI_MemFree(best);
		best = JLI_StringDup(name);
	    }
    }

    /*
     * Extract "JavaHome" from the "best" registry directory and return
     * that path.  If no appropriate version was located, or there is an
     * error in extracting the "JavaHome" string, return null.
     */
    if (best == NULL)
	return (NULL);
    else {
	if (RegOpenKeyEx(top_key, best, 0, KEY_READ, &ver_key)
	  != ERROR_SUCCESS) {
	    JLI_MemFree(best);
	    if (ver_key != NULL)
		RegCloseKey(ver_key);
	    return (NULL);
	}
	JLI_MemFree(best);
	len = MAXNAMELEN;
	if (RegQueryValueEx(ver_key, "JavaHome", NULL, NULL, (LPBYTE)name, &len)
	  != ERROR_SUCCESS) {
	    if (ver_key != NULL)
		RegCloseKey(ver_key);
	    return (NULL);
	}
	if (ver_key != NULL)
	    RegCloseKey(ver_key);
	return (JLI_StringDup(name));
    }
}

/*
 * This is the global entry point. It examines the host for the optimal
 * JRE to be used by scanning a set of registry entries.  This set of entries
 * is hardwired on Windows as "Software\JavaSoft\Java Runtime Environment"
 * under the set of roots "{ HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE }".
 *
 * This routine simply opens each of these registry directories before passing
 * control onto ProcessDir().
 */
char *
LocateJRE(manifest_info* info) {
    HKEY    key = NULL;
    char    *path;
    int	    key_index;
    HKEY    root_keys[2] = { HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE };

    for (key_index = 0; key_index <= 1; key_index++) {
	if (RegOpenKeyEx(root_keys[key_index], JRE_KEY, 0, KEY_READ, &key)
	  == ERROR_SUCCESS)
	    if ((path = ProcessDir(info, key)) != NULL) {
		if (key != NULL)
		    RegCloseKey(key);
		return (path);
	    }
	if (key != NULL)
	    RegCloseKey(key);
    }
    return NULL;
}

/*
 * Local helper routine to isolate a single token (option or argument)
 * from the command line.
 *
 * This routine accepts a pointer to a character pointer.  The first
 * token (as defined by MSDN command-line argument syntax) is isolated
 * from that string.
 *
 * Upon return, the input character pointer pointed to by the parameter s
 * is updated to point to the remainding, unscanned, portion of the string,
 * or to a null character if the entire string has been consummed.
 *
 * This function returns a pointer to a null-terminated string which
 * contains the isolated first token, or to the null character if no
 * token could be isolated.
 *
 * Note the side effect of modifying the input string s by the insertion
 * of a null character, making it two strings.
 *
 * See "Parsing C Command-Line Arguments" in the MSDN Library for the
 * parsing rule details.  The rule summary from that specification is:
 *
 *  * Arguments are delimited by white space, which is either a space or a tab.
 *
 *  * A string surrounded by double quotation marks is interpreted as a single
 *    argument, regardless of white space contained within. A quoted string can
 *    be embedded in an argument. Note that the caret (^) is not recognized as
 *    an escape character or delimiter.
 *
 *  * A double quotation mark preceded by a backslash, \", is interpreted as a
 *    literal double quotation mark (").
 *
 *  * Backslashes are interpreted literally, unless they immediately precede a
 *    double quotation mark.
 *
 *  * If an even number of backslashes is followed by a double quotation mark,
 *    then one backslash (\) is placed in the argv array for every pair of
 *    backslashes (\\), and the double quotation mark (") is interpreted as a
 *    string delimiter.
 *
 *  * If an odd number of backslashes is followed by a double quotation mark,
 *    then one backslash (\) is placed in the argv array for every pair of
 *    backslashes (\\) and the double quotation mark is interpreted as an
 *    escape sequence by the remaining backslash, causing a literal double
 *    quotation mark (") to be placed in argv.
 */
static char*
nextarg(char** s) {
    char    *p = *s;
    char    *head;
    int     slashes = 0;
    int     inquote = 0;

    /*
     * Strip leading whitespace, which MSDN defines as only space or tab.
     * (Hence, no locale specific "isspace" here.)
     */
    while (*p != (char)0 && (*p == ' ' || *p == '\t'))
        p++;
    head = p;			/* Save the start of the token to return */

    /*
     * Isolate a token from the command line.
     */
    while (*p != (char)0 && (inquote || !(*p == ' ' || *p == '\t'))) {
	if (*p == '\\' && *(p+1) == '"' && slashes % 2 == 0)
	    p++;
	else if (*p == '"')
	    inquote = !inquote;
	slashes = (*p++ == '\\') ? slashes + 1 : 0;
    }

    /*
     * If the token isolated isn't already terminated in a "char zero",
     * then replace the whitespace character with one and move to the
     * next character.
     */
    if (*p != (char)0)
	*p++ = (char)0;

    /*
     * Update the parameter to point to the head of the remaining string
     * reflecting the command line and return a pointer to the leading
     * token which was isolated from the command line.
     */
    *s = p;
    return (head);
}

/*
 * Local helper routine to return a string equivalent to the input string
 * s, but with quotes removed so the result is a string as would be found
 * in argv[].  The returned string should be freed by a call to JLI_MemFree().
 *
 * The rules for quoting (and escaped quotes) are:
 *
 *  1 A double quotation mark preceded by a backslash, \", is interpreted as a
 *    literal double quotation mark (").
 *
 *  2 Backslashes are interpreted literally, unless they immediately precede a
 *    double quotation mark.
 *
 *  3 If an even number of backslashes is followed by a double quotation mark,
 *    then one backslash (\) is placed in the argv array for every pair of
 *    backslashes (\\), and the double quotation mark (") is interpreted as a
 *    string delimiter.
 *
 *  4 If an odd number of backslashes is followed by a double quotation mark,
 *    then one backslash (\) is placed in the argv array for every pair of
 *    backslashes (\\) and the double quotation mark is interpreted as an
 *    escape sequence by the remaining backslash, causing a literal double
 *    quotation mark (") to be placed in argv.
 */
static char*
unquote(const char *s) {
    const char *p = s;		/* Pointer to the tail of the original string */
    char *un = (char*)JLI_MemAlloc(strlen(s) + 1);  /* Ptr to unquoted string */
    char *pun = un;		/* Pointer to the tail of the unquoted string */

    while (*p != '\0') {
	if (*p == '"') {
	    p++;
	} else if (*p == '\\') {
	    const char *q = p + strspn(p,"\\");
	    if (*q == '"')
		do {
		    *pun++ = '\\';
		    p += 2;
		 } while (*p == '\\' && p < q);
	    else
		while (p < q)
		    *pun++ = *p++;
	} else {
	    *pun++ = *p++;
	}
    }
    *pun = '\0';
    return un;
}

/*
 * Given a path to a jre to execute, this routine checks if this process
 * is indeed that jre.  If not, it exec's that jre.
 *
 * We want to actually check the paths rather than just the version string
 * built into the executable, so that given version specification will yield
 * the exact same Java environment, regardless of the version of the arbitrary
 * launcher we start with.
 */
void
ExecJRE(char *jre, char **argv) {
    int     len;
    char    *progname;
    char    path[MAXPATHLEN + 1];

    /*
     * Determine the executable we are building (or in the rare case, running).
     */
#ifdef JAVA_ARGS  /* javac, jar and friends. */
    progname = "java";
#else             /* java, oldjava, javaw and friends */
#ifdef PROGNAME
    progname = PROGNAME;
#else
    {
	char *s;
	progname = *argv;
	if ((s = strrchr(progname, FILE_SEPARATOR)) != 0) {
	    progname = s + 1;
	}
    }
#endif /* PROGNAME */
#endif /* JAVA_ARGS */

    /*
     * Resolve the real path to the currently running launcher.
     */
    len = GetModuleFileName(NULL, path, MAXPATHLEN + 1);
    if (len == 0 || len > MAXPATHLEN) {
	ReportSysErrorMessage2(
	  "Unable to resolve path to current %s executable: %s",
	  progname, JNI_TRUE);
	exitProcess(1);
    }

    if (_launcher_debug) {
	printf("ExecJRE: old: %s\n", path);
	printf("ExecJRE: new: %s\n", jre);
    }

    /*
     * If the path to the selected JRE directory is a match to the initial
     * portion of the path to the currently executing JRE, we have a winner!
     * If so, just return. (strnicmp() is the Windows equiv. of strncasecmp().)
     */
    if (strnicmp(jre, path, strlen(jre)) == 0)
	return;			/* I am the droid you were looking for */

    /*
     * If this isn't the selected version, exec the selected version.
     */
    (void)strcat(strcat(strcpy(path, jre), "\\bin\\"), progname);
    (void)strcat(path, ".exe");

    /*
     * Although Windows has an execv() entrypoint, it doesn't actually
     * overlay a process: it can only create a new process and terminate
     * the old process.  Therefore, any processes waiting on the initial
     * process wake up and they shouldn't.  Hence, a chain of pseudo-zombie
     * processes must be retained to maintain the proper wait semantics.
     * Fortunately the image size of the launcher isn't too large at this
     * time.
     *
     * If it weren't for this semantic flaw, the code below would be ...
     *
     *     execv(path, argv);
     *     ReportErrorMessage2("Exec of %s failed\n", path, JNI_TRUE);
     *     exitProcess(1);
     *
     * The incorrect exec semantics could be addressed by:
     *
     *	   exitProcess((int)spawnv(_P_WAIT, path, argv));
     *
     * Unfortunately, a bug in Windows spawn/exec impementation prevents
     * this from completely working.  All the Windows POSIX process creation
     * interfaces are implemented as wrappers around the native Windows
     * function CreateProcess().  CreateProcess() takes a single string
     * to specify command line options and arguments, so the POSIX routine
     * wrappers build a single string from the argv[] array and in the
     * process, any quoting information is lost.
     *
     * The solution to this to get the original command line, to process it
     * to remove the new multiple JRE options (if any) as was done for argv
     * in the common SelectVersion() routine and finally to pass it directly
     * to the native CreateProcess() Windows process control interface.
     */
    {
	char	*cmdline;
	char	*p;
	char	*np;
	char	*ocl;
	char	*ccl;
	char	*unquoted;
	DWORD	exitCode;
	STARTUPINFO si;
	PROCESS_INFORMATION pi;

	/*
	 * The following code block gets and processes the original command
	 * line, replacing the argv[0] equivalent in the command line with
	 * the path to the new executable and removing the appropriate
	 * Multiple JRE support options. Note that similar logic exists
	 * in the platform independent SelectVersion routine, but is
	 * replicated here due to the syntax of CreateProcess().
	 *
	 * The magic "+ 4" characters added to the command line length are
	 * 2 possible quotes around the path (argv[0]), a space after the
	 * path and a terminating null character.
	 */
	ocl = GetCommandLine();
	np = ccl = JLI_StringDup(ocl);
	p = nextarg(&np);		/* Discard argv[0] */
	cmdline = (char *)JLI_MemAlloc(strlen(path) + strlen(np) + 4);
	if (strchr(path, (int)' ') == NULL && strchr(path, (int)'\t') == NULL)
	    cmdline = strcpy(cmdline, path);
	else
	    cmdline = strcat(strcat(strcpy(cmdline, "\""), path), "\"");

	while (*np != (char)0) {		/* While more command-line */
	    p = nextarg(&np);
	    if (*p != (char)0) {		/* If a token was isolated */
		unquoted = unquote(p);
		if (*unquoted == '-') {		/* Looks like an option */
		    if (strcmp(unquoted, "-classpath") == 0 ||
		      strcmp(unquoted, "-cp") == 0) {	/* Unique cp syntax */
			cmdline = strcat(strcat(cmdline, " "), p);
			p = nextarg(&np);
			if (*p != (char)0)	/* If a token was isolated */
			    cmdline = strcat(strcat(cmdline, " "), p);
		    } else if (strncmp(unquoted, "-version:", 9) != 0 &&
		      strcmp(unquoted, "-jre-restrict-search") != 0 &&
		      strcmp(unquoted, "-no-jre-restrict-search") != 0) {
			cmdline = strcat(strcat(cmdline, " "), p);
		    }
		} else {			/* End of options */
		    cmdline = strcat(strcat(cmdline, " "), p);
		    cmdline = strcat(strcat(cmdline, " "), np);
		    JLI_MemFree((void *)unquoted);
		    break;
		}
		JLI_MemFree((void *)unquoted);
	    }
	}
	JLI_MemFree((void *)ccl);

	if (_launcher_debug) {
	    np = ccl = JLI_StringDup(cmdline);
	    p = nextarg(&np);
	    printf("ReExec Command: %s (%s)\n", path, p);
	    printf("ReExec Args: %s\n", np);
	    JLI_MemFree((void *)ccl);
	}
	(void)fflush(stdout);
	(void)fflush(stderr);

	/*
	 * The following code is modeled after a model presented in the
	 * Microsoft Technical Article "Moving Unix Applications to
	 * Windows NT" (March 6, 1994) and "Creating Processes" on MSDN
	 * (Februrary 2005).  It approximates UNIX spawn semantics with
	 * the parent waiting for termination of the child.
	 */
	memset(&si, 0, sizeof(si));
	si.cb =sizeof(STARTUPINFO);
	memset(&pi, 0, sizeof(pi));

	if (!CreateProcess((LPCTSTR)path,	/* executable name */
	  (LPTSTR)cmdline,			/* command line */
	  (LPSECURITY_ATTRIBUTES)NULL,		/* process security attr. */
	  (LPSECURITY_ATTRIBUTES)NULL,		/* thread security attr. */
	  (BOOL)TRUE,				/* inherits system handles */
	  (DWORD)0,				/* creation flags */
	  (LPVOID)NULL,				/* environment block */
	  (LPCTSTR)NULL,			/* current directory */
	  (LPSTARTUPINFO)&si,			/* (in) startup information */
	  (LPPROCESS_INFORMATION)&pi)) {	/* (out) process information */
	    ReportSysErrorMessage2("CreateProcess(%s, ...) failed: %s",
	      path, JNI_TRUE);
	      exitProcess(1);
	}

	if (WaitForSingleObject(pi.hProcess, INFINITE) != WAIT_FAILED) {
	    if (GetExitCodeProcess(pi.hProcess, &exitCode) == FALSE)
		exitCode = 1;
	} else {
	    ReportErrorMessage("WaitForSingleObject() failed.", JNI_TRUE);
	    exitCode = 1;
	}

	CloseHandle(pi.hThread);
	CloseHandle(pi.hProcess);

	exitProcess(exitCode);
    }

}

/*
 * Wrapper for platform dependent unsetenv function.
 */
int
UnsetEnv(char *name)
{
    int ret;
    char *buf = JLI_MemAlloc(strlen(name) + 2);
    buf = strcat(strcpy(buf, name), "=");
    ret = _putenv(buf);
    JLI_MemFree(buf);
    return (ret);
}

/* --- Splash Screen shared library support --- */

static const char* SPLASHSCREEN_SO = "\\bin\\splashscreen.dll";

static HMODULE hSplashLib = NULL;

void* SplashProcAddress(const char* name) {
    char libraryPath[MAXPATHLEN]; /* some extra space for strcat'ing SPLASHSCREEN_SO */

    if (!GetJREPath(libraryPath, MAXPATHLEN)) {
        return NULL;
    }
    if (strlen(libraryPath)+strlen(SPLASHSCREEN_SO) >= MAXPATHLEN) {
        return NULL;
    }
    strcat(libraryPath, SPLASHSCREEN_SO);

    if (!hSplashLib) {
        hSplashLib = LoadLibrary(libraryPath);
    }
    if (hSplashLib) {
        return GetProcAddress(hSplashLib, name);
    } else {
        return NULL;
    }
}

void SplashFreeLibrary() {
    if (hSplashLib) {
        FreeLibrary(hSplashLib);
        hSplashLib = NULL;
    }
}

const char *
jlong_format_specifier() {
    return "%I64d";
}

/*
 * Block current thread and continue execution in a new thread
 */
int
ContinueInNewThread(int (JNICALL *continuation)(void *), jlong stack_size, void * args) {
    int rslt = 0;
    unsigned thread_id;

#ifndef STACK_SIZE_PARAM_IS_A_RESERVATION
#define STACK_SIZE_PARAM_IS_A_RESERVATION  (0x10000)
#endif

    /*
     * STACK_SIZE_PARAM_IS_A_RESERVATION is what we want, but it's not
     * supported on older version of Windows. Try first with the flag; and
     * if that fails try again without the flag. See MSDN document or HotSpot
     * source (os_win32.cpp) for details.
     */
    HANDLE thread_handle =
      (HANDLE)_beginthreadex(NULL,
                             (unsigned)stack_size,
                             continuation,
                             args,
                             STACK_SIZE_PARAM_IS_A_RESERVATION,
                             &thread_id);
    if (thread_handle == NULL) {
      thread_handle =
      (HANDLE)_beginthreadex(NULL,
                             (unsigned)stack_size,
                             continuation,
                             args,
                             0,
                             &thread_id);
    }
    if (thread_handle) {
      WaitForSingleObject(thread_handle, INFINITE);
      GetExitCodeThread(thread_handle, &rslt);
      CloseHandle(thread_handle);
    } else {
      rslt = continuation(args);
    }
    return rslt;
}

/* Linux only, empty on windows. */
void SetJavaLauncherPlatformProps() {}
