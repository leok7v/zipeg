// RarCodecsRegister.cpp

#include "StdAfx.h"

#include "Rar20Decoder.h"
#include "Rar29Decoder.h"
#include "../Common/RegisterCodec.h"

static void *CreateCodec15() { return (void *)(ICompressCoder *)(new NCompress::NRar15::CDecoder); }
static void *CreateCodec20() { return (void *)(ICompressCoder *)(new NCompress::NRar20::CDecoder); }
static void *CreateCodec29() { return (void *)(ICompressCoder *)(new NCompress::NRar29::CDecoder); }

static CCodecInfo g_CodecsInfo[] = {
    { CreateCodec15, NULL, 0x040301, L"Rar15", 1, false },
    { CreateCodec20, NULL, 0x040302, L"Rar20", 1, false },
    { CreateCodec29, NULL, 0x040303, L"Rar29", 1, false },
};
REGISTER_CODECS(RarCodecs)
