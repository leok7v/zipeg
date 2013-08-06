// ChmHandler.cpp

#include "StdAfx.h"

#include "Common/ComTry.h"
#include "Common/Defs.h"
#include "Common/StringConvert.h"
#include "Common/UTFConvert.h"

#include "Windows/PropVariant.h"
#include "Windows/Time.h"

#include "../../Common/LimitedStreams.h"
#include "../../Common/ProgressUtils.h"
#include "../../Common/StreamUtils.h"

#include "../../Compress/CopyCoder.h"
#include "../../Compress/LzxDecoder.h"

#include "../Common/ItemNameUtils.h"

#include "ChmHandler.h"

using namespace NWindows;
using namespace NTime;

namespace NArchive {
namespace NChm {

// #define _CHM_DETAILS

#ifdef _CHM_DETAILS

enum
{
  kpidSection = kpidUserDefined
};

#endif

STATPROPSTG kProps[] =
{
  { NULL, kpidPath, VT_BSTR},
  { NULL, kpidSize, VT_UI8},
  { NULL, kpidMethod, VT_BSTR},
  { NULL, kpidBlock, VT_UI4}
  
  #ifdef _CHM_DETAILS
  ,
  { L"Section", kpidSection, VT_UI4},
  { NULL, kpidOffset, VT_UI4}
  #endif
};

STATPROPSTG kArcProps[] =
{
  { NULL, kpidNumBlocks, VT_UI8}
};

IMP_IInArchive_Props

IMP_IInArchive_ArcProps_NO
/*
IMP_IInArchive_ArcProps

STDMETHODIMP CHandler::GetArchiveProperty(PROPID propID, PROPVARIANT *value)
{
  COM_TRY_BEGIN
  NWindows::NCOM::CPropVariant prop;
  switch(propID)
  {
    case kpidNumBlocks:
    {
      UInt64 numBlocks = 0;
      for (int i = 0; i < m_Database.Sections.Size(); i++)
      {
        const CSectionInfo &s = m_Database.Sections[i];
        for (int j = 0; j < s.Methods.Size(); j++)
        {
          const CMethodInfo &m = s.Methods[j];
          if (m.IsLzx())
            numBlocks += m.LzxInfo.ResetTable.GetNumBlocks();
        }
      }
      prop = numBlocks;
      break;
    }
  }
  prop.Detach(value);
  return S_OK;
  COM_TRY_END
}
*/

STDMETHODIMP CHandler::GetProperty(UInt32 index, PROPID propID,  PROPVARIANT *value)
{
  COM_TRY_BEGIN
  NWindows::NCOM::CPropVariant prop;
  if (m_Database.NewFormat)
  {
    switch(propID)
    {
      case kpidSize:
        prop = (UInt64)m_Database.NewFormatString.Length();
      break;
    }
    prop.Detach(value);
    return S_OK;
  }
  int entryIndex;
  if (m_Database.LowLevel)
    entryIndex = index;
  else
    entryIndex = m_Database.Indices[index];
  const CItem &item = m_Database.Items[entryIndex];
  switch(propID)
  {
    case kpidPath:
    {
      UString us;
      if (ConvertUTF8ToUnicode(item.Name, us))
      {
        if (!m_Database.LowLevel)
        {
          if (us.Length() > 1)
            if (us[0] == L'/')
              us.Delete(0);
        }
        prop = NItemName::GetOSName2(us);
      }
      break;
    }
    case kpidIsDir:  prop = item.IsDir(); break;
    case kpidSize:  prop = item.Size; break;
    case kpidMethod:
    {
      if (!item.IsDir())
        if (item.Section == 0)
          prop = L"Copy";
        else if (item.Section < m_Database.Sections.Size())
          prop = m_Database.Sections[(int)item.Section].GetMethodName();
      break;
    }
    case kpidBlock:
      if (m_Database.LowLevel)
        prop = item.Section;
      else if (item.Section != 0)
        prop = m_Database.GetFolder(index);
      break;
    
    #ifdef _CHM_DETAILS
    
    case kpidSection:  prop = (UInt32)item.Section; break;
    case kpidOffset:  prop = (UInt32)item.Offset; break;

    #endif
  }
  prop.Detach(value);
  return S_OK;
  COM_TRY_END
}

class CProgressImp: public CProgressVirt
{
  CMyComPtr<IArchiveOpenCallback> _callback;
public:
  STDMETHOD(SetTotal)(const UInt64 *numFiles);
  STDMETHOD(SetCompleted)(const UInt64 *numFiles);
  CProgressImp(IArchiveOpenCallback *callback): _callback(callback) {};
};

STDMETHODIMP CProgressImp::SetTotal(const UInt64 *numFiles)
{
  if (_callback)
    return _callback->SetCompleted(numFiles, NULL);
  return S_OK;
}

STDMETHODIMP CProgressImp::SetCompleted(const UInt64 *numFiles)
{
  if (_callback)
    return _callback->SetCompleted(numFiles, NULL);
  return S_OK;
}

STDMETHODIMP CHandler::Open(IInStream *inStream,
    const UInt64 *maxCheckStartPosition,
    IArchiveOpenCallback * /* openArchiveCallback */)
{
  COM_TRY_BEGIN
  m_Stream.Release();
  try
  {
    CInArchive archive;
    // CProgressImp progressImp(openArchiveCallback);
    RINOK(archive.Open(inStream, maxCheckStartPosition, m_Database));
    /*
    if (m_Database.LowLevel)
      return S_FALSE;
    */
    m_Stream = inStream;
  }
  catch(...)
  {
    return S_FALSE;
  }
  return S_OK;
  COM_TRY_END
}

STDMETHODIMP CHandler::Close()
{
  m_Database.Clear();
  m_Stream.Release();
  return S_OK;
}

class CChmFolderOutStream:
  public ISequentialOutStream,
  public CMyUnknownImp
{
public:
  MY_UNKNOWN_IMP

  HRESULT Write2(const void *data, UInt32 size, UInt32 *processedSize, bool isOK);
  STDMETHOD(Write)(const void *data, UInt32 size, UInt32 *processedSize);

  UInt64 m_FolderSize;
  UInt64 m_PosInFolder;
  UInt64 m_PosInSection;
  const CRecordVector<bool> *m_ExtractStatuses;
  int m_StartIndex;
  int m_CurrentIndex;
  int m_NumFiles;

private:
  const CFilesDatabase *m_Database;
  CMyComPtr<IArchiveExtractCallback> m_ExtractCallback;
  bool m_TestMode;

  bool m_IsOk;
  bool m_FileIsOpen;
  UInt64 m_RemainFileSize;
  CMyComPtr<ISequentialOutStream> m_RealOutStream;

  HRESULT OpenFile();
  HRESULT WriteEmptyFiles();
public:
  void Init(
    const CFilesDatabase *database,
    IArchiveExtractCallback *extractCallback,
    bool testMode);
  HRESULT FlushCorrupted(UInt64 maxSize);
};

void CChmFolderOutStream::Init(
    const CFilesDatabase *database,
    IArchiveExtractCallback *extractCallback,
    bool testMode)
{
  m_Database = database;
  m_ExtractCallback = extractCallback;
  m_TestMode = testMode;

  m_CurrentIndex = 0;
  m_FileIsOpen = false;
}

HRESULT CChmFolderOutStream::OpenFile()
{
  Int32 askMode = (*m_ExtractStatuses)[m_CurrentIndex] ? (m_TestMode ?
      NExtract::NAskMode::kTest :
      NExtract::NAskMode::kExtract) :
      NExtract::NAskMode::kSkip;
  m_RealOutStream.Release();
  RINOK(m_ExtractCallback->GetStream(m_StartIndex + m_CurrentIndex, &m_RealOutStream, askMode));
  if (!m_RealOutStream && !m_TestMode)
    askMode = NExtract::NAskMode::kSkip;
  return m_ExtractCallback->PrepareOperation(askMode);
}

HRESULT CChmFolderOutStream::WriteEmptyFiles()
{
  if (m_FileIsOpen)
    return S_OK;
  for (;m_CurrentIndex < m_NumFiles; m_CurrentIndex++)
  {
    UInt64 fileSize = m_Database->GetFileSize(m_StartIndex + m_CurrentIndex);
    if (fileSize != 0)
      return S_OK;
    HRESULT result = OpenFile();
    m_RealOutStream.Release();
    RINOK(result);
    RINOK(m_ExtractCallback->SetOperationResult(NExtract::NOperationResult::kOK));
  }
  return S_OK;
}

// This is WritePart function
HRESULT CChmFolderOutStream::Write2(const void *data, UInt32 size, UInt32 *processedSize, bool isOK)
{
  UInt32 realProcessed = 0;
  if (processedSize != NULL)
   *processedSize = 0;
  while(size != 0)
  {
    if (m_FileIsOpen)
    {
      UInt32 numBytesToWrite = (UInt32)MyMin(m_RemainFileSize, (UInt64)(size));
      HRESULT res = S_OK;
      if (numBytesToWrite > 0)
      {
        if (!isOK)
          m_IsOk = false;
        if (m_RealOutStream)
        {
          UInt32 processedSizeLocal = 0;
          res = m_RealOutStream->Write((const Byte *)data, numBytesToWrite, &processedSizeLocal);
          numBytesToWrite = processedSizeLocal;
        }
      }
      realProcessed += numBytesToWrite;
      if (processedSize != NULL)
        *processedSize = realProcessed;
      data = (const void *)((const Byte *)data + numBytesToWrite);
      size -= numBytesToWrite;
      m_RemainFileSize -= numBytesToWrite;
      m_PosInSection += numBytesToWrite;
      m_PosInFolder += numBytesToWrite;
      if (res != S_OK)
        return res;
      if (m_RemainFileSize == 0)
      {
        m_RealOutStream.Release();
        RINOK(m_ExtractCallback->SetOperationResult(
          m_IsOk ?
            NExtract::NOperationResult::kOK:
            NExtract::NOperationResult::kDataError));
        m_FileIsOpen = false;
      }
      if (realProcessed > 0)
        break; // with this break this function works as write part
    }
    else
    {
      if (m_CurrentIndex >= m_NumFiles)
        return E_FAIL;
      int fullIndex = m_StartIndex + m_CurrentIndex;
      m_RemainFileSize = m_Database->GetFileSize(fullIndex);
      UInt64 fileOffset = m_Database->GetFileOffset(fullIndex);
      if (fileOffset < m_PosInSection)
        return E_FAIL;
      if (fileOffset > m_PosInSection)
      {
        UInt32 numBytesToWrite = (UInt32)MyMin(fileOffset - m_PosInSection, UInt64(size));
        realProcessed += numBytesToWrite;
        if (processedSize != NULL)
          *processedSize = realProcessed;
        data = (const void *)((const Byte *)data + numBytesToWrite);
        size -= numBytesToWrite;
        m_PosInSection += numBytesToWrite;
        m_PosInFolder += numBytesToWrite;
      }
      if (fileOffset == m_PosInSection)
      {
        RINOK(OpenFile());
        m_FileIsOpen = true;
        m_CurrentIndex++;
        m_IsOk = true;
      }
    }
  }
  return WriteEmptyFiles();
}

STDMETHODIMP CChmFolderOutStream::Write(const void *data, UInt32 size, UInt32 *processedSize)
{
  return Write2(data, size, processedSize, true);
}

HRESULT CChmFolderOutStream::FlushCorrupted(UInt64 maxSize)
{
  const UInt32 kBufferSize = (1 << 10);
  Byte buffer[kBufferSize];
  for (int i = 0; i < kBufferSize; i++)
    buffer[i] = 0;
  if (maxSize > m_FolderSize)
    maxSize = m_FolderSize;
  while (m_PosInFolder < maxSize)
  {
    UInt32 size = (UInt32)MyMin(maxSize - m_PosInFolder, (UInt64)kBufferSize);
    UInt32 processedSizeLocal = 0;
    RINOK(Write2(buffer, size, &processedSizeLocal, false));
    if (processedSizeLocal == 0)
      return S_OK;
  }
  return S_OK;
}


STDMETHODIMP CHandler::Extract(const UInt32 *indices, UInt32 numItems,
    Int32 testModeSpec, IArchiveExtractCallback *extractCallback)
{
  COM_TRY_BEGIN
  bool allFilesMode = (numItems == (UInt32)-1);

  if (allFilesMode)
    numItems = m_Database.NewFormat ? 1:
      (m_Database.LowLevel ?
      m_Database.Items.Size():
      m_Database.Indices.Size());
  if (numItems == 0)
    return S_OK;
  bool testMode = (testModeSpec != 0);

  UInt64 currentTotalSize = 0;

  NCompress::CCopyCoder *copyCoderSpec = new NCompress::CCopyCoder();
  CMyComPtr<ICompressCoder> copyCoder = copyCoderSpec;
  UInt32 i;

  CLocalProgress *lps = new CLocalProgress;
  CMyComPtr<ICompressProgressInfo> progress = lps;
  lps->Init(extractCallback, false);

  CLimitedSequentialInStream *streamSpec = new CLimitedSequentialInStream;
  CMyComPtr<ISequentialInStream> inStream(streamSpec);
  streamSpec->SetStream(m_Stream);

  if (m_Database.LowLevel)
  {
    UInt64 currentItemSize = 0;
    UInt64 totalSize = 0;
    if (m_Database.NewFormat)
      totalSize = m_Database.NewFormatString.Length();
    else
      for (i = 0; i < numItems; i++)
        totalSize += m_Database.Items[allFilesMode ? i : indices[i]].Size;
    extractCallback->SetTotal(totalSize);
    
    for (i = 0; i < numItems; i++, currentTotalSize += currentItemSize)
    {
      currentItemSize = 0;
      lps->InSize = currentTotalSize; // Change it
      lps->OutSize = currentTotalSize;

      RINOK(lps->SetCur());
      CMyComPtr<ISequentialOutStream> realOutStream;
      Int32 askMode= testMode ?
          NExtract::NAskMode::kTest :
          NExtract::NAskMode::kExtract;
      Int32 index = allFilesMode ? i : indices[i];
      RINOK(extractCallback->GetStream(index, &realOutStream, askMode));

      if (m_Database.NewFormat)
      {
        if (index != 0)
          return E_FAIL;
        if (!testMode && !realOutStream)
          continue;
        if (!testMode)
        {
          UInt32 size = m_Database.NewFormatString.Length();
          RINOK(WriteStream(realOutStream, (const char *)m_Database.NewFormatString, size));
        }
        RINOK(extractCallback->SetOperationResult(NExtract::NOperationResult::kOK));
        continue;
      }
      const CItem &item = m_Database.Items[index];
      
      currentItemSize = item.Size;
      
      if (!testMode && !realOutStream)
        continue;
      RINOK(extractCallback->PrepareOperation(askMode));
      if (item.Section != 0)
      {
        RINOK(extractCallback->SetOperationResult(NExtract::NOperationResult::kUnSupportedMethod));
        continue;
      }

      if (testMode)
      {
        RINOK(extractCallback->SetOperationResult(NExtract::NOperationResult::kOK));
        continue;
      }
      
      RINOK(m_Stream->Seek(m_Database.ContentOffset + item.Offset, STREAM_SEEK_SET, NULL));
      streamSpec->Init(item.Size);
      
      RINOK(copyCoder->Code(inStream, realOutStream, NULL, NULL, progress));
      realOutStream.Release();
      RINOK(extractCallback->SetOperationResult((copyCoderSpec->TotalSize == item.Size) ?
          NExtract::NOperationResult::kOK:
          NExtract::NOperationResult::kDataError));
    }
    return S_OK;
  }
  
  UInt64 lastFolderIndex = ((UInt64)0 - 1);
  for (i = 0; i < numItems; i++)
  {
    UInt32 index = allFilesMode ? i : indices[i];
    int entryIndex = m_Database.Indices[index];
    const CItem &item = m_Database.Items[entryIndex];
    UInt64 sectionIndex = item.Section;
    if (item.IsDir() || item.Size == 0)
      continue;
    if (sectionIndex == 0)
    {
      currentTotalSize += item.Size;
      continue;
    }
    const CSectionInfo &section = m_Database.Sections[(int)item.Section];
    if (section.IsLzx())
    {
      const CLzxInfo &lzxInfo = section.Methods[0].LzxInfo;
      UInt64 folderIndex = m_Database.GetFolder(index);
      if (lastFolderIndex == folderIndex)
        folderIndex++;
      lastFolderIndex = m_Database.GetLastFolder(index);
      for (; folderIndex <= lastFolderIndex; folderIndex++)
        currentTotalSize += lzxInfo.GetFolderSize();
    }
  }

  RINOK(extractCallback->SetTotal(currentTotalSize));

  NCompress::NLzx::CDecoder *lzxDecoderSpec = 0;
  CMyComPtr<ICompressCoder> lzxDecoder;
  CChmFolderOutStream *chmFolderOutStream = 0;
  CMyComPtr<ISequentialOutStream> outStream;

  currentTotalSize = 0;

  CRecordVector<bool> extractStatuses;
  for (i = 0; i < numItems;)
  {
    RINOK(extractCallback->SetCompleted(&currentTotalSize));
    UInt32 index = allFilesMode ? i : indices[i];
    i++;
    int entryIndex = m_Database.Indices[index];
    const CItem &item = m_Database.Items[entryIndex];
    UInt64 sectionIndex = item.Section;
    Int32 askMode= testMode ?
        NExtract::NAskMode::kTest :
        NExtract::NAskMode::kExtract;
    if (item.IsDir())
    {
      CMyComPtr<ISequentialOutStream> realOutStream;
      RINOK(extractCallback->GetStream(index, &realOutStream, askMode));
      RINOK(extractCallback->PrepareOperation(askMode));
      realOutStream.Release();
      RINOK(extractCallback->SetOperationResult(NExtract::NOperationResult::kOK));
      continue;
    }

    lps->InSize = currentTotalSize; // Change it
    lps->OutSize = currentTotalSize;

    if (item.Size == 0 || sectionIndex == 0)
    {
      CMyComPtr<ISequentialOutStream> realOutStream;
      RINOK(extractCallback->GetStream(index, &realOutStream, askMode));
      if (!testMode && !realOutStream)
        continue;
      RINOK(extractCallback->PrepareOperation(askMode));
      Int32 opRes = NExtract::NOperationResult::kOK;
      if (!testMode && item.Size != 0)
      {
        RINOK(m_Stream->Seek(m_Database.ContentOffset + item.Offset, STREAM_SEEK_SET, NULL));
        streamSpec->Init(item.Size);
        RINOK(copyCoder->Code(inStream, realOutStream, NULL, NULL, progress));
        if (copyCoderSpec->TotalSize != item.Size)
          opRes = NExtract::NOperationResult::kDataError;
      }
      realOutStream.Release();
      RINOK(extractCallback->SetOperationResult(opRes));
      currentTotalSize += item.Size;
      continue;
    }
  
    const CSectionInfo &section = m_Database.Sections[(int)sectionIndex];

    if (!section.IsLzx())
    {
      CMyComPtr<ISequentialOutStream> realOutStream;
      RINOK(extractCallback->GetStream(index, &realOutStream, askMode));
      if (!testMode && !realOutStream)
        continue;
      RINOK(extractCallback->PrepareOperation(askMode));
      RINOK(extractCallback->SetOperationResult(NExtract::NOperationResult::kUnSupportedMethod));
      continue;
    }

    const CLzxInfo &lzxInfo = section.Methods[0].LzxInfo;

    if (chmFolderOutStream == 0)
    {
      chmFolderOutStream = new CChmFolderOutStream;
      outStream = chmFolderOutStream;
    }

    chmFolderOutStream->Init(&m_Database, extractCallback, testMode);

    if (lzxDecoderSpec == NULL)
    {
      lzxDecoderSpec = new NCompress::NLzx::CDecoder;
      lzxDecoder = lzxDecoderSpec;
    }

    UInt64 folderIndex = m_Database.GetFolder(index);

    UInt64 compressedPos = m_Database.ContentOffset + section.Offset;
    UInt32 numDictBits = lzxInfo.GetNumDictBits();
    RINOK(lzxDecoderSpec->SetParams(numDictBits));

    const CItem *lastItem = &item;
    extractStatuses.Clear();
    extractStatuses.Add(true);

    for (;; folderIndex++)
    {
      RINOK(extractCallback->SetCompleted(&currentTotalSize));

      UInt64 startPos = lzxInfo.GetFolderPos(folderIndex);
      UInt64 finishPos = lastItem->Offset + lastItem->Size;
      UInt64 limitFolderIndex = lzxInfo.GetFolder(finishPos);

      lastFolderIndex = m_Database.GetLastFolder(index);
      UInt64 folderSize = lzxInfo.GetFolderSize();
      UInt64 unPackSize = folderSize;
      if (extractStatuses.IsEmpty())
        chmFolderOutStream->m_StartIndex = index + 1;
      else
        chmFolderOutStream->m_StartIndex = index;
      if (limitFolderIndex == folderIndex)
      {
        for (; i < numItems; i++)
        {
          UInt32 nextIndex = allFilesMode ? i : indices[i];
          int entryIndex = m_Database.Indices[nextIndex];
          const CItem &nextItem = m_Database.Items[entryIndex];
          if (nextItem.Section != sectionIndex)
            break;
          UInt64 nextFolderIndex = m_Database.GetFolder(nextIndex);
          if (nextFolderIndex != folderIndex)
            break;
          for (index++; index < nextIndex; index++)
            extractStatuses.Add(false);
          extractStatuses.Add(true);
          index = nextIndex;
          lastItem = &nextItem;
          if (nextItem.Size != 0)
            finishPos = nextItem.Offset + nextItem.Size;
          lastFolderIndex = m_Database.GetLastFolder(index);
        }
      }
      unPackSize = MyMin(finishPos - startPos, unPackSize);

      chmFolderOutStream->m_FolderSize = folderSize;
      chmFolderOutStream->m_PosInFolder = 0;
      chmFolderOutStream->m_PosInSection = startPos;
      chmFolderOutStream->m_ExtractStatuses = &extractStatuses;
      chmFolderOutStream->m_NumFiles = extractStatuses.Size();
      chmFolderOutStream->m_CurrentIndex = 0;
      try
      {
        UInt64 startBlock = lzxInfo.GetBlockIndexFromFolderIndex(folderIndex);
        const CResetTable &rt = lzxInfo.ResetTable;
        UInt32 numBlocks = (UInt32)rt.GetNumBlocks(unPackSize);
        for (UInt32 b = 0; b < numBlocks; b++)
        {
          UInt64 completedSize = currentTotalSize + chmFolderOutStream->m_PosInSection - startPos;
          RINOK(extractCallback->SetCompleted(&completedSize));
          UInt64 bCur = startBlock + b;
          if (bCur >= rt.ResetOffsets.Size())
            return E_FAIL;
          UInt64 offset = rt.ResetOffsets[(int)bCur];
          UInt64 compressedSize;
          rt.GetCompressedSizeOfBlock(bCur, compressedSize);
          UInt64 rem = finishPos - chmFolderOutStream->m_PosInSection;
          if (rem > rt.BlockSize)
            rem = rt.BlockSize;
          RINOK(m_Stream->Seek(compressedPos + offset, STREAM_SEEK_SET, NULL));
          streamSpec->SetStream(m_Stream);
          streamSpec->Init(compressedSize);
          lzxDecoderSpec->SetKeepHistory(b > 0);
          HRESULT res = lzxDecoder->Code(inStream, outStream, NULL, &rem, NULL);
          if (res != S_OK)
          {
            if (res != S_FALSE)
              return res;
            throw 1;
          }
        }
      }
      catch(...)
      {
        RINOK(chmFolderOutStream->FlushCorrupted(unPackSize));
      }
      currentTotalSize += folderSize;
      if (folderIndex == lastFolderIndex)
        break;
      extractStatuses.Clear();
    }
  }
  return S_OK;
  COM_TRY_END
}

STDMETHODIMP CHandler::GetItemName(UInt32 index, const char* &buf) {
    COM_TRY_BEGIN
    int entryIndex = m_Database.Indices[index];
    const CItem &item = m_Database.Items[entryIndex];
    buf = item.Name;
    return S_OK;
    COM_TRY_END
}
    
STDMETHODIMP CHandler::SetEncoding(Int32 e) {
  _encoding = e;
  return S_OK;
}

STDMETHODIMP CHandler::GetNumberOfItems(UInt32 *numItems)
{
    *numItems = m_Database.NewFormat ? 1:
      (m_Database.LowLevel ?
      m_Database.Items.Size():
      m_Database.Indices.Size());
  return S_OK;
}

}}
