// UpdateCallbackConsole.cpp

#include "StdAfx.h"

#include "UpdateCallbackConsole.h"

#include "Windows/Error.h"

using namespace NWindows;

static const wchar_t *kEmptyFileAlias = L"[Content]";

static const char *kCreatingArchiveMessage = "Creating archive ";
static const char *kUpdatingArchiveMessage = "Updating archive ";
static const char *kScanningMessage = "Scanning";
static const char *kNoFilesScannedMessage = "No files scanned";
static const char *kTotalFilesAddedMessage = "Total files added to archive: ";

// static NSynchronization::CCriticalSection g_CriticalSection;

HRESULT CUpdateCallbackConsole::OpenResult(const wchar_t *name, HRESULT result)
{
/*
  (*OutStream) << endl;
  if (result != S_OK)
    (*OutStream) << "Error: " << name << " is not supported archive" << endl;
*/
  return S_OK;
}

HRESULT CUpdateCallbackConsole::StartScanning()
{
//(*OutStream) << kScanningMessage;
  return S_OK;
}

HRESULT CUpdateCallbackConsole::CanNotFindError(const wchar_t *name, DWORD systemError)
{
  CantFindFiles.Add(name);
  CantFindCodes.Add(systemError);
  // m_PercentPrinter.ClosePrint();
  if (!m_WarningsMode)
  {
//  (*OutStream) << endl << endl;
//  m_PercentPrinter.PrintNewLine();
    m_WarningsMode = true;
  }
/*
  m_PercentPrinter.PrintString(name);
  m_PercentPrinter.PrintString(":  WARNING: ");
  m_PercentPrinter.PrintString(NError::MyFormatMessageW(systemError));
  m_PercentPrinter.PrintNewLine();
*/
  return S_OK;
}

HRESULT CUpdateCallbackConsole::FinishScanning()
{
//(*OutStream) << endl << endl;
  return S_OK;
}

HRESULT CUpdateCallbackConsole::StartArchive(const wchar_t *name, bool updating)
{
/*
  if(updating)
    (*OutStream) << kUpdatingArchiveMessage;
  else
    (*OutStream) << kCreatingArchiveMessage;
  if (name != 0)
    (*OutStream) << name;
  else
    (*OutStream) << "StdOut";
  (*OutStream) << endl << endl;
*/
  return S_OK;
}

HRESULT CUpdateCallbackConsole::FinishArchive()
{
//(*OutStream) << endl;
  return S_OK;
}

HRESULT CUpdateCallbackConsole::CheckBreak()
{
/*
  if (NConsoleClose::TestBreakSignal())
    return E_ABORT;
*/
  return S_OK;
}

HRESULT CUpdateCallbackConsole::Finilize()
{
  // NSynchronization::CCriticalSectionLock lock(g_CriticalSection);
  if (m_NeedBeClosed)
  {
    if (EnablePercents)
    {
//    m_PercentPrinter.ClosePrint();
      m_PercentCanBePrint = false;
    }
    if (!StdOutMode) {
//    m_PercentPrinter.PrintNewLine();
    }
    m_NeedBeClosed = false;
  }
  return S_OK;
}

HRESULT CUpdateCallbackConsole::SetTotal(UInt64 size)
{
  // NSynchronization::CCriticalSectionLock lock(g_CriticalSection);
  if (EnablePercents) {
//  m_PercentPrinter.SetTotal(size);
  }
/*
char buf[128];
wsprintf(buf, "SetTotal(%d)\n", (int)size);
OutputDebugString(buf);
*/
  return S_OK;
}

HRESULT CUpdateCallbackConsole::SetCompleted(const UInt64 *completeValue)
{
  // NSynchronization::CCriticalSectionLock lock(g_CriticalSection);
  if (completeValue != NULL)
  {
    if (EnablePercents)
    {
//    m_PercentPrinter.SetRatio(*completeValue);
      if (m_PercentCanBePrint) {
//      m_PercentPrinter.PrintRatio();
      }
    }
  }
/*
  if (NConsoleClose::TestBreakSignal())
    return E_ABORT;
*/
/*
char buf[128];
wsprintf(buf, "SetCompleted(%d)\n", (int)*completeValue);
OutputDebugString(buf);
*/
  return S_OK;
}

HRESULT CUpdateCallbackConsole::GetStream(const wchar_t *name, bool isAnti)
{
  // NSynchronization::CCriticalSectionLock lock(g_CriticalSection);
  if (StdOutMode)
    return S_OK;
  if(isAnti) {
//  m_PercentPrinter.PrintString("Anti item    ");
  } else {
//  m_PercentPrinter.PrintString("Compressing  ");
  }
  if (name[0] == 0)
    name = kEmptyFileAlias;
//m_PercentPrinter.PrintString(name);
  if (EnablePercents)
  {
    m_PercentCanBePrint = true;
//  m_PercentPrinter.PreparePrint();
//  m_PercentPrinter.RePrintRatio();
  }
  return S_OK;
}

HRESULT CUpdateCallbackConsole::OpenFileError(const wchar_t *name, DWORD systemError)
{
  // NSynchronization::CCriticalSectionLock lock(g_CriticalSection);
  FailedCodes.Add(systemError);
  FailedFiles.Add(name);
  // if (systemError == ERROR_SHARING_VIOLATION)
  {
/*
    m_PercentPrinter.ClosePrint();
    m_PercentPrinter.PrintNewLine();
    m_PercentPrinter.PrintString("WARNING: ");
    m_PercentPrinter.PrintString(NError::MyFormatMessageW(systemError));
*/
    return S_FALSE;
  }
  // return systemError;
}

HRESULT CUpdateCallbackConsole::SetOperationResult(Int32 )
{
  m_NeedBeClosed = true;
  return S_OK;
}

HRESULT CUpdateCallbackConsole::CryptoGetTextPassword2(Int32 *passwordIsDefined, BSTR *password)
{
  if (!PasswordIsDefined)
  {
    if (AskPassword)
    {
/*
      Password = GetPassword(OutStream);
      PasswordIsDefined = true;
*/
      return E_FAIL;
    }
  }
  *passwordIsDefined = BoolToInt(PasswordIsDefined);
  CMyComBSTR tempName(Password);
  *password = tempName;
  return S_OK;
}
