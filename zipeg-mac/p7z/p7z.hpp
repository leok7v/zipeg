#pragma once

#include "c.h"
// #include "7zip/PropID.h"

class P7Z {
public:
    struct Value { // see VT_BSTR, VT_FILETIME etc...
        enum { kEmpty = 0, kString = 8, kFiletime = 64, kBool = 11, kI1 = 16, kI2 = 2, kI4 = 3, kI8 = 20 }; // kind
        int kind;
        uint64_t num;
        const wchar_t* str; // do not cache it. Only valid during iterate
        Value() : kind(0), num(0), str(null) {}
    };
    struct Delegate {
        virtual void error(const char* error) = 0; // exception text is reported here
        virtual const wchar_t* password(P7Z*) = 0;
        virtual bool progress(P7Z*, int64_t pos, int64_t total) = 0; // true means carry on
        virtual bool progressFile(P7Z*, int64_t fileno, int64_t files) = 0; // true means carry on
        virtual bool cancel(P7Z*) = 0; // true means cancel, false - carry on
        // called from iterateArchiveProperies()
        // subArchiveIndex == -1 for combined archive properties
        virtual bool archiveProperty(P7Z*, const char* name, Value& value) = 0;
        // called from iterateItems() both names[n] and values[n] where n = getNumberOfProperties()
        virtual bool itemProperties(P7Z*, int itemIndex, const char* names[], Value* values[]) = 0;
    };
    P7Z(Delegate* d);
    virtual ~P7Z() { close(); };
    bool open(const char* archiveName);
    void close();

    int  getNumberOfArchiveProperties();
    bool iterateArchiveProperies();

    int  getNumberOfItems();
    int  getNumberOfProperties();
    bool iterateAllItems();
    bool iterateItems(int fromInclusively, int toExclusively);
    const char* getItemName(int itemIndex);
    void setCodePage(int cp);
    int getCodePage() { return codePage; }
    
    enum { // for "PosixAttrib" see <stat.h>; Windows "Attrib" values:
        _FILE_ATTRIBUTE_READONLY            =      1,
        _FILE_ATTRIBUTE_HIDDEN              =      2,
        _FILE_ATTRIBUTE_SYSTEM              =      4,
        _FILE_ATTRIBUTE_UNDOCUMENTED_8      =      8,
        _FILE_ATTRIBUTE_DIRECTORY           =     16,
        _FILE_ATTRIBUTE_ARCHIVE             =     32,
        _FILE_ATTRIBUTE_DEVICE              =     64,
        _FILE_ATTRIBUTE_NORMAL              =    128,
        _FILE_ATTRIBUTE_TEMPORARY           =    256,
        _FILE_ATTRIBUTE_SPARSE_FILE         =    512,
        _FILE_ATTRIBUTE_REPARSE_POINT       =   1024,
        _FILE_ATTRIBUTE_COMPRESSED          =   2048,
        _FILE_ATTRIBUTE_OFFLINE             = 0x1000,
        _FILE_ATTRIBUTE_NOT_CONTENT_INDEXED = 0x2000,
        _FILE_ATTRIBUTE_ENCRYPTED           = 0x4000,
        _FILE_ATTRIBUTE_UNIX_EXTENSION      = 0x8000,
        _FILE_ATTRIBUTE_VIRTUAL             = 0x10000
    };
    
//  static void foo(void* context, const char *utf8, void (*bar)(void*, const char*));

private:
    bool reportException(int e);
    bool reportException(const char* e);
    bool reportException(const wchar_t* e);
    Delegate* delegate;
    void* archiveLink;
    int codePage;
};
