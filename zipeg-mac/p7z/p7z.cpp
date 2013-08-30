#ifdef __APPLE_CC__
#define UInt32  macUIn32
#include <CoreFoundation/CoreFoundation.h>
#undef UInt32
#endif

#include "myWindows/StdAfx.h"
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>

#include "myPrivate.h"
#include "p7z.hpp"

#include "Common/IntToString.h"
#include "Common/StringConvert.h"
#include "Common/UTFConvert.h"
#include "Common/UTFConvert.h"
#include "Common/Wildcard.h"
#include "Common/MyException.h"
#include "Common/MyWindows.h"

#include "Windows/FileDir.h"
#include "Windows/FileFind.h"
#include "Windows/FileName.h"
#include "Windows/NtCheck.h"
#include "Windows/PropVariant.h"
#include "Windows/PropVariantConversions.h"

#include "7zip/Common/FileStreams.h"

#include "7zip/UI/Common/OpenArchive.h"
#include "7zip/UI/Common/Extract.h"
#include "7zip/UI/Common/PropIDUtils.h"
#include "7zip/UI/Common/ExitCode.h"

#include "7zip/Archive/IArchive.h"

#include "7zip/IPassword.h"
#include "7zip/MyVersion.h"


#include "HashMapS2L.hpp"
#include "NanoTime.hpp"


using namespace NWindows;
using namespace NExtract;

struct CPropIdToName {
    PROPID propId;
    const char *name;
};

#define pid2name(s) { kpid##s, #s }

static const int AllPropIds[] = {
    /* kpidNoProperty, */
    kpidMainSubfile,
    kpidHandlerItemIndex,
    kpidPath,
    kpidName,
    kpidExtension,
    kpidIsDir,
    kpidSize,
    kpidPackSize,
    kpidAttrib,
    kpidCTime,
    kpidATime,
    kpidMTime,
    kpidSolid,
    kpidCommented,
    kpidEncrypted,
    kpidSplitBefore,
    kpidSplitAfter,
    kpidDictionarySize,
    kpidCRC,
    kpidType,
    kpidIsAnti,
    kpidMethod,
    kpidHostOS,
    kpidFileSystem,
    kpidUser,
    kpidGroup,
    kpidBlock,
    kpidComment,
    kpidPosition,
    kpidPrefix,
    kpidNumSubDirs,
    kpidNumSubFiles,
    kpidUnpackVer,
    kpidVolume,
    kpidIsVolume,
    kpidOffset,
    kpidLinks,
    kpidNumBlocks,
    kpidNumVolumes,
    kpidTimeType,
    kpidBit64,
    kpidBigEndian,
    kpidCpu,
    kpidPhySize,
    kpidHeadersSize,
    kpidChecksum,
    kpidCharacts,
    kpidVa,
    kpidId,
    kpidShortName,
    kpidCreatorApp,
    kpidSectorSize,
    kpidPosixAttrib,
    kpidLink,
    kpidError,
    
    kpidTotalSize,
    kpidFreeSpace,
    kpidClusterSize,
    kpidVolumeName,
    
    kpidLocalName,
    kpidProvider
    
    /* kpidUserDefined */
};

static const CPropIdToName kPropIdToName[] = {
    pid2name(Path),
    pid2name(NoProperty),
    pid2name(MainSubfile),
    pid2name(HandlerItemIndex),
    pid2name(Path),
    pid2name(Name),
    pid2name(Extension),
    pid2name(IsDir),
    pid2name(Size),
    pid2name(PackSize),
    pid2name(Attrib),
    pid2name(CTime),
    pid2name(ATime),
    pid2name(MTime),
    pid2name(Solid),
    pid2name(Commented),
    pid2name(Encrypted),
    pid2name(SplitBefore),
    pid2name(SplitAfter),
    pid2name(DictionarySize),
    pid2name(CRC),
    pid2name(Type),
    pid2name(IsAnti),
    pid2name(Method),
    pid2name(HostOS),
    pid2name(FileSystem),
    pid2name(User),
    pid2name(Group),
    pid2name(Block),
    pid2name(Comment),
    pid2name(Position),
    pid2name(Prefix),
    pid2name(NumSubDirs),
    pid2name(NumSubFiles),
    pid2name(UnpackVer),
    pid2name(Volume),
    pid2name(IsVolume),
    pid2name(Offset),
    pid2name(Links),
    pid2name(NumBlocks),
    pid2name(NumVolumes),
    pid2name(TimeType),
    pid2name(Bit64),
    pid2name(BigEndian),
    pid2name(Cpu),
    pid2name(PhySize),
    pid2name(HeadersSize),
    pid2name(Checksum),
    pid2name(Characts),
    pid2name(Va),
    pid2name(Id),
    pid2name(ShortName),
    pid2name(CreatorApp),
    pid2name(SectorSize),
    pid2name(PosixAttrib),
    pid2name(Link),
    pid2name(Error),
    pid2name(TotalSize),
    pid2name(FreeSpace),
    pid2name(ClusterSize),
    pid2name(VolumeName),
    pid2name(LocalName),
    pid2name(Provider),
    pid2name(UserDefined),
    {0, null}
};

static const char* pidName(PROPID pid) {
    int n = countof(kPropIdToName);
    for (int i = 0; i < n - 1; i++) {
        if (kPropIdToName[i].propId == pid) {
            return kPropIdToName[i].name;
        }
    }
    return null;
}

static CCodecs *codecs;

P7Z::P7Z(Delegate* d) : archiveLink(null), delegate(d), codePage(CP_UTF8) {
    assert(delegate != null);
    if (delegate == null) {
        throw "delegate cannot be null";
    }
    if (codecs == null) {
        codecs = new CCodecs;
        HRESULT result = codecs->Load();
        if (result != S_OK) {
            throw "failed to load codecs";
        }
    }
}

bool P7Z::reportException(int e) {
    char buff[1024] = {0};
    const char* s = "";
    switch(e) {
        case S_OK: s = "unexpected OK"; break;
        case E_OUTOFMEMORY: s = "out of memory"; break;
        case E_INVALIDARG: s = "invalid argument"; break;
        case E_ABORT: s = "aborted by user"; break;
        case E_FAIL: s = "failed"; break;
        case S_FALSE: s = "internal error"; break;
        case E_NOTIMPL: s = "not implemented"; break;
        case E_NOINTERFACE: s = "no interface"; break;
        case STG_E_INVALIDFUNCTION: s = "invalid operation"; break;
        default: s = buff;
            sprintf(buff, "error %d [0x%08x]", e, e);
    }
    delegate->error("", s);
    return false; // MUST ALWAYS RETURN FALSE (see usages)
}

bool P7Z::reportException(const char* e) {
    delegate->error("", e);
    return false; // MUST ALWAYS RETURN FALSE (see usages)
}

bool P7Z::reportException(const wchar_t* e) {
    UString us = e;
    AString as;
    if (ConvertUnicodeToUTF8(us, as) && as.Length() > 0) {
        delegate->error("", as);
    } else {
        delegate->error("", "internal error");
    }
    return false; // MUST ALWAYS RETURN FALSE (see usages)
}


static IInArchive* getArchive(void* archiveLink) {
    CArchiveLink &link = *(CArchiveLink*)archiveLink;
    return link.GetArchive();
}

int P7Z::getNumberOfItems() {
    UInt32 n = 0;
    return getArchive(archiveLink)->GetNumberOfItems(&n) == S_OK ? (int)n : 0;
}

int P7Z::getNumberOfProperties() {
    UInt32 n = 0;
    return getArchive(archiveLink)->GetNumberOfProperties(&n) == S_OK ? n : 0;
}

const char* P7Z::getItemName(int itemIndex) {
    const char* buf;
    HRESULT r = getArchive(archiveLink)->GetItemName(itemIndex, buf);
    return r == S_OK ? buf : null;
}

int P7Z::getNumberOfArchiveProperties() {
    UInt32 n = 0;
    return getArchive(archiveLink)->GetNumberOfArchiveProperties(&n) == S_OK ? n : 0;
}

void P7Z::setCodePage(int cp) {
    codePage = cp;
    getArchive(archiveLink)->SetEncoding(cp);
}

bool P7Z::open(const char* archiveName) {

    struct OpenCallback : public IOpenCallbackUI {
        OpenCallback(P7Z *context) : asked(false), totalFiles(0), totalBytes(0) { ctx = context; }

        STDMETHOD_(ULONG, Release)() { return 1; } // only used on the stack
        
        virtual HRESULT Open_CryptoGetTextPassword(UString &pwd) {
            if (!asked) {
                password = ctx->delegate->password(ctx);
                asked = true;
            }
            pwd = password;
            return S_OK;
        }
        
        virtual HRESULT Open_GetPasswordIfAny(UString &pwd) {
            if (asked && this->password) {
                pwd = this->password;
                return S_OK;
            } else {
                return E_FAIL;
            }
        }
        
        virtual bool Open_WasPasswordAsked() { return asked; }
        
        virtual void Open_ClearPasswordWasAskedFlag() {
            password = L"";
            asked = false;
        }
        
        virtual HRESULT Open_CheckBreak()  {
            return ctx->delegate->cancel(ctx) ? E_FAIL : S_OK;
        }
        
        virtual HRESULT Open_SetTotal(const UInt64 *files, const UInt64 *bytes) {
            totalFiles = files != null ? (int64_t)*files : 0;
            totalBytes = bytes != null ? (int64_t)*bytes : 0;
            return S_OK;
        }
        
        virtual HRESULT Open_SetCompleted(const UInt64 *files, const UInt64 *bytes)  {
            bool b = true;
            if (files != null) {
                b = ctx->delegate->progressFile(ctx, (int64_t)*files, totalFiles);
            }
            if (bytes != null) {
                b = ctx->delegate->progress(ctx, (int64_t)*bytes, totalBytes);
            }
            return b ? S_OK : E_ABORT;
        }
        
        P7Z *ctx;
        bool asked;
        int64_t totalFiles;
        int64_t totalBytes;
        UString password;
    };
    assert(archiveLink == null);
    if (archiveLink != null) {
        throw "opening same archive twice without close";
    }
    try {
        CArchiveLink* link = new CArchiveLink;
        archiveLink = link;
        CIntVector formatIndices;
        bool stdInMode = false;
        AString cn = archiveName;
        UString name;
        if (!ConvertUTF8ToUnicode(archiveName, name)) {
            return false;
        }
        OpenCallback oc(this);
        if (link->Open2(codecs, formatIndices, stdInMode, NULL, name, &oc) != S_OK) {
            return false;
        }
        return true;
    } catch (int err) { return reportException(err);
    } catch (const char* err) { return reportException(err);
    } catch (const wchar_t* err) { return reportException(err);
    } catch (CSystemException &err) { return reportException(err.ErrorCode);
    }
}

bool P7Z::extract(int* indices, int n, const char* dest, const char* removePathComponents[], int pc, int fd) {
    
    struct ExtractCallback : public IFolderArchiveExtractCallback, public CArchiveExtractCallback {
        ExtractCallback(P7Z *context) : asked(false), total(0), completed(0), fd(-1) { ctx = context; }
        MY_QUERYINTERFACE_BEGIN
        MY_QUERYINTERFACE_ENTRY(ICryptoGetTextPassword)
        MY_QUERYINTERFACE_END
        STDMETHOD_(ULONG, AddRef)() { return 1; } // only used on the stack
        STDMETHOD_(ULONG, Release)() { return 1; } // only used on the stack

        int fd;

        virtual HRESULT CryptoGetTextPassword(UString &p) {
            if (!asked) {
                password = ctx->delegate->password(ctx);
                asked = true;
            }
            p = password;
            return S_OK;
        }
                                 
        virtual HRESULT SetTotal(UInt64 t) {
            total = t;
            // trace("total=%lld\n", total);
            return S_OK;
        }
        
        virtual HRESULT SetCompleted(const UInt64 *completeValue) {
            completed = completeValue == 0 ? 0 : *completeValue;
            // trace("completeValue=%lld\n", completed);
            bool b = ctx->delegate->progress(ctx, completed, total);
            return b ? S_OK : E_ABORT;
        }

        virtual HRESULT MoveToTrash(const wchar_t *pathname) {
            AString pname;
            if (!ConvertUnicodeToUTF8(pathname, pname)) {
                throw "bad file name";
            }
            bool b = ctx->delegate->moveToTrash(ctx, pname);
            return b ? S_OK : E_ABORT;
        }

        virtual HRESULT AskOverwrite(const wchar_t *existName,
                                     const FILETIME *existTime, const UInt64 *existSize,
                                     const wchar_t *newName,
                                     const FILETIME *newTime, const UInt64 *newSize,
                                     Int32 *answer) {
            AString from;
            if (!ConvertUnicodeToUTF8(existName, from)) {
                throw "bad file name";
            }
            AString to;
            if (!ConvertUnicodeToUTF8(newName, to)) {
                throw "bad file name";
            }
            int64_t fromTime = existTime == null ? 0 :
              (((int64_t)existTime->dwHighDateTime) << 32) | existTime->dwHighDateTime;
            int64_t toTime = newTime == null ? 0 :
              (((int64_t)newTime->dwHighDateTime) << 32) | newTime->dwHighDateTime;
            int64_t fromSize = existSize == null ? 0 : (int64_t)*existSize;
            int64_t toSize = newSize == null ? 0 : (int64_t)*newSize;
            *answer = ctx->delegate->askOverwrite(ctx, from, fromTime, fromSize, to, toTime, toSize);
            // *answer = NOverwriteAnswer::kAutoRename;
            return S_OK;
        }
                                 
        virtual HRESULT PrepareOperation(const wchar_t* name, bool isFolder, Int32 askExtractMode,
                                         const UInt64 *position) {
            const char* s = null;
            char buff[128] = {0};
            switch (askExtractMode) {
                case NArchive::NExtract::NAskMode::kExtract: s = "extract"; break;
                case NArchive::NExtract::NAskMode::kTest: s = "test"; break;
                case NArchive::NExtract::NAskMode::kSkip: s = "skip"; break;
                default: sprintf(buff, "askExtractMode=%d", askExtractMode); s = buff;
            }
            file = name;
            // trace("PrepareOperation: name=%ls isFolder=%d %s position=%lld\n", name, isFolder, s, position != null ? *position : 0);
            return S_OK;
        }
                                 
        virtual HRESULT MessageError(const wchar_t *message) {
            // trace("MessageError: %ls\n", message);
            AString e;
            AString f;
            if (ConvertUnicodeToUTF8(file, f) && ConvertUnicodeToUTF8(message, e)) {
                if (!ctx->delegate->error(f, e)) {
                    return E_ABORT;
                }
            } else {
                if (!ctx->delegate->error("", "unconvertable unicode string")) {
                    return E_ABORT;
                }
            }
            return S_OK;
        }
                                 
        virtual HRESULT SetOperationResult(Int32 r, bool encrypted) {
            const wchar_t* s = null;
            wchar_t buff[128] = {0};
            switch (r) {
                case NArchive::NExtract::NOperationResult::kOK:
                    s = L"OK"; break;
                case NArchive::NExtract::NOperationResult::kUnSupportedMethod:
                    s = L"Unsupported Method"; break;
                case NArchive::NExtract::NOperationResult::kDataError:
                    s = L"Data Error"; break;
                    trace("SetOperationResult: \n"); break;
                case NArchive::NExtract::NOperationResult::kCRCError:
                    s = L"CRC Error"; break;
                case NArchive::NExtract::NOperationResult::kAuthError:
                    s = L"Authentication Error"; break;
                default: wprintf(buff, "error %d", r); s = buff;
            }
            if (r != NArchive::NExtract::NOperationResult::kOK) {
                // trace("SetOperationResult: result=%ls encrypted=%d\n", s, encrypted);
                return MessageError(s);
            }
            return S_OK;
        }

        // folowing functions are only called when Extract.cpp is used to decompress multiple archives
        virtual HRESULT BeforeOpen(const wchar_t *name) { return S_OK; }
        virtual HRESULT OpenResult(const wchar_t *name, HRESULT result, bool encrypted) { return S_OK; }
        virtual HRESULT ThereAreNoFiles() { return S_OK; }
        virtual HRESULT ExtractResult(HRESULT result) { return S_OK; }
        virtual HRESULT SetPassword(const UString &password) { return S_OK; }

        virtual HRESULT GetStream(UInt32 index, ISequentialOutStream **outStream, Int32 askExtractMode) {
            return CArchiveExtractCallback::GetStream(index, outStream, askExtractMode, fd);
        }

        virtual HRESULT PrepareOperation(Int32 askExtractMode) {
            return CArchiveExtractCallback::PrepareOperation(askExtractMode);
        }

        virtual HRESULT SetOperationResult(Int32 resultEOperationResult) {
            return CArchiveExtractCallback::SetOperationResult(resultEOperationResult);
        }

        P7Z *ctx;
        bool asked;
        int64_t total;
        int64_t completed;
        UString password;
        UString file;
    };
    assert(archiveLink != null);
    if (archiveLink == null) {
        throw "archive is not open";
    }
    try {
//      bool stdInMode = false;
        UString directoryPath;
        if (!ConvertUTF8ToUnicode(dest, directoryPath)) {
            return false;
        }
        ExtractCallback ecs(this);
        CArchiveLink &link = *(CArchiveLink*)archiveLink;
        const CArc &arc = link.Arcs.Back();
        UStringVector removePathParts;
        for (int i = 0; i < pc; i++) {
            UString s;
            if (ConvertUTF8ToUnicode(removePathComponents[i], s)) {
                removePathParts.Add(s);
            }
        }
        ecs.Init(
             null, // const NWildcard::CCensorNode *wildcardCensor,
             &arc,
             &ecs, // IFolderArchiveExtractCallback *extractCallback2,
             false /*stdOutMode*/, false /*testMode*/, false /*crcMode*/,
             directoryPath,
             removePathParts,
             999999 // packSize  // TODO: ??????
        );
        ecs.fd = fd;
        if (fd >= 0) {
            assert(n == 1); // single file mode
        }
        IInArchive* a = getArchive(archiveLink);
        // TODO: all for now
        HRESULT result = a->Extract((const unsigned int*)indices, n, false /*test*/, &ecs);
        return result == S_OK;
    } catch (int err) { return reportException(err);
    } catch (const char* err) { return reportException(err);
    } catch (const wchar_t* err) { return reportException(err);
    } catch (CSystemException &err) { return reportException(err.ErrorCode);
    }
}

static void propToValue(PROPID propId, NCOM::CPropVariant &prop, UString* storage, P7Z::Value *value) {
    P7Z::Value &v = *value;
    UString  &s = *storage;
    s = ConvertPropertyToString(prop, propId);
    v.str = (const wchar_t *)(LPCTSTR)s;
    switch (prop.vt) {
        case VT_EMPTY: // fall thru to VT_NULL
        case VT_NULL: v.str = null; v.num = 0; v.kind = P7Z::Value::kEmpty; return;
        case VT_BSTR: v.kind = P7Z::Value::kString; break;
        case VT_UI1: v.kind = P7Z::Value::kI1; v.num = prop.bVal;  break;
        case VT_UI2: v.kind = P7Z::Value::kI2; v.num = prop.uiVal; break;
        case VT_UI4: v.kind = P7Z::Value::kI4; v.num = prop.ulVal; break;
        case VT_UI8: v.kind = P7Z::Value::kI8; v.num = prop.uhVal.QuadPart; break;
        case VT_FILETIME: v.kind = P7Z::Value::kFiletime;
            v.num = (((UInt64)prop.filetime.dwHighDateTime) << 32) | prop.filetime.dwLowDateTime;
            break;
        case VT_I2: v.kind = P7Z::Value::kI2; v.num = prop.iVal; break;
        case VT_I4: v.kind = P7Z::Value::kI4; v.num = prop.lVal; break;
        case VT_I8: v.kind = P7Z::Value::kI8; v.num = prop.hVal.QuadPart; break;
        case VT_BOOL: v.kind = P7Z::Value::kBool;
            v.num = VARIANT_BOOLToBool(prop.boolVal);
            v.str = v.num ? L"true" : L"false";
            break;
        default:
            assert(false);
            v.kind = P7Z::Value::kString;
            v.str = L"Unknown";
    }
}

bool P7Z::iterateArchiveProperies() {
    try {
        IInArchive &a = *getArchive(archiveLink);
        int n = getNumberOfArchiveProperties();
        CMyComBSTR name;
        PROPID propId;
        VARTYPE varType;
        P7Z::Value v;
        UString s;
        for (int i = 0; i < n; i++) {
            if (a.GetArchivePropertyInfo(i, &name, &propId, &varType) != S_OK) {
                return false;
            }
            NCOM::CPropVariant prop;
            if (a.GetArchiveProperty(propId, &prop) != S_OK) {
                return false;
            }
            if (prop.vt != VT_EMPTY) {
                propToValue(propId, prop, &s, &v);
                if (!delegate->archiveProperty(this, pidName(propId), v)) {
                    return false;
                }
            }
        }
        return true;
    } catch (int err) { return reportException(err);
    } catch (const char* err) { return reportException(err);
    } catch (const wchar_t* err) { return reportException(err);
    } catch (CSystemException &err) { return reportException(err.ErrorCode);
    }
}

bool getUInt64Value(IInArchive *archive, UInt32 index, PROPID propID, UInt64 &value) {
    NCOM::CPropVariant prop;
    if (archive->GetProperty(index, propID, &prop) != S_OK)
        return false;
    if (prop.vt == VT_EMPTY)
        return false;
    value = ConvertPropVariantToUInt64(prop);
    return true;
}

bool P7Z::iterateAllItems() {
    return iterateItems(0, getNumberOfItems());
}

bool P7Z::isDir(int i) {
    NCOM::CPropVariant prop;
    CArchiveLink &link = *(CArchiveLink*)archiveLink;
    const CArc &arc = link.Arcs.Back();
    if (arc.Archive->GetProperty(i, kpidIsDir, &prop) != S_OK) {
        return false;
    } else {
        return prop.vt == VT_BOOL && prop.boolVal != VARIANT_FALSE;
    }
}

bool P7Z::iterateItems(int from, int to) { // [from..to[
    try {
        int n = to - from;
        if (n <= 0 || from < 0 || from + to > getNumberOfItems()) {
            return false;
        }
        CArchiveLink &link = *(CArchiveLink*)archiveLink;
        const CArc &arc = link.Arcs.Back();
        IInArchive &a = *arc.Archive;
        int np = getNumberOfProperties();
        PROPID props[np];
        const char* names[np];
        UString* s[np];
        Value* values[np];
        bool b = true;
        CMyComBSTR name;
        PROPID propId;
        VARTYPE vt;
        for (int i = 0; i < np && b; i++) {
            values[i] = new Value();
            s[i] = new UString();
            b = values[i] != null && s[i] != null;
            if (b) {
                if (a.GetPropertyInfo(i, &name, &propId, &vt) != S_OK) {
                    return false;
                }
                props[i] = propId;
                names[i] = pidName(propId);
            }
        }
        if (b) {
            NCOM::CPropVariant prop;
            for (int i = from; i < to && b; i++) {
                for (int j = 0; j < np && b; j++) {
                    if (arc.Archive->GetProperty(i, props[j], &prop) != S_OK) {
                        b = false;
                    } else if (prop.vt != VT_EMPTY && prop.vt != VT_NULL) {
                        propToValue(props[j], prop, s[j], values[j]);
                        // printf("[%d] props[%d] %s propId=%d v.kind=%d v.num=0x%016llu v.str=0x%016llu\n", i, j, names[j], props[j], values[j]->kind, values[j]->num, values[j]->str);
                        assert(values[j]->kind != 0);
                    } else {
                        // prop.vt == VT_EMPTY and there is nothing I can do about it.
                    }
                }
                if (b) {
                    b = delegate->itemProperties(this, i, names, values);
                }
            }
        }
        for (int i = 0; i < np; i++) {
            delete s[i];
            delete values[i];
        }
        return b;
    } catch (int err) { return reportException(err);
    } catch (const char* err) { return reportException(err);
    } catch (const wchar_t* err) { return reportException(err);
    } catch (CSystemException &err) { return reportException(err.ErrorCode);
    }
}

void P7Z::close() {
    if (archiveLink != null) {
        CArchiveLink* link = (CArchiveLink*)archiveLink;
        link->Release();
        delete link;
        archiveLink = null;
    }
}

////////////////////
extern "C" {

void hexdump(const char* s) {
    int i = 0;
    int n = (int)strlen(s);
    for (i = 0; i < n; i++) {
        printf("%c 0x%02X ", s[i], (s[i] & 0xFF));
    }
    printf("\n");
}

}