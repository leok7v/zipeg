// MbrHandler.cpp

#include "StdAfx.h"

// #define SHOW_DEBUG_INFO

#ifdef SHOW_DEBUG_INFO
#include <stdio.h>
#endif

#include "../../../C/CpuArch.h"

#include "Common/Buffer.h"
#include "Common/ComTry.h"
#include "Common/IntToString.h"
#include "Common/MyString.h"

#include "Windows/PropVariant.h"

#include "../Common/LimitedStreams.h"
#include "../Common/ProgressUtils.h"
#include "../Common/RegisterArc.h"
#include "../Common/StreamUtils.h"

#include "../Compress/CopyCoder.h"

#ifdef SHOW_DEBUG_INFO
#define PRF(x) x
#else
#define PRF(x)
#endif

using namespace NWindows;

namespace NArchive {
namespace NMbr {

struct CChs
{
  Byte Head;
  Byte SectCyl;
  Byte Cyl8;
  
  UInt32 GetSector() const { return SectCyl & 0x3F; }
  UInt32 GetCyl() const { return ((UInt32)SectCyl >> 6 << 8) | Cyl8; }
  void ToString(NCOM::CPropVariant &prop) const;

  void Parse(const Byte *p)
  {
    Head = p[0];
    SectCyl = p[1];
    Cyl8 = p[2];
  }
  bool Check() const { return GetSector() > 0; }
};

#define RINOZ(x) { int __tt = (x); if (__tt != 0) return __tt; }

static int CompareChs(const CChs &c1, const CChs &c2)
{
  RINOZ(MyCompare(c1.GetCyl(), c2.GetCyl()));
  RINOZ(MyCompare(c1.Head, c2.Head));
  return MyCompare(c1.GetSector(), c2.GetSector());
}

static void AddUIntToString(UInt32 val, AString &res)
{
  char s[16];
  ConvertUInt32ToString(val, s);
  res += s;
}

void CChs::ToString(NCOM::CPropVariant &prop) const
{
  AString s;
  AddUIntToString(GetCyl(), s);
  s += '-';
  AddUIntToString(Head, s);
  s += '-';
  AddUIntToString(GetSector(), s);
  prop = s;
}

struct CPartition
{
  Byte Status;
  CChs BeginChs;
  Byte Type;
  CChs EndChs;
  UInt32 Lba;
  UInt32 NumBlocks;

  CPartition() { memset (this, 0, sizeof(*this)); }
  
  bool IsEmpty() const { return Type == 0; }
  bool IsExtended() const { return Type == 5 || Type == 0xF; }
  UInt32 GetLimit() const { return Lba + NumBlocks; }
  // bool IsActive() const { return Status == 0x80; }
  UInt64 GetPos() const { return (UInt64)Lba * 512; }
  UInt64 GetSize() const { return (UInt64)NumBlocks * 512; }

  bool CheckLbaLimits() const { return (UInt32)0xFFFFFFFF - Lba >= NumBlocks; }
  bool Parse(const Byte *p)
  {
    Status = p[0];
    BeginChs.Parse(p + 1);
    Type = p[4];
    EndChs.Parse(p + 5);
    Lba = GetUi32(p + 8);
    NumBlocks = GetUi32(p + 12);
    if (Type == 0)
      return true;
    if (Status != 0 && Status != 0x80)
      return false;
    return
       BeginChs.Check() &&
       EndChs.Check() &&
       CompareChs(BeginChs, EndChs) <= 0 &&
       NumBlocks > 0 &&
       CheckLbaLimits();
  }

  #ifdef SHOW_DEBUG_INFO
  void Print() const
  {
    NCOM::CPropVariant prop, prop2;
    BeginChs.ToString(prop);
    EndChs.ToString(prop2);
    printf("   %2x %2x %8X %8X %12S %12S", (int)Status, (int)Type, Lba, NumBlocks, prop.bstrVal, prop2.bstrVal);
  }
  #endif
};

struct CPartType
{
  UInt32 Id;
  const char *Ext;
  const char *Name;
};

static const char *kFat = "fat";

static const CPartType kPartTypes[] =
{
  { 0x01, kFat, "FAT12" },
  { 0x04, kFat, "FAT16 DOS 3.0+" },
  { 0x05, 0, "Extended" },
  { 0x06, kFat, "FAT16 DOS 3.31+" },
  { 0x07, "ntfs", "NTFS" },
  { 0x0B, kFat, "FAT32" },
  { 0x0C, kFat, "FAT32-LBA" },
  { 0x0E, kFat, "FAT16-LBA" },
  { 0x0F, 0, "Extended-LBA" },
  { 0x11, kFat, "FAT12-Hidden" },
  { 0x14, kFat, "FAT16-Hidden < 32 MB" },
  { 0x16, kFat, "FAT16-Hidden >= 32 MB" },
  { 0x1B, kFat, "FAT32-Hidden" },
  { 0x1C, kFat, "FAT32-LBA-Hidden" },
  { 0x1E, kFat, "FAT16-LBA-WIN95-Hidden" },
  { 0x82, 0, "Solaris x86 / Linux swap" },
  { 0x83, 0, "Linux" },
  { 0xBE, 0, "Solaris 8 boot" },
  { 0xBF, 0, "New Solaris x86" },
  { 0xC2, 0, "Linux-Hidden" },
  { 0xC3, 0, "Linux swap-Hidden" },
  { 0xEE, 0, "EFI-MBR" },
  { 0xEE, 0, "EFI" }
};

static int FindPartType(UInt32 type)
{
  for (int i = 0; i < sizeof(kPartTypes) / sizeof(kPartTypes[0]); i++)
    if (kPartTypes[i].Id == type)
      return i;
  return -1;
}

struct CItem
{
  bool IsReal;
  bool IsPrim;
  UInt64 Size;
  CPartition Part;
};

class CHandler:
  public IInArchive,
  public IInArchiveGetStream,
  public CMyUnknownImp
{
  CMyComPtr<IInStream> _stream;
  CObjectVector<CItem> _items;
  UInt64 _totalSize;
  CByteBuffer _buffer;
  Int32 _encoding = CP_UTF8;

  HRESULT ReadTables(IInStream *stream, UInt32 baseLba, UInt32 lba, int level);
public:
  MY_UNKNOWN_IMP2(IInArchive, IInArchiveGetStream)
  INTERFACE_IInArchive(;)
  STDMETHOD(GetStream)(UInt32 index, ISequentialInStream **stream);
};

HRESULT CHandler::ReadTables(IInStream *stream, UInt32 baseLba, UInt32 lba, int level)
{
  if (level >= 128 || _items.Size() >= 128)
    return S_FALSE;

  const int kNumHeaderParts = 4;
  CPartition parts[kNumHeaderParts];

  {
    const UInt32 kSectorSize = 512;
    _buffer.SetCapacity(kSectorSize);
    Byte *buf = _buffer;
    UInt64 newPos = (UInt64)lba << 9;
    if (newPos + 512 > _totalSize)
      return S_FALSE;
    RINOK(stream->Seek(newPos, STREAM_SEEK_SET, NULL));
    RINOK(ReadStream_FALSE(stream, buf, kSectorSize));
    
    if (buf[0x1FE] != 0x55 || buf[0x1FF] != 0xAA)
      return S_FALSE;
    
    for (int i = 0; i < kNumHeaderParts; i++)
      if (!parts[i].Parse(buf + 0x1BE + 16 * i))
        return S_FALSE;
  }

  PRF(printf("\n# %8X", lba));

  UInt32 limLba = lba + 1;
  if (limLba == 0)
    return S_FALSE;

  for (int i = 0; i < kNumHeaderParts; i++)
  {
    CPartition &part = parts[i];
    
    if (part.IsEmpty())
      continue;
    PRF(printf("\n   %2d ", (int)level));
    #ifdef SHOW_DEBUG_INFO
    part.Print();
    #endif
    
    int numItems = _items.Size();
    UInt32 newLba = lba + part.Lba;
    
    if (part.IsExtended())
    {
      // if (part.Type == 5) // Check it!
      newLba = baseLba + part.Lba;
      if (newLba < limLba)
        return S_FALSE;
      HRESULT res = ReadTables(stream, level < 1 ? newLba : baseLba, newLba, level + 1);
      if (res != S_FALSE && res != S_OK)
        return res;
    }
    if (newLba < limLba)
      return S_FALSE;
    part.Lba = newLba;
    if (!part.CheckLbaLimits())
      return S_FALSE;

    CItem n;
    n.Part = part;
    bool addItem = false;
    if (numItems == _items.Size())
    {
      n.IsPrim = (level == 0);
      n.IsReal = true;
      addItem = true;
    }
    else
    {
      const CItem &back = _items.Back();
      UInt32 backLimit = back.Part.GetLimit();
      UInt32 partLimit = part.GetLimit();
      if (backLimit < partLimit)
      {
        n.IsReal = false;
        n.Part.Lba = backLimit;
        n.Part.NumBlocks = partLimit - backLimit;
        addItem = true;
      }
    }
    if (addItem)
    {
      if (n.Part.GetLimit() < limLba)
        return S_FALSE;
      limLba = n.Part.GetLimit();
      n.Size = n.Part.GetSize();
      _items.Add(n);
    }
  }
  return S_OK;
}

STDMETHODIMP CHandler::Open(IInStream *stream,
    const UInt64 * /* maxCheckStartPosition */,
    IArchiveOpenCallback * /* openArchiveCallback */)
{
  COM_TRY_BEGIN
  Close();
  RINOK(stream->Seek(0, STREAM_SEEK_END, &_totalSize));
  RINOK(ReadTables(stream, 0, 0, 0));
  if (_items.IsEmpty())
    return S_FALSE;
  UInt32 lbaLimit = _items.Back().Part.GetLimit();
  UInt64 lim = (UInt64)lbaLimit << 9;
  if (lim < _totalSize)
  {
    CItem n;
    n.Part.Lba = lbaLimit;
    n.Size = _totalSize - lim;
    n.IsReal = false;
    _items.Add(n);
  }
  _stream = stream;
  return S_OK;
  COM_TRY_END
}

STDMETHODIMP CHandler::Close()
{
  _items.Clear();
  _stream.Release();
  return S_OK;
}

enum
{
  kpidPrimary = kpidUserDefined,
  kpidBegChs,
  kpidEndChs
};

STATPROPSTG kProps[] =
{
  { NULL, kpidPath, VT_BSTR},
  { NULL, kpidSize, VT_UI8},
  { NULL, kpidFileSystem, VT_BSTR},
  { NULL, kpidOffset, VT_UI8},
  { L"Primary", kpidPrimary, VT_BOOL},
  { L"Begin CHS", kpidBegChs, VT_BSTR},
  { L"End CHS", kpidEndChs, VT_BSTR}
};

IMP_IInArchive_Props_WITH_NAME
IMP_IInArchive_ArcProps_NO_Table

STDMETHODIMP CHandler::GetArchiveProperty(PROPID propID, PROPVARIANT *value)
{
  NCOM::CPropVariant prop;
  switch(propID)
  {
    case kpidMainSubfile:
    {
      int mainIndex = -1;
      for (int i = 0; i < _items.Size(); i++)
        if (_items[i].IsReal)
        {
          if (mainIndex >= 0)
          {
            mainIndex = -1;
            break;
          }
          mainIndex = i;
        }
      if (mainIndex >= 0)
        prop = (UInt32)mainIndex;
      break;
    }
  }
  prop.Detach(value);
  return S_OK;
}
    
STDMETHODIMP CHandler::GetItemName(UInt32 index, const char* &buf) {
    return E_FAIL;
}
    
STDMETHODIMP CHandler::SetEncoding(Int32 e) {
  _encoding = e;
  return S_OK;
}

STDMETHODIMP CHandler::GetNumberOfItems(UInt32 *numItems)
{
  *numItems = _items.Size();
  return S_OK;
}

STDMETHODIMP CHandler::GetProperty(UInt32 index, PROPID propID, PROPVARIANT *value)
{
  COM_TRY_BEGIN
  NCOM::CPropVariant prop;

  const CItem &item = _items[index];
  const CPartition &part = item.Part;
  switch(propID)
  {
    case kpidPath:
    {
      AString s;
      AddUIntToString(index, s);
      if (item.IsReal)
      {
        int typeIndex = FindPartType(part.Type);
        s += '.';
        const char *ext = "img";
        if (typeIndex >= 0 && kPartTypes[typeIndex].Ext != 0)
          ext = kPartTypes[typeIndex].Ext;
        s += ext;
      }
      prop = s;
      break;
    }
    case kpidFileSystem:
      if (item.IsReal)
      {
        char s[32];
        ConvertUInt32ToString(part.Type, s);
        const char *res = s;
        int typeIndex = FindPartType(part.Type);
        if (typeIndex >= 0 && kPartTypes[typeIndex].Name)
          res = kPartTypes[typeIndex].Name;
        prop = res;
      }
      break;
    case kpidSize: prop = item.Size; break;;
    case kpidPackSize: prop = item.Size; break;
    case kpidOffset: prop = part.GetPos(); break;
    case kpidPrimary: if (item.IsReal) prop = item.IsPrim; break;
    case kpidBegChs: if (item.IsReal) part.BeginChs.ToString(prop); break;
    case kpidEndChs: if (item.IsReal) part.EndChs.ToString(prop); break;
  }
  prop.Detach(value);
  return S_OK;
  COM_TRY_END
}

STDMETHODIMP CHandler::Extract(const UInt32 *indices, UInt32 numItems,
    Int32 testMode, IArchiveExtractCallback *extractCallback)
{
  COM_TRY_BEGIN
  bool allFilesMode = (numItems == (UInt32)-1);
  if (allFilesMode)
    numItems = _items.Size();
  if (numItems == 0)
    return S_OK;
  UInt64 totalSize = 0;
  UInt32 i;
  for (i = 0; i < numItems; i++)
    totalSize += _items[allFilesMode ? i : indices[i]].Size;
  extractCallback->SetTotal(totalSize);

  totalSize = 0;
  
  NCompress::CCopyCoder *copyCoderSpec = new NCompress::CCopyCoder();
  CMyComPtr<ICompressCoder> copyCoder = copyCoderSpec;

  CLocalProgress *lps = new CLocalProgress;
  CMyComPtr<ICompressProgressInfo> progress = lps;
  lps->Init(extractCallback, false);

  CLimitedSequentialInStream *streamSpec = new CLimitedSequentialInStream;
  CMyComPtr<ISequentialInStream> inStream(streamSpec);
  streamSpec->SetStream(_stream);

  for (i = 0; i < numItems; i++)
  {
    lps->InSize = totalSize;
    lps->OutSize = totalSize;
    RINOK(lps->SetCur());
    CMyComPtr<ISequentialOutStream> outStream;
    Int32 askMode = testMode ?
        NExtract::NAskMode::kTest :
        NExtract::NAskMode::kExtract;
    Int32 index = allFilesMode ? i : indices[i];
    const CItem &item = _items[index];
    const CPartition &part = item.Part;
    RINOK(extractCallback->GetStream(index, &outStream, askMode));
    totalSize += item.Size;
    if (!testMode && !outStream)
      continue;
    RINOK(extractCallback->PrepareOperation(askMode));

    RINOK(_stream->Seek(part.GetPos(), STREAM_SEEK_SET, NULL));
    streamSpec->Init(item.Size);
    RINOK(copyCoder->Code(inStream, outStream, NULL, NULL, progress));
    outStream.Release();
    RINOK(extractCallback->SetOperationResult(copyCoderSpec->TotalSize == item.Size ?
        NExtract::NOperationResult::kOK:
        NExtract::NOperationResult::kDataError));
  }
  return S_OK;
  COM_TRY_END
}

STDMETHODIMP CHandler::GetStream(UInt32 index, ISequentialInStream **stream)
{
  COM_TRY_BEGIN
  const CItem &item = _items[index];
  return CreateLimitedInStream(_stream, item.Part.GetPos(), item.Size, stream);
  COM_TRY_END
}

static IInArchive *CreateArc() { return new CHandler; }

static CArcInfo g_ArcInfo =
  { L"MBR", L"mbr", 0, 0xDB, { 1, 1, 0 }, 3, false, CreateArc, 0 };

REGISTER_ARC(Mbr)

}}
