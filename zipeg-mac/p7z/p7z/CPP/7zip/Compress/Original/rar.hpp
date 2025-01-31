#ifndef _RAR_RARCOMMON_
#define _RAR_RARCOMMON_

#include "raros.hpp"
#include "os.hpp"

#ifdef _WINDOWS
#pragma warning(disable :4615) // unknown user warning type
#pragma warning(disable :4018) // signed/unsigned mismatch
#pragma warning(disable :4146) // unary minus operator applied to unsigned type, result still unsigned
#pragma warning(disable :4996) // strspy
#pragma warning(disable :4800) // int to bool
#define _WIN_32
#define NOCRYPT
#define SILENT
#undef RARDLL
#undef GUI
#define NOVOLUME
#endif

#ifdef __APPLE_CC__
#define NOCRYPT
#define SILENT
#undef RARDLL
#undef GUI
#define NOVOLUME
#endif

#ifdef RARDLL
#include "dll.hpp"
#endif

#ifndef _WIN_CE
#include "version.hpp"
#endif
#include "rartypes.hpp"
#include "rardefs.hpp"
#include "rarlang.hpp"
#include "int64.hpp"
#include "unicode.hpp"
#include "errhnd.hpp"
#include "array.hpp"
#include "timefn.hpp"
#include "options.hpp"
#include "headers.hpp"
#include "rarfn.hpp"
#include "pathfn.hpp"
#include "strfn.hpp"
#include "strlist.hpp"
#include "file.hpp"
#include "sha1.hpp"
#include "crc.hpp"
#include "rijndael.hpp"
#include "crypt.hpp"
#include "filefn.hpp"
#include "filestr.hpp"
#include "find.hpp"
#include "scantree.hpp"
#include "savepos.hpp"
#include "getbits.hpp"
#include "rdwrfn.hpp"
#include "archive.hpp"
#include "match.hpp"
#include "cmddata.hpp"
#include "filcreat.hpp"
#include "consio.hpp"
#include "system.hpp"
#include "isnt.hpp"
#include "log.hpp"
#include "rawread.hpp"
#include "encname.hpp"
#include "resource.hpp"
#include "compress.hpp"


#include "rarvm.hpp"
#include "model.hpp"


#include "unpack.hpp"


#include "extinfo.hpp"
#include "extract.hpp"



#include "list.hpp"



#include "rs.hpp"
#include "recvol.hpp"
#include "volume.hpp"
#include "smallfn.hpp"
#include "ulinks.hpp"

#include "global.hpp"


#endif
