#include "myWindows/StdAfx.h"

#include "Common/MyGuidDef.h"
#include "Common/MyCom.h"
#include "Common/StringConvert.h"
//#include "Windows/PropVariantConversions.h"
#include "7zip/Archive/IArchive.h"
#include "7zip/IPassword.h"
#include "7zip/Common/FileStreams.h"

#include "EmbeddedCodecs.h"
#include "NanoTime.h"

int EmbeddedCodecs::handlers[] = {
    Zip,
    BZip2,
    Rar,
    Arj,
    Z,
    Lzh,
    p7z,
    Cab,
    Nsis,
    lzma,
    lzma86,
    xz,
    ppmd,
    SquashFS,
    CramFS,
    APM,
    Mslz,
    Flv,
    Swf,
    Swfc,
    Ntfs,
    Fat,
    Mbr,
    Vhd,
    Pe,
    Elf,
    Mach_O,
    Udf,
    Xar,
    Mub,
    Hfs,
    Dmg,
    Compound,
    Wim,
    Iso,
    Bkf,
    Chm,
    Split,
    Rpm,
    Deb,
    Cpio,
    Tar,
    GZip,
    0
};

const char *EmbeddedCodecs::handlerNames[] = {
    "Zip",
    "BZip2",
    "Rar",
    "Arj",
    "Z",
    "Lzh",
    "p7z",
    "Cab",
    "Nsis",
    "lzma",
    "lzma86",
    "xz",
    "ppmd",
    "SquashFS",
    "CramFS",
    "APM",
    "Mslz",
    "Flv",
    "Swf",
    "Swfc",
    "Ntfs",
    "Fat",
    "Mbr",
    "Vhd",
    "Pe",
    "Elf",
    "Mach_O",
    "Udf",
    "Xar",
    "Mub",
    "Hfs",
    "Dmg",
    "Compound",
    "Wim",
    "Iso",
    "Bkf",
    "Chm",
    "Split",
    "Rpm",
    "Deb",
    "Cpio",
    "Tar",
    "GZip",
    0
};

/* DEFINE_GUID(CLSID_CFormat??,  0x23170F69, 0x40C1, 0x278A, 0x10, 0x00, 0x00, 0x01, 0x10, 0x??, 0x00, 0x00); */
static unsigned char tail[] = {0x10, 0x00, 0x00, 0x01, 0x10, 0x00, 0x00, 0x00};

STDAPI CreateObject(const GUID *clsid, const GUID *iid, void **outObject);

EmbeddedCodecs::EmbeddedCodecs() { // timestamp ~ 88 microseconds
    // NanoTime::timestamp("EmbeddedCodecs::EmbeddedCodecs()");
    int i = 0;
    memset(inHandlers, 0, sizeof(inHandlers));
    memset(outHandlers, 0, sizeof(outHandlers));
    while (handlers[i] != 0) {
        GUID guid = {0x23170F69, 0x40C1, 0x278A};
        memcpy(&guid.Data4, tail, sizeof guid.Data4);
        guid.Data4[5] = handlers[i];
        CMyComPtr<IInArchive> inArchive;
        if (CreateObject(&guid, &IID_IInArchive, (void **)&inArchive) != S_OK) {
            //printf("Can not get class object for IInArchive %s\n", handlerNames[i]);
        } else {
            IInArchive* in = inArchive;
            in->AddRef();
            inHandlers[handlers[i]] = in;
            //printf("Created class object for IInArchive %s\n", handlerNames[i]);
        }
        CMyComPtr<IOutArchive> outArchive;
        if (CreateObject(&guid, &IID_IOutArchive, (void **)&outArchive) != S_OK) {
            //printf("Can not get class object for IOutArchive %s\n", handlerNames[i]);
        } else {
            IOutArchive* out = outArchive;
            out->AddRef();
            outHandlers[handlers[i]] = out;
            //printf("Created class object for IOutArchive %s\n", handlerNames[i]);
        }
        i++;
    }
    // NanoTime::timestamp("EmbeddedCodecs::EmbeddedCodecs()");
}

EmbeddedCodecs::~EmbeddedCodecs() {
    int i = 0;
    while (handlers[i] != 0) {
        int k = handlers[i];
        if (inHandlers[k] != null) {
            inHandlers[k]->Release();
            inHandlers[k] = null;
        }
        if (outHandlers[k] != null) {
            outHandlers[k]->Release();
            outHandlers[k] = null;
        }
        i++;
    }
}

class CArchiveOpenCallback3: public IArchiveOpenCallback, public ICryptoGetTextPassword, public CMyUnknownImp {
public:
    MY_UNKNOWN_IMP1(ICryptoGetTextPassword)
    
    STDMETHOD(SetTotal)(const UInt64 *files, const UInt64 *bytes) {
        isOpened = true;
        if (files != null) {
            fprintf(stderr, "SetTotal files: %lld\n", *files);
        }
        if (bytes != null) {
            fprintf(stderr, "SetTotal bytes: %lld\n", *bytes);
        }
        return S_OK;
    }

    STDMETHOD(SetCompleted)(const UInt64 *files, const UInt64 *bytes) {
        if (files != null) {
            fprintf(stderr, "SetCompleted files: %lld\n", *files);
        }
        if (bytes != null) {
            fprintf(stderr, "SetCompleted bytes: %lld\n", *bytes);
        }
        return S_OK;
    }
    
    STDMETHOD(CryptoGetTextPassword)(UString &password) {
        isPasswordRequired = true;
        return StringToBstr(L"", password);
    }
    
    bool isPasswordRequired;
    bool isOpened;
    bool isCompleted;
    
    CArchiveOpenCallback3() : isPasswordRequired(false) {}

};

IInArchive* EmbeddedCodecs::open(const wchar_t* archiveName, IArchiveOpenCallback *ocb) {
    CInFileStream *ifs = new CInFileStream;
    CMyComPtr<IInStream> file = ifs;
    if (!ifs->Open(archiveName)) {
        fprintf(stderr, "Can not open archive file: %ls\n", archiveName);
        return null;
    }
    int i = 0;
    CIntVector formatIndices;
    while (handlers[i] != 0) {
        int k = handlers[i];
        if (inHandlers[k] != null) {
            if (inHandlers[k]->Open(file, 0, ocb) == S_OK) {
                fprintf(stderr, "%s can open archive file: %ls\n", handlerNames[i], archiveName);
                inHandlers[k]->Close();
            }
        }
        i++;
    }
    ifs->File.Close();
    return null;
}

