// Common/StringConvert.cpp

#include "StdAfx.h"
#include <stdlib.h>

#include "StringConvert.h"
extern "C"
{
int global_use_utf16_conversion = 0;
}

#ifdef LOCALE_IS_UTF8

#ifdef __APPLE_CC__
#define UInt32  macUIn32
#include <CoreFoundation/CoreFoundation.h>
#undef UInt32

static CFStringEncoding wchar_t_encoding;

static bool getBytes(CFStringRef cfs, wchar_t* buff, int n, UString& r) {
    if (wchar_t_encoding == 0) {
        wchar_t_encoding = CFByteOrderGetCurrent() == CFByteOrderLittleEndian ? kCFStringEncodingUTF32LE : kCFStringEncodingUTF32BE;
    }
    CFIndex index = CFStringGetBytes(cfs, CFRangeMake(0, n), wchar_t_encoding, '?', false,
                                     (UInt8*)buff, (n + 1) * sizeof(wchar_t) * 2, NULL);
    CFRelease(cfs);
    if (index > 0) {
        r = buff;
    }
    return index > 0;
}


UString MultiByteToUnicodeString(const AString &srcString, int codePage)
{
    UString r;
    const char * s = &srcString[0];
    size_t n = strlen(s);
    if (n == 0) {
        return r;
    }
    wchar_t wide[(n + 1) * 2 + n];
    bool high = false;
    for (size_t i = 0 ; i < n && ! high; i++) {
        wide[i] = ((unsigned char)s[i]) & 0xFFU;
        high = wide[i] > 0x7F;
    }
    wide[n] = 0;
    if (!high) { // code page does not matter unless it is UTF7 which is rarely used
        r = wide;
        return r;
    }
    CFStringEncoding encoding = codePage == CP_ACP || codePage == CP_OEMCP ?
            CFStringGetSystemEncoding() : (CFStringEncoding)codePage;
    CFStringRef cfs = CFStringCreateWithBytes(kCFAllocatorDefault, (const UInt8*)s, n, codePage, false);
    if (cfs && getBytes(cfs, wide, n, r)) {
        return r;
    }
    if (encoding != kCFStringEncodingUTF8) {
        CFStringRef cfs = CFStringCreateWithBytes(kCFAllocatorDefault, (const UInt8*)s, n, kCFStringEncodingUTF8, false);
        if (cfs && getBytes(cfs, wide, n, r)) {
            return r;
        }
    }
    if (encoding != kCFStringEncodingDOSLatin1) {
        CFStringRef cfs = CFStringCreateWithBytes(kCFAllocatorDefault, (const UInt8*)s, n, kCFStringEncodingDOSLatin1, false);
        if (cfs && getBytes(cfs, wide, n, r)) {
            return r;
        }
    }
    if (encoding != kCFStringEncodingISOLatin1) {
        CFStringRef cfs = CFStringCreateWithBytes(kCFAllocatorDefault, (const UInt8*)s, n, kCFStringEncodingISOLatin1, false);
        if (cfs && getBytes(cfs, wide, n, r)) {
            return r;
        }
    }
    if (encoding != kCFStringEncodingWindowsLatin1) {
        CFStringRef cfs = CFStringCreateWithBytes(kCFAllocatorDefault, (const UInt8*)s, n, kCFStringEncodingWindowsLatin1, false);
        if (cfs && getBytes(cfs, wide, n, r)) {
            return r;
        }
    }
    r = wide;
    return r;
}

AString UnicodeStringToMultiByte(const UString &s, int codePage)
{
    printf("UnicodeStringToMultiByte \"%ls\" %d 0x%08X\n", (LPCTSTR)s, codePage, wchar_t_encoding);
    if (s.IsEmpty()) {
        return "";
    }
    if (wchar_t_encoding == 0) {
        wchar_t_encoding = CFByteOrderGetCurrent() == CFByteOrderLittleEndian ? kCFStringEncodingUTF32LE : kCFStringEncodingUTF32BE;
    }
    size_t n = s.Length();
    CFStringRef cfs = CFStringCreateWithBytes(kCFAllocatorDefault, (const UInt8*)&s[0], n * sizeof(wchar_t), wchar_t_encoding, false);
    assert(cfs);
    int size = (n + 1) * sizeof(wchar_t) + n; // very conservative, assume that we will need to escape every wchar_t
    char buff[size];
    CFIndex index = CFStringGetBytes(cfs, CFRangeMake(0, n), kCFStringEncodingUTF8, '?', false,
                                     (UInt8*)buff, size, NULL);
    buff[index] = 0;
    printf("UnicodeStringToMultiByte buff=\"%s\" %d\n", (LPCTSTR)buff, index);
    CFRelease(cfs);
    AString a = buff;
    return a;
}

#else /* __APPLE_CC__ */


#include "UTFConvert.h"

UString MultiByteToUnicodeString(const AString &srcString, int codePage)
{
  if ((global_use_utf16_conversion) && (!srcString.IsEmpty()))
  {
    UString resultString;
    bool bret = ConvertUTF8ToUnicode(srcString,resultString);
    if (bret) return resultString;
  }

  UString resultString;
  for (int i = 0; i < srcString.Length(); i++)
    resultString += wchar_t(srcString[i] & 255);

  return resultString;
}

AString UnicodeStringToMultiByte(const UString &srcString, int codePage)
{
  if ((global_use_utf16_conversion) && (!srcString.IsEmpty()))
  {
    AString resultString;
    bool bret = ConvertUnicodeToUTF8(srcString,resultString);
    if (bret) return resultString;
  }

  AString resultString;
  for (int i = 0; i < srcString.Length(); i++)
  {
    if (srcString[i] >= 256) resultString += '?';
    else                     resultString += char(srcString[i]);
  }
  return resultString;
}

#endif /* __APPLE_CC__ */

#else /* LOCALE_IS_UTF8 */

UString MultiByteToUnicodeString(const AString &srcString, int codePage)
{
#ifdef ENV_HAVE_MBSTOWCS
  if ((global_use_utf16_conversion) && (!srcString.IsEmpty()))
  {
    UString resultString;
    int numChars = (int)mbstowcs(resultString.GetBuffer(srcString.Length()),srcString,srcString.Length()+1);
    if (numChars >= 0) {
      resultString.ReleaseBuffer(numChars);
      return resultString;
    }
  }
#endif

  UString resultString;
  for (int i = 0; i < srcString.Length(); i++)
    resultString += wchar_t(srcString[i] & 255);

  return resultString;
}

AString UnicodeStringToMultiByte(const UString &srcString, int codePage)
{
#ifdef ENV_HAVE_WCSTOMBS
  if ((global_use_utf16_conversion) && (!srcString.IsEmpty()))
  {
    AString resultString;
    int numRequiredBytes = srcString.Length() * 6+1;
    int numChars = (int)wcstombs(resultString.GetBuffer(numRequiredBytes),srcString,numRequiredBytes);
    if (numChars >= 0) {
      resultString.ReleaseBuffer(numChars);
      return resultString;
    }
  }
#endif

  AString resultString;
  for (int i = 0; i < srcString.Length(); i++)
  {
    if (srcString[i] >= 256) resultString += '?';
    else                     resultString += char(srcString[i]);
  }
  return resultString;
}

#endif /* LOCALE_IS_UTF8 */

