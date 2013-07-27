#ifndef __p7z__EmbeddedCodecs__
#define __p7z__EmbeddedCodecs__

#include "c.h"
#include "Common/MyCom.h"
#include "7zip/Archive/IArchive.h"

class EmbeddedCodecs {
public:
    EmbeddedCodecs();
    virtual ~EmbeddedCodecs();
    enum {
        Zip = 0x1,
        BZip2 = 0x2,
        Rar = 0x3,
        Arj = 0x4,
        Z = 0x5,
        Lzh = 0x6,
        p7z = 0x7,
        Cab = 0x8,
        
        Nsis = 0x9,
        lzma = 0xA,
        lzma86 = 0xB,
        xz = 0xC,
        ppmd = 0xD,
        
        SquashFS = 0xD2,
        CramFS = 0xD3,
        APM = 0xD4,
        Mslz = 0xD5,
        Flv = 0xD6,
        Swf = 0xD7,
        Swfc = 0xD8,
        Ntfs = 0xD9,
        Fat = 0xDA,
        Mbr = 0xDB,
        Vhd = 0xDC,
        Pe = 0xDD,
        Elf = 0xDE,
        Mach_O = 0xDF,
        Udf = 0xE0,
        Xar = 0xE1,
        Mub = 0xE2,
        Hfs = 0xE3,
        Dmg = 0xE4,
        Compound = 0xE5,
        Wim = 0xE6,
        Iso = 0xE7,
        Bkf = 0xE8,
        Chm = 0xE9,
        Split = 0xEA,
        Rpm = 0xEB,
        Deb = 0xEC,
        Cpio = 0xED,
        Tar = 0xEE,
        GZip = 0xEF
    };
    
    static int handlers[]; // zero terminated: { Zip, BZip2, ... GZip, 0 }
    static const char *handlerNames[]; // zero terminated: { "Zip", "BZip2", ... "GZip", 0 }
    IInArchive* open(const wchar_t* archiveName,  IArchiveOpenCallback *ocb);
private:
    IInArchive  *inHandlers[256];
    IOutArchive *outHandlers[256];
};

#endif