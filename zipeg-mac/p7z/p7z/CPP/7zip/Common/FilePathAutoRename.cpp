// FilePathAutoRename.cpp

#include "StdAfx.h"

#include "Common/Defs.h"
#include "Common/IntToString.h"

#include "Windows/FileFind.h"

#include "FilePathAutoRename.h"

using namespace NWindows;

static bool MakeAutoName(const UString &name,
    const UString &extension, Int64 value, UString &path)
{
  wchar_t number[16];
  ConvertInt64ToString(value, number);
  path = name;
  path += number;
  path += extension;
  return NFile::NFind::DoesFileOrDirExist(path);
}

bool AutoRenamePath(UString &fullProcessedPath)
{
  UString path;
  int dotPos = fullProcessedPath.ReverseFind(L'.');

  int slashPos = fullProcessedPath.ReverseFind(L'/');
#ifdef _WIN32
  int slash1Pos = fullProcessedPath.ReverseFind(L'\\');
  slashPos = MyMax(slashPos, slash1Pos);
#endif

  UString name, extension;
  if (dotPos > slashPos && dotPos > 0)
  {
    name = fullProcessedPath.Left(dotPos);
    extension = fullProcessedPath.Mid(dotPos);
  }
  else {
    name = fullProcessedPath;
  }
#ifdef __APPLE_CC__
    wchar_t suffix = L' ';
#else
    wchar_t suffix = L'_';
#endif
    // This is what "Finder.app" does on OS X 10.8
    Int64 i = -1;
    int spacePos = name.ReverseFind(suffix);
    if (spacePos > 0 && spacePos < name.Length() - 1 && isdigit(name[spacePos + 1])) {
        int k = spacePos + 1;
        int number = name[k++] - '0';
        while (k < name.Length() && isdigit(name[k]) && number < (1LL << 59)) {
            number = number * 10 + (name[k++] - '0');
        }
        if (k == name.Length()) {
            i = number;
            name = fullProcessedPath.Left(spacePos + 1);
        }
    }
    if (i < 0) {
        i = 1;
        name += suffix;
    }
    bool b = true;
    while (b && i < (1LL << 62)) {
        b = MakeAutoName(name, extension, i, path);
        if (b) {
            i = i + i;
        }
    }
    if (b) {
        return false;
    }
    Int64 left = i / 2 + 1, right = i;
    while (left < right) {
       Int64 mid = (left + right) / 2;
       if (MakeAutoName(name, extension, mid, path))
           left = mid + 1;
       else
           right = mid;
    }
    return !MakeAutoName(name, extension, right, fullProcessedPath);
}
