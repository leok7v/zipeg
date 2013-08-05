#import "ZG7zip.h"
#import "ZGNumber.h"
#import "ZGErrors.h"
#import "ZGDate.h"
#import "ZGBitset.h"
#import "ZGSheet.h"
#import "ZGDocument.h"
#include "universalchardet.h"
#include "p7z.hpp"
#include "HashMapS2L.hpp"
#include "NanoTime.hpp"
#include "../chardet/universalchardet.h"

struct D;

@interface ZG7zip() {
    NSString* _filterText;
    NSString* _archiveFilePath;
    NSObject<ZGItemProtocol> *_root;
    D* d;
    P7Z* a;
    NSString* _error;
    NSString* _password;
    wchar_t* _w_password;
    ZGBitset* _isFolders;
    ZGBitset* _isLeafFolders;
    ZGBitset* _isFilteredOut;
    NSMutableDictionary* _items;
    NSMutableArray* _paths;
    NSMutableArray* _pnames;
    NSMutableArray* _props;
    NSOperation* _op;
    ZGDocument* __weak document;
    int pathIndex; // in pnames
    int items;
    int folders;
    int properties;
}

- (BOOL) isFiltered;
- (BOOL) isCancelled;
- (BOOL) itemProperties: (int) itemIndex names: (const char*[]) names values: (P7Z::Value*[])values;
- (BOOL) progress: (long long)pos ofTotal:(long long)total;
- (BOOL) progressFile: (long long)fileno ofTotal:(long long)files;
- (BOOL) isFilteredOut: (int) index;
- (BOOL) isLeafFolder: (int) index;
- (BOOL) isFolder: (int) index;
- (void) error: (const char*) text;
- (const wchar_t*) password;

@end

static NSMutableArray *leafNode;

@interface ZG7zipItem() {
    NSMutableArray *_children;
    NSMutableArray *_folderChildren;
    ZG7zip __weak *_archive;
@public
    int _index;
}
@end

@implementation ZG7zipItem

+ (void) initialize {
    assert(self == [ZG7zipItem class]);
    leafNode = [[NSMutableArray alloc] init];
}

- (id) initWith:(ZG7zip*) archive name:(NSString*) name index:(int) i isLeaf:(BOOL) leaf {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        _archive = archive;
        _name = name;
        _index = i;
        _children = leaf ? leafNode : [NSMutableArray new];
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    //  trace(@"");
}

- (void)addChild:(NSObject<ZGItemProtocol>*)child {
    assert(_children != leafNode);
    assert(child != self);
    [_children addObject:child];
}

- (NSString*)fullPath {
    assert(_parent != null); // cannot take fullPath of root
    NSUInteger n = _name.length;
    NSObject<ZGItemProtocol>* p = _parent;
    while (p != null) {
        n += p.name.length + 1;
        p = p.parent;
    }
    NSMutableString *s = [NSMutableString stringWithCapacity:n];
    [s appendString:_name];
    p = _parent;
    while (p.name.length > 0) {
        [s insertString:p.name atIndex:0];
        p = p.parent;
    }
    //trace("fullPath=%@", s);
    return s;
}

static NSMutableArray* filterOut(ZG7zip* a, NSMutableArray* childs) {
    if (childs == leafNode) {
        return null;
    }
    if (!a.isFiltered) {
        return childs;
    }
    BOOL affected = false;
    int n = (int)childs.count;
    for (ZG7zipItem* it in childs) {
        if ([a isFilteredOut:it->_index]) {
            affected = true;
            n--;
        }
    }
    if (!affected) {
        return childs;
    } if (n == 0) {
        return null;
    } else {
        NSMutableArray* c = [NSMutableArray arrayWithCapacity:n];
        for (ZG7zipItem* it in childs) {
            if (it->_index < 0 || ![a isFilteredOut:it->_index]) {
                [c addObject:it];
            }
        }
        assert(c.count == n);
        return c;
    }
}

- (NSMutableArray*) children {
    if (_children == leafNode) {
        return null;
    } else {
        return filterOut(_archive, _children);
    }
}

- (NSMutableArray*) folderChildren {
    if (_children == leafNode) {
        return null;
    } else if (_folderChildren == leafNode) {
        return null;
    } else if (_folderChildren == nil) {
        int n = 0;
        for (ZG7zipItem* it in _children) {
            n += [_archive isFolder:it->_index];
        }
        if (n == 0) {
            _folderChildren = leafNode;
        } else {
            _folderChildren = [NSMutableArray arrayWithCapacity:n];
            for (ZG7zipItem* it in _children) {
                if ([_archive isFolder:it->_index]) {
                    [_folderChildren addObject:it];
                }
            }
        }
    }
    return filterOut(_archive, _folderChildren);
}

- (BOOL) isGroup {
    return self == _archive.root;
}

@end

struct D : P7Z::Delegate {
    ZG7zip* __unsafe_unretained delegate;
    D(ZG7zip* __unsafe_unretained d) { delegate = d; }
    virtual ~D() {}
    virtual void error(const char* text) {
        [delegate error: text];
    }
    virtual const wchar_t* password(P7Z*) { return [delegate password]; }
    virtual bool progress(P7Z*, int64_t pos, int64_t total) {
        return [delegate progress:pos ofTotal:total];
    }
    virtual bool progressFile(P7Z*, int64_t fileno, int64_t files) {
        return [delegate progressFile:fileno ofTotal:files];
    }
    virtual bool cancel(P7Z*) {
        return delegate.isCancelled;
    }
    virtual bool archiveProperty(P7Z*, const char* name, P7Z::Value& v) {
        return true;
    }
    virtual bool itemProperties(P7Z* a, int itemIndex, const char* names[], P7Z::Value* values[]) {
        return [delegate itemProperties:itemIndex names:names values:values];
    }
};

@implementation ZG7zip

- (id) init {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        _root = [[ZG7zipItem alloc] initWith:self name:@"" index:-1 isLeaf:false];
        d = new D(self);
        if (d) {
            a = new P7Z(d);
        }
        pathIndex = -1;
    }
    return self;
}

- (void) dealloc {
    // trace(@"");
    if (a != null) {
        [self close];
    }
    dealloc_count(self);
}

- (void) close {
    // trace(@"");
    if (a != null) {
        a->close();
    }
    delete a; a = null;
    delete d; d = null;
    if (_w_password) {
        delete[] _w_password;
        _w_password = null;
    }
}


static bool ignore(const NSString* pathname) {
     // TODO: for now ignore resource forks and Finder's .DS_Store
    return [pathname hasSuffix:@".DS_Store"] || [pathname hasSuffix:@"__MACOSX"] ||
           [pathname hasPrefix:@"__MACOSX/"] || [pathname rangeOfString:@"/__MACOSX/"].location != NSNotFound;
}

- (BOOL) buildTree {
    folders = 0;
    for (int i = 0; i < items; i++) {
        const NSString* pathname = _paths[i];
        if (ignore(pathname)) {
            continue;
        }
        // ignore duplicates inside archive for now
        if (_items[pathname] == null) {
            BOOL isFolder = false;
            NSObject* isDir = _props[i][@"IsDir"];
            if ([isDir isKindOfClass:[ZGNumber class]] && ((ZGNumber*)isDir).kind == kB) {
                isFolder = ((NSNumber*)isDir).boolValue;
            } else {
                isFolder = [pathname hasSuffix:@"/"];
            }
            ZG7zipItem* item = [[ZG7zipItem alloc] initWith:self name:[pathname lastPathComponent] index:i isLeaf:!isFolder];
            if (item == null) {
                return false;
            }
            if (isFolder) {
                [_isFolders setBit:i to:true];
                [_isLeafFolders setBit:i to:true];
                folders++;
            }
            _items[pathname] = item;
        } else {
            console(@"duplicate file \"%@\" ignored", pathname);
        }
    }
    for (int i = 0; i < items; i++) {
        const NSString* pathname = _paths[i];
        if (ignore(pathname)) {
            continue;
        }
        ZG7zipItem* item = _items[pathname];
        assert(item != null);
        // NSArray* components = [pathname pathComponents]; might be faster
        for (;;) {
            NSString* parentComponents =  [pathname stringByDeletingLastPathComponent];
            if (item.parent != null) {
                break;
            }
            if ([parentComponents length] == 0) {
                item.parent = _root;
                // trace(@"0x%016llX.parent:=0x%016llX (root) %@ -> %@", (unsigned long long)item, (unsigned long long)_root, _root.name, item.name);
                break;
            }
            ZG7zipItem* p = _items[parentComponents];
            // archives without entries for the folders do exist
            // create "synthetic" parent for such folder with -1 as an index
            if (p == null) {
                trace(@"creating synthetic parent for %@", parentComponents);
                NSString* last = [parentComponents lastPathComponent];
                p = [[ZG7zipItem alloc] initWith: self name: last index: -1 isLeaf: false];
                if (p == null) {
                    return false;
                }
                _items[parentComponents] = p;
                folders++;
            }
            item.parent = p;
            // trace(@"0x%016llX.parent:=0x%016llX %@ -> %@", (unsigned long long)item, (unsigned long long)p, p.name, item.name);
            if (p->_index >= 0 && (item->_index < 0 || [_isFolders isSet:item->_index])) {
                [_isLeafFolders setBit:p->_index to:false];
            }
            item = p;
            pathname = parentComponents;
        }
    }
    // because of synthetic parents _items can have more elements than the rest
    for (NSString* pathname in _items) {
        if (ignore(pathname)) {
            continue;
        }
        ZG7zipItem* item = _items[pathname];
        assert(item != null);
        assert(item.parent != null);
        [(ZG7zipItem*)item.parent addChild:item];
    }
    NSComparator c = ^NSComparisonResult(id f, id s) {
        ZG7zipItem *first = (ZG7zipItem*)f;
        ZG7zipItem *second = (ZG7zipItem*)s;
        return [first.name compare:second.name];
    };
    for (NSString* pathname in _items) {
        if (ignore(pathname)) {
            continue;
        }
        ZG7zipItem* item = _items[pathname];
        if (item->_index >= 0 && [_isFolders isSet:item->_index]) {
            continue;
        }
        [item.children sortUsingComparator: c];
    }
    [_root.children sortUsingComparator: c];
    return true;
}

// http://regexkit.sourceforge.net/RegexKitLite/#ICUSyntax_ICURegularExpressionSyntax
static const char* kCharsNeedEscaping = "?+[(){}^$|\\./";

- (void) setFilter: (NSString*) filterText operation: (NSOperation*) op done: (void(^)(BOOL)) block {
    assert(![NSThread isMainThread]);
    trace("filterText=%@ _filterText=%@", filterText, _filterText);
    bool e0 =  filterText == null ||  filterText.length == 0;
    bool e1 = _filterText == null || _filterText.length == 0;
    if (e0 && e1) {
        return;
    }
    bool e = (e0 && [_filterText isEqualToString: filterText]) ||
             (e1 && [filterText isEqualToString: _filterText]) ||
             [filterText isEqualToString: _filterText];
    trace("filterText=%@ _filterText=%@ filterText isEqualToString:_filterText=%d",
          filterText, _filterText, e);
    if (e) {
        return;
    }
    BOOL __block b = false;
    int in = items;
    ZGBitset* isFilteredOut = null;
    if (!filterText || filterText.length == 0) {
        _filterText = null;
        [_isFilteredOut clear];
        b = true;
    } else {
        timestamp("setFilter");
        // making sure search strings like "foo*bar.txt" will work:
        NSStringCompareOptions opts = NSCaseInsensitiveSearch;
        if ([filterText rangeOfString:@"*"].location != NSNotFound) {
            NSUInteger k = filterText.length;
            NSMutableString *res = [NSMutableString stringWithCapacity:k * 2];
            char  ch[2] = {0};
            for(int i = 0; i < k; i++) {
                unichar c = [filterText characterAtIndex:i];
                ch[0] = (char)(c & 0xFF);
                // if char needs to be escaped
                if ('*' == c) {
                    [res appendString:@".*"];
                } else if (strstr(kCharsNeedEscaping, ch)) {
                    [res appendFormat:@"\\%c", c];
                } else {
                    [res appendFormat:@"%c", c];
                }
            }
            filterText = res;
            opts |= NSRegularExpressionSearch;
        }
        _filterText = filterText;
        isFilteredOut = [ZGBitset bitsetWithCapacity: items];
        b = isFilteredOut != null;
        [isFilteredOut fill];
        for (int i = 0; b && i < items; i++) {
            if (op.isCancelled) {
                b = false;
            } else {
                const NSString* pathname = _paths[i];
                ZG7zipItem* it = _items[pathname];
                if ([pathname rangeOfString:_filterText options:opts].location != NSNotFound) {
                    while (it) {
                        if (it->_index >= 0) {
                            if (![isFilteredOut isSet:it->_index]) {
                                break;
                            } else {
                                [isFilteredOut setBit:it->_index to:false];
                                in--;
                            }
                        }
                        it = (ZG7zipItem*)it.parent;
                    }
                }
            }
        }
        timestamp("setFilter");
    }
    if (b) {
        trace("search done");
        dispatch_async(dispatch_get_main_queue(), ^{
            assert([NSThread isMainThread]);
            bool found = true;
            if (in == items) {
                _filterText = null;
                [_isFilteredOut clear];
                found = false; // not found
            } else {
                _filterText = filterText;
                _isFilteredOut = isFilteredOut;
            }
            trace("block(%d)", found);
            block(found);
        });
    } else {
        trace("search cancelled");
    }
}

- (BOOL) isFiltered {
    return _filterText != null;
}

- (BOOL) isCancelled {
    return _op != null ? _op.isCancelled : false;
}

- (BOOL) isFilteredOut: (int) index {
    return [_isFilteredOut isSet:index];
}

- (BOOL) isLeafFolder: (int) index {
    return [_isLeafFolders isSet:index];
}

- (BOOL) isFolder: (int) index {
    return index < 0 || [_isFolders isSet:index];
}

- (int) numberOfItems {
    return items;
}

- (int) numberOfFolders {
    return folders;
}

static struct { const char* name; CFStringEncoding enc; } kEncodingsMap [] = {
    // CHARDET_ENCODING_X_ISO_10646_UCS_4 should not ever happen...
    {CHARDET_ENCODING_ISO_2022_JP,      kCFStringEncodingISO_2022_JP_3},
    {CHARDET_ENCODING_ISO_2022_CN,      kCFStringEncodingISO_2022_CN_EXT},
    {CHARDET_ENCODING_ISO_2022_KR,      kCFStringEncodingISO_2022_KR},
    {CHARDET_ENCODING_ISO_8859_5,       kCFStringEncodingISOLatinCyrillic},
    {CHARDET_ENCODING_ISO_8859_7,       kCFStringEncodingISOLatinGreek},
    {CHARDET_ENCODING_ISO_8859_8,       kCFStringEncodingISOLatinHebrew},
    {CHARDET_ENCODING_BIG5,             kCFStringEncodingBig5},
    {CHARDET_ENCODING_GB18030,          kCFStringEncodingGB_18030_2000},
    {CHARDET_ENCODING_EUC_JP,           kCFStringEncodingEUC_JP},
    {CHARDET_ENCODING_EUC_KR,           kCFStringEncodingEUC_KR},
    {CHARDET_ENCODING_EUC_TW,           kCFStringEncodingEUC_TW},
    {CHARDET_ENCODING_SHIFT_JIS,        kCFStringEncodingShiftJIS},
    {CHARDET_ENCODING_IBM855,           kCFStringEncodingDOSCyrillic},
    {CHARDET_ENCODING_IBM866,           kCFStringEncodingDOSRussian},
    {CHARDET_ENCODING_KOI8_R,           kCFStringEncodingKOI8_R},
    {CHARDET_ENCODING_MACCYRILLIC,      kCFStringEncodingMacCyrillic},
    {CHARDET_ENCODING_WINDOWS_1250,     kCFStringEncodingWindowsLatin2},
    {CHARDET_ENCODING_WINDOWS_1251,     kCFStringEncodingWindowsCyrillic},
    {CHARDET_ENCODING_WINDOWS_1252,     kCFStringEncodingWindowsLatin1},
    {CHARDET_ENCODING_WINDOWS_1253,     kCFStringEncodingWindowsGreek},
    {CHARDET_ENCODING_WINDOWS_1255,     kCFStringEncodingWindowsHebrew},
    {CHARDET_ENCODING_HZ_GB_2312,       kCFStringEncodingHZ_GB_2312},
    {CHARDET_ENCODING_ISO_8859_2,       kCFStringEncodingISOLatin2},
    {CHARDET_ENCODING_TIS_620,          kCFStringEncodingISOLatinThai}, // http://en.wikipedia.org/wiki/ISO/IEC_8859-11
    {CHARDET_ENCODING_UTF_8,            kCFStringEncodingUTF8},
    {CHARDET_ENCODING_UTF_16LE,         kCFStringEncodingUTF16LE},
    {CHARDET_ENCODING_UTF_16BE,         kCFStringEncodingUTF16BE},
    {CHARDET_ENCODING_UTF_32LE,         kCFStringEncodingUTF32LE},
    {CHARDET_ENCODING_UTF_32BE,         kCFStringEncodingUTF32BE},
    // with the BOM header (thus must be external in CFString) https://en.wikipedia.org/wiki/Universal_Character_Set
    {CHARDET_ENCODING_X_ISO_10646_UCS_4_3412,kCFStringEncodingUTF32BE}, // shouldn't ever happen
    // with the BOM header (thus must be external in CFString) https://en.wikipedia.org/wiki/Universal_Character_Set
    {CHARDET_ENCODING_X_ISO_10646_UCS_4_2143,kCFStringEncodingUTF32LE}, // shouldn't ever happen
    {null, (CFStringEncoding)-1 }
};

static CFStringEncoding CHARDET_ENCODING_to_CFStringEncoding(const char* encoding) {
    for (int i = 0; kEncodingsMap[i].name != null; i++) {
        if (strcmp(kEncodingsMap[i].name, encoding) == 0) {
            return kEncodingsMap[i].enc;
        }
    }
    return (CFStringEncoding)-1;
}

- (CFStringEncoding) detectEncoding {
    int n = a->getNumberOfItems();
    bool b = true;
    char encoding[CHARDET_MAX_ENCODING_NAME] = {0};
    chardet_t det;
    chardet_create(&det);
    long long deadline = NanoTime::time() + 1000 * 1000 * 50; // 50 milliseconds
    long long chars = 0;
    for (int i = 0; i < n && b; i++) {
        const char* name = a->getItemName(i);
        b = name != null;
        if (b) {
            int len = (int)strlen(name);
            int r = chardet_handle_data(det, name, len);
            chars += len;
            b = r != CHARDET_RESULT_NOMEMORY;
        }
        if ((i % 100 == 0 && NanoTime::time() > deadline) || chars > 1024 * 1024 || self.isCancelled) {
            break;
        }
    }
    chardet_data_end(det);
    chardet_get_charset(det, encoding, CHARDET_MAX_ENCODING_NAME);
    chardet_destroy(det);
    return CHARDET_ENCODING_to_CFStringEncoding(encoding);
}

static NSString* starifyMultipartRAR(NSString* s) {
    if ([s endsWithIgnoreCase:@".rar"]) {
        int i = [s lastIndexOfIgnoreCase:@".part"];
        if (i > 0 && isdigit([s characterAtIndex: i + 5])) {
            int j = i + 6; // no need to check s.length because s ends with ".rar"
            while (isdigit([s characterAtIndex: j])) {
                j++;
            }
            if ([s characterAtIndex: j] == '.' && j == s.length - 4) {
                s = [[s substringFrom: 0 to: i + 5] stringByAppendingString: @"*.rar"];
            }
        }
    }
    return s;
}

- (BOOL) readFromURL: (NSURL*) url ofType: (NSString*) type encoding:(NSStringEncoding) enc
            document: (ZGDocument*) doc
           operation: (NSOperation*) op error:(NSError**) err
                done: (void(^)(NSObject<ZGItemFactory>* factory, NSError* error)) done {
    // This method must be called on the background thread
    assert(![NSThread isMainThread]);
    assert(err != null);
    assert(doc != null);
    assert(op != null);
    document = doc;
//    [self password]; // TODO: messes up everything... :(
    // trace(@"absoluteURL=%@ type=%@ enc=%ld", url, type, enc);
    if (self == null || a == null || d == null) {
        *err = ZGOutOfMemoryError();
        return false;
    }
    if (![url isFileURL]) {
        NSMutableDictionary *details = [NSMutableDictionary dictionary];
        [details setValue:ZG_ERROR_LOCALIZED_DESCRIPTION(ZGIsNotAFile) forKey:NSLocalizedDescriptionKey];
        [details setValue:url forKey:NSURLErrorKey];
        *err = [NSError errorWithDomain:ZGAppErrorDomain code:ZGIsNotAFile userInfo:details];
        // [NSApp presentError:*err];
        return false;
    }
    _archiveFilePath = [url path];
    // trace("filePath=%@", _archiveFilePath);
    // "/Users/leo/code/zipeg/test/attachment(password-123456).rar"
    // _archiveFilePath = @"/Users/leo/tmp/test-xp-zip 試験.zip";
    // _archiveFilePath = @"/Users/leo/tmp/Райкин-birthday-export-2013-06-24.zip";
    bool b = false;
    @try {
        _op = op;
        b = a->open([_archiveFilePath UTF8String]) && a->getNumberOfItems() > 0 && a->getNumberOfProperties() > 0;
        if (b) {
            _root.name = starifyMultipartRAR([_archiveFilePath lastPathComponent]);
            items = a->getNumberOfItems();
            properties = a->getNumberOfProperties();
            _isFilteredOut = [ZGBitset bitsetWithCapacity:items];
            _isFolders = [ZGBitset bitsetWithCapacity:items];
            _isLeafFolders = [ZGBitset bitsetWithCapacity:items];
            _items = [NSMutableDictionary dictionaryWithCapacity:items * 3 / 2];
            _paths = [NSMutableArray arrayWithCapacity:items];
            _props = [NSMutableArray arrayWithCapacity:items];
            _pnames = [NSMutableArray arrayWithCapacity:properties];
            if (!_isFolders || !_isLeafFolders || !_isFilteredOut || !_items || !_paths || !_pnames || !_props) {
                a->close();
                *err = ZGOutOfMemoryError();
                return false;
            }
            b = a->iterateArchiveProperies();
            if (b) {
                CFStringEncoding encoding = [self detectEncoding];
                a->setCodePage(encoding);
                b = a->iterateAllItems();
                if (b) {
                    b = [self buildTree];
                }
            }
        } else {
            if (_error == null && _password != null) {
                _error = @"invalid password";
            } else if (_error == null && self.isCancelled) {
                _error = @"Operation has been cancelled by user request";
            } else if (_error == null) {
                _error = @"";
            }
            // TODO: NSUnderlyingErrorKey does nothing. Investigate later...
            NSError* e = [NSError errorWithDomain: ZGAppErrorDomain
                                             code: ZGArchiverError
                                         userInfo:@{NSLocalizedDescriptionKey: _error }];
            *err = [NSError errorWithDomain:NSCocoaErrorDomain code:NSFileReadCorruptFileError
                                   userInfo:@{NSFilePathErrorKey:_archiveFilePath, NSUnderlyingErrorKey: e
                    }];
        }
    } @finally {
        _op = null; // we must release the opearation here
        done(self, *err);
    }
    return b;
}

static NSObject* p7zValueToObject(P7Z::Value& v) {
    NSObject *o = null;
    switch (v.kind) {
        case P7Z::Value::kEmpty:
            o = null;
            break;
        case P7Z::Value::kString:
            o = [[NSString alloc] initWithBytes:v.str length:wcslen(v.str)*sizeof(wchar_t)
                                       encoding:NSUTF32LittleEndianStringEncoding];
            break;
        case P7Z::Value::kFiletime:
            o = [ZGNumber numberWithLongLong:(unsigned long long)v.num];
            break;
        case P7Z::Value::kBool:
            o = [ZGNumber numberWithBool:v.num != 0];
            break;
        case P7Z::Value::kI1:
            o = [ZGNumber numberWithUnsignedChar:(unsigned char)v.num];
            break;
        case P7Z::Value::kI2:
            o = [ZGNumber numberWithUnsignedShort:(unsigned short)v.num];
            break;
        case P7Z::Value::kI4:
            o = [ZGNumber numberWithUnsignedInt:(unsigned int)v.num];
            break;
        case P7Z::Value::kI8:
            o = [ZGNumber numberWithUnsignedLongLong:(unsigned long long)v.num];
            break;
        default:
            assert(false);
            break;
    }
    return o;
}

- (BOOL) itemProperties: (int) itemIndex names: (const char*[]) names values: (P7Z::Value*[])values {
    NSMutableDictionary* dic = [[NSMutableDictionary alloc] initWithCapacity: properties * 3 / 2];
    if (itemIndex == 0) {
        for (int i = 0; i < properties; i++) {
            _pnames[i] = [NSString stringWithUTF8String:names[i]];
            if (pathIndex < 0 && [_pnames[i] isEqualToString:@"Path"]) {
                pathIndex = i;
            }
        }
        if (pathIndex < 0) {
            return false;
        }
    }
    int codePage = a->getCodePage();
    NSString* path = null;
    for (int i = 0; i < properties; i++) {
        NSObject *o = p7zValueToObject(*values[i]);
        if (i == pathIndex) {
            if (codePage >= 0) {
                const char* name = a->getItemName(itemIndex);
                if (name) {
                    CFStringRef cfs = CFStringCreateWithBytes(kCFAllocatorDefault, (const UInt8*)name, strlen(name), codePage, false);
                    if (cfs) {
                        path = (__bridge NSString*)cfs;
                        CFRelease(cfs);
                    }
                }
            }
            if (!path) {
                path = (NSString*)o;
            }
        } else if (o != null) {
            NSObject *o = p7zValueToObject(*values[i]);
            if ([_pnames[i] hasSuffix:@"Time"]) {
                unsigned long long ticks = ((ZGNumber*)o).unsignedLongLongValue;
                o = ticks != 0 ? [NSDate dateWithTicksSince1601:ticks] : null;
            }
            if (o != null) {
                dic[_pnames[i]] = o;
            }
        }
    }
    if (path == null) {
        return false;
    }
    if ([path hasPrefix:@"/"]) {
        path = [path substringFromIndex: 1]; // fix the absolute path if present
    }
    _paths[itemIndex] = path;
    _props[itemIndex] = dic;
/*
    if (itemIndex < 10) {
        NSMutableString* s = [NSMutableString new];
        for (int i = 0; i < properties; i++) {
            if (![_pnames[i] isEqualToString:@"Path"]) {
                NSObject* va = dic[_pnames[i]];
                if (va != null) {
                    [s appendFormat:@"%@=%@ ", _pnames[i], va];
                }
            }
        }
        trace(@"[%d] %@: %@", itemIndex, path, s);
    }
*/ 
    return !self.isCancelled;
}

- (void) error:(const char*)text {
    _error = [NSString stringWithUTF8String:text];
}

- (const wchar_t*) password {
    if (!_password || _password.length == 0) {
        _password = [document askForPasswordFromBackgroundThread];
    }
    if (_password) {
        NSInteger n = _password.length;
        if (_w_password) {
            delete[] _w_password;
        }
        _w_password = new wchar_t[n + 1];
        if (_w_password) {
            memset(_w_password, 0, sizeof(wchar_t) * (n + 1));
            if ([_password getBytes: _w_password maxLength: sizeof(wchar_t) * n usedLength: null
                           encoding: NSUTF32LittleEndianStringEncoding options: 0
                              range: NSMakeRange(0, n) remainingRange: null]) {
                for (int i = 0; i < n; i++) {
                    _w_password[i] = NSSwapLittleIntToHost(_w_password[i]);
                }
                return _w_password;
            }
        }
    }
    return L"";
}

- (BOOL) progress:(long long)pos ofTotal:(long long)total {
    assert(![NSThread isMainThread]);
    BOOL __block b = false;
    dispatch_sync(dispatch_get_main_queue(), ^{
        assert([NSThread isMainThread]);
        b = [document progress:pos ofTotal:total];
    });
    return b && !self.isCancelled;
}

- (BOOL) progressFile:(long long)fileno ofTotal:(long long)totalNumberOfFiles {
    assert(![NSThread isMainThread]);
    BOOL __block b = false;
    dispatch_sync(dispatch_get_main_queue(), ^{
        assert([NSThread isMainThread]);
        b = [document progressFile:fileno ofTotal:totalNumberOfFiles];
    });
    return b && !self.isCancelled;
}

@end
