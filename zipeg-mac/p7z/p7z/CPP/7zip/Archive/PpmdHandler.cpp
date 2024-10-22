/* PpmdHandler.c -- PPMd format handler
2010-03-10 : Igor Pavlov : Public domain
This code is based on:
  PPMd var.H (2001) / var.I (2002): Dmitry Shkarin : Public domain
  Carryless rangecoder (1999): Dmitry Subbotin : Public domain */

#include "StdAfx.h"

#include "../../../C/CpuArch.h"
#include "../../../C/Alloc.h"
#include "../../../C/Ppmd7.h"
#include "../../../C/Ppmd8.h"

#include "Common/ComTry.h"
#include "Common/IntToString.h"
#include "Common/StringConvert.h"

#include "Windows/PropVariant.h"
#include "Windows/Time.h"

#include "../Common/CWrappers.h"
#include "../Common/ProgressUtils.h"
#include "../Common/RegisterArc.h"
#include "../Common/StreamUtils.h"

using namespace NWindows;

namespace NArchive {
namespace NPpmd {

static void *SzBigAlloc(void *, size_t size) { return BigAlloc(size); }
static void SzBigFree(void *, void *address) { BigFree(address); }
static ISzAlloc g_BigAlloc = { SzBigAlloc, SzBigFree };

static const UInt32 kBufSize = (1 << 20);

struct CBuf
{
  Byte *Buf;
  
  CBuf(): Buf(0) {}
  ~CBuf() { ::MidFree(Buf); }
  bool Alloc()
  {
    if (!Buf)
      Buf = (Byte *)::MidAlloc(kBufSize);
    return (Buf != 0);
  }
};

static const UInt32 kHeaderSize = 16;
static const UInt32 kSignature = 0x84ACAF8F;
static const unsigned kNewHeaderVer = 8;

struct CItem
{
  UInt32 Attrib;
  UInt32 Time;
  AString Name;
  
  unsigned Order;
  unsigned MemInMB;
  unsigned Ver;
  unsigned Restor;

  HRESULT ReadHeader(ISequentialInStream *s, UInt32 &headerSize);
  bool IsSupported() const { return Ver == 7 || (Ver == 8 && Restor <= 1); }
};

HRESULT CItem::ReadHeader(ISequentialInStream *s, UInt32 &headerSize)
{
  Byte h[kHeaderSize];
  RINOK(ReadStream_FALSE(s, h, kHeaderSize));
  if (GetUi32(h) != kSignature)
    return S_FALSE;
  Attrib = GetUi32(h + 4);
  Time = GetUi32(h + 12);
  
  unsigned info = GetUi16(h + 8);
  Order = (info & 0xF) + 1;
  MemInMB = ((info >> 4) & 0xFF) + 1;
  Ver = info >> 12;
 
  UInt32 nameLen = GetUi16(h + 10);
  Restor = nameLen >> 14;
  if (Restor > 2)
    return S_FALSE;
  if (Ver >= kNewHeaderVer)
    nameLen &= 0x3FFF;
  if (nameLen > (1 << 9))
    return S_FALSE;
  char *name = Name.GetBuffer(nameLen + 1);
  HRESULT res = ReadStream_FALSE(s, name, nameLen);
  name[nameLen] = 0;
  headerSize = kHeaderSize + nameLen;
  Name.ReleaseBuffer();
  return res;
}

class CHandler:
  public IInArchive,
  public IArchiveOpenSeq,
  public CMyUnknownImp
{
  CItem _item;
  UInt32 _headerSize;
  UInt64 _packSize;
  bool _packSizeDefined;
  CMyComPtr<ISequentialInStream> _stream;
  Int32 _encoding = CP_UTF8;

public:
  MY_UNKNOWN_IMP2(IInArchive, IArchiveOpenSeq)
  INTERFACE_IInArchive(;)
  STDMETHOD(OpenSeq)(ISequentialInStream *stream);
};

STATPROPSTG kProps[] =
{
  { NULL, kpidPath, VT_BSTR},
  { NULL, kpidMTime, VT_FILETIME},
  { NULL, kpidAttrib, VT_UI4},
  { NULL, kpidMethod, VT_BSTR}
};

IMP_IInArchive_Props
IMP_IInArchive_ArcProps_NO_Table

STDMETHODIMP CHandler::GetArchiveProperty(PROPID propID, PROPVARIANT *value)
{
  NCOM::CPropVariant prop;
  switch(propID)
  {
    case kpidPhySize: if (_packSizeDefined) prop = _packSize; break;
  }
  prop.Detach(value);
  return S_OK;
}
    
STDMETHODIMP CHandler::GetItemName(UInt32 index, const char* &buf) {
    buf = _item.Name;
    return S_OK;
}
    
STDMETHODIMP CHandler::SetEncoding(Int32 e) {
  _encoding = e;
  return S_OK;
}

STDMETHODIMP CHandler::GetNumberOfItems(UInt32 *numItems)
{
  *numItems = 1;
  return S_OK;
}

static void UIntToString(AString &s, const char *prefix, unsigned value)
{
  s += prefix;
  char temp[16];
  ::ConvertUInt32ToString((UInt32)value, temp);
  s += temp;
}

STDMETHODIMP CHandler::GetProperty(UInt32 /* index */, PROPID propID,  PROPVARIANT *value)
{
  COM_TRY_BEGIN
  NWindows::NCOM::CPropVariant prop;
  switch(propID)
  {
    case kpidPath: prop = MultiByteToUnicodeString(_item.Name, CP_ACP); break;
    case kpidMTime:
    {
      FILETIME utc;
      if (NTime::DosTimeToFileTime(_item.Time, utc))
        prop = utc;
      break;
    }
    case kpidAttrib: prop = _item.Attrib; break;
    case kpidPackSize: if (_packSizeDefined) prop = _packSize; break;
    case kpidMethod:
    {
      AString s = "PPMd";
      s += (char)('A' + _item.Ver);
      UIntToString(s, ":o", _item.Order);
      UIntToString(s, ":mem", _item.MemInMB);
      s += 'm';
      if (_item.Ver >= kNewHeaderVer && _item.Restor != 0)
        UIntToString(s, ":r", _item.Restor);
      prop = s;
    }
  }
  prop.Detach(value);
  return S_OK;
  COM_TRY_END
}

STDMETHODIMP CHandler::Open(IInStream *stream, const UInt64 *, IArchiveOpenCallback *)
{
  return OpenSeq(stream);
}

STDMETHODIMP CHandler::OpenSeq(ISequentialInStream *stream)
{
  COM_TRY_BEGIN
  HRESULT res;
  try
  {
    Close();
    res = _item.ReadHeader(stream, _headerSize);
  }
  catch(...) { res = S_FALSE; }
  if (res == S_OK)
    _stream = stream;
  else
    Close();
  return res;
  COM_TRY_END
}

STDMETHODIMP CHandler::Close()
{
  _packSizeDefined = false;
  _stream.Release();
  return S_OK;
}

static const UInt32 kTopValue = (1 << 24);
static const UInt32 kBot = (1 << 15);

struct CRangeDecoder
{
  IPpmd7_RangeDec s;
  UInt32 Range;
  UInt32 Code;
  UInt32 Low;
  CByteInBufWrap *Stream;

public:
  bool Init()
  {
    Code = 0;
    Low = 0;
    Range = 0xFFFFFFFF;
    for (int i = 0; i < 4; i++)
      Code = (Code << 8) | Stream->ReadByte();
    return Code < 0xFFFFFFFF;
  }

  void Normalize()
  {
    while ((Low ^ (Low + Range)) < kTopValue ||
       Range < kBot && ((Range = (0 - Low) & (kBot - 1)), 1))
    {
      Code = (Code << 8) | Stream->ReadByte();
      Range <<= 8;
      Low <<= 8;
    }
  }

  CRangeDecoder();
};


extern "C" {

static UInt32 Range_GetThreshold(void *pp, UInt32 total)
{
  CRangeDecoder *p = (CRangeDecoder *)pp;
  return p->Code / (p->Range /= total);
}

static void Range_Decode(void *pp, UInt32 start, UInt32 size)
{
  CRangeDecoder *p = (CRangeDecoder *)pp;
  start *= p->Range;
  p->Low += start;
  p->Code -= start;
  p->Range *= size;
  p->Normalize();
}

static UInt32 Range_DecodeBit(void *pp, UInt32 size0)
{
  CRangeDecoder *p = (CRangeDecoder *)pp;
  if (p->Code / (p->Range >>= 14) < size0)
  {
    Range_Decode(p, 0, size0);
    return 0;
  }
  else
  {
    Range_Decode(p, size0, (1 << 14) - size0);
    return 1;
  }
}

}

CRangeDecoder::CRangeDecoder()
{
  s.GetThreshold = Range_GetThreshold;
  s.Decode = Range_Decode;
  s.DecodeBit = Range_DecodeBit;
}

struct CPpmdCpp
{
  unsigned Ver;
  CRangeDecoder _rc;
  CPpmd7 _ppmd7;
  CPpmd8 _ppmd8;
  
  CPpmdCpp(unsigned version)
  {
    Ver = version;
    Ppmd7_Construct(&_ppmd7);
    Ppmd8_Construct(&_ppmd8);
  }

  ~CPpmdCpp()
  {
    Ppmd7_Free(&_ppmd7, &g_BigAlloc);
    Ppmd8_Free(&_ppmd8, &g_BigAlloc);
  }

  bool Alloc(UInt32 memInMB)
  {
    memInMB <<= 20;
    if (Ver == 7)
      return Ppmd7_Alloc(&_ppmd7, memInMB, &g_BigAlloc) != 0;
    return Ppmd8_Alloc(&_ppmd8, memInMB, &g_BigAlloc) != 0;
  }

  void Init(unsigned order, unsigned restor)
  {
    if (Ver == 7)
      Ppmd7_Init(&_ppmd7, order);
    else
      Ppmd8_Init(&_ppmd8, order, restor);;
  }
    
  bool InitRc(CByteInBufWrap *inStream)
  {
    if (Ver == 7)
    {
      _rc.Stream = inStream;
      return _rc.Init();
    }
    else
    {
      _ppmd8.Stream.In = &inStream->p;
      return Ppmd8_RangeDec_Init(&_ppmd8) != 0;
    }
  }

  bool IsFinishedOK()
  {
    if (Ver == 7)
      return Ppmd7z_RangeDec_IsFinishedOK(&_rc);
    return Ppmd8_RangeDec_IsFinishedOK(&_ppmd8);
  }
};

STDMETHODIMP CHandler::Extract(const UInt32 *indices, UInt32 numItems,
    Int32 testMode, IArchiveExtractCallback *extractCallback)
{
  if (numItems == 0)
    return S_OK;
  if (numItems != (UInt32)-1 && (numItems != 1 || indices[0] != 0))
    return E_INVALIDARG;

  // extractCallback->SetTotal(_packSize);
  UInt64 currentTotalPacked = 0;
  RINOK(extractCallback->SetCompleted(&currentTotalPacked));
  CMyComPtr<ISequentialOutStream> realOutStream;
  Int32 askMode = testMode ?
      NExtract::NAskMode::kTest :
      NExtract::NAskMode::kExtract;
  RINOK(extractCallback->GetStream(0, &realOutStream, askMode));
  if (!testMode && !realOutStream)
    return S_OK;

  extractCallback->PrepareOperation(askMode);

  CByteInBufWrap inBuf;
  if (!inBuf.Alloc(1 << 20))
    return E_OUTOFMEMORY;
  inBuf.Stream = _stream;

  CBuf outBuf;
  if (!outBuf.Alloc())
    return E_OUTOFMEMORY;

  CLocalProgress *lps = new CLocalProgress;
  CMyComPtr<ICompressProgressInfo> progress = lps;
  lps->Init(extractCallback, true);

  CPpmdCpp ppmd(_item.Ver);
  if (!ppmd.Alloc(_item.MemInMB))
    return E_OUTOFMEMORY;
  Int32 opRes = NExtract::NOperationResult::kUnSupportedMethod;
  if (_item.IsSupported())
  {
    opRes = NExtract::NOperationResult::kDataError;
    ppmd.Init(_item.Order, _item.Restor);
    inBuf.Init();
    UInt64 outSize = 0;
    if (ppmd.InitRc(&inBuf) && !inBuf.Extra && inBuf.Res == S_OK)
    for (;;)
    {
      lps->InSize = _packSize = inBuf.GetProcessed();
      lps->OutSize = outSize;
      RINOK(lps->SetCur());

      size_t i;
      int sym = 0;

      if (ppmd.Ver == 7)
      {
        for (i = 0; i < kBufSize; i++)
        {
          sym = Ppmd7_DecodeSymbol(&ppmd._ppmd7, &ppmd._rc.s);
          if (inBuf.Extra || sym < 0)
            break;
          outBuf.Buf[i] = (Byte)sym;
        }
      }
      else
      {
        for (i = 0; i < kBufSize; i++)
        {
          sym = Ppmd8_DecodeSymbol(&ppmd._ppmd8);
          if (inBuf.Extra || sym < 0)
            break;
          outBuf.Buf[i] = (Byte)sym;
        }
      }

      outSize += i;
      _packSize = _headerSize + inBuf.GetProcessed();
      _packSizeDefined = true;
      if (realOutStream)
      {
        RINOK(WriteStream(realOutStream, outBuf.Buf, i));
      }
      if (sym < 0)
      {
        if (sym == -1 && ppmd.IsFinishedOK())
          opRes = NExtract::NOperationResult::kOK;
        break;
      }
    }
    RINOK(inBuf.Res);
  }
  realOutStream.Release();
  return extractCallback->SetOperationResult(opRes);
}

static IInArchive *CreateArc() { return new CHandler; }

static CArcInfo g_ArcInfo =
  { L"Ppmd", L"pmd", 0, 0xD, { 0x8F, 0xAF, 0xAC, 0x84 }, 4, false, CreateArc, 0 };

REGISTER_ARC(Ppmd)

}}
