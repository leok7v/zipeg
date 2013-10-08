#import "ZG7zip.h"
#import "ZGGenericItem.h"
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
#include <sys/stat.h>

static ZGNumber* _TRUE = [ZGNumber.alloc initWithBool: true];

struct D;

@interface ZG7zip() {
    NSString* _filterText;
    NSString* _archiveFilePath;
    ZG7zipItem* _root;
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
    ZGOperation* _op;
    NSObject<ZGArchiveCallbacks>* __weak document;
    int _pathIndex; // in pnames
    int _numberOfItems;
    int _numberOfFolders;
    int _properties;
    int64_t _open_total;
    int64_t _open_progress;
}

- (BOOL) isFiltered;
- (BOOL) isCancelled;
- (BOOL) itemProperties: (int) itemIndex names: (const char*[]) names values: (P7Z::Value*[])values;
- (BOOL) progress: (long long)pos ofTotal:(long long)total;
- (BOOL) progressFile: (long long)fileno ofTotal:(long long)files;
- (BOOL) isFilteredOut: (int) index;
- (BOOL) isLeafFolder: (int) index;
- (BOOL) isFolder: (int) index;
- (BOOL) file: (const char*) file error: (const char*) text;
- (BOOL) moveToTrash: (const char*) pathname;
- (int)  askOverwriteFrom: (const char*) fromName time: (int64_t) fromTime size: (int64_t) fromSize
                       to: (const char*) toName time: (int64_t) toTime size: (int64_t) toSize;
- (const wchar_t*) password;
- (NSString*) pathname: (int) index;
- (NSDictionary*) props: (int) index;

@end

static NSMutableArray *leafNode;

@interface ZG7zipItem() {
    NSString* _name;
    NSObject<ZGItemProtocol>* __weak _parent;
    NSMutableArray *_children;
    NSMutableArray *_folderChildren;
    ZG7zip __weak *_archive;
@public
    int _index;
}
@end

@implementation ZG7zipItem

@synthesize name = _name;
@synthesize parent = _parent;

+ (void) initialize {
    assert(self == ZG7zipItem.class);
    leafNode = [NSMutableArray new];
    assert(leafNode != null);
}

- (id) initWith:(ZG7zip*) archive name: (NSString*) n index: (int) i isLeaf: (BOOL) leaf {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        _archive = archive;
        _name = n;
        _index = i;
        // it is important that empty leaf folders do have empty _children arrays;
        // this allows to distinguish them from files
        _children = leaf ? leafNode : [NSMutableArray new];
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (void) addChild: (NSObject<ZGItemProtocol>*) child {
    assert(_children != leafNode);
    assert(child != self);
    [_children addObject: child];
}

- (NSString*) fullPath {
    if (_parent == null) {
        return @"/";
    }
    if (_index >= 0) {
        return [_archive pathname: _index];
    } else {
        return [ZGGenericItem fullPath: self];
    }
}

- (NSDictionary*) properties {
    return _index >= 0 ? [_archive props: _index] : @{};
}

- (NSNumber*) size {
    NSObject* o = self.properties[@"Size"];
    if ([o isKindOfClass: ZGNumber.class]) {
        ZGNumber* n = (ZGNumber*)o;
        return @(n.longLongValue);
    } else if (self.children != null) {
        return @(self.children.count);
    }
    return null;
}

- (NSDate*) time {
    NSObject* o = self.properties[@"MTime"];
    if ([o isKindOfClass: NSDate.class]) {
        return (NSDate*)o;
    }
    return null;
}

- (NSString*) description {
    NSString* d = [NSString stringWithFormat: @"%@ index=%d name=%@ %@", [super description], _index, _name, self.fullPath];
    return d;
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
            if (it->_index < 0 || ![a isFilteredOut: it->_index]) {
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
    } else if (_folderChildren == null) {
        int n = 0;
        for (ZG7zipItem* it in _children) {
            n += [_archive isFolder: it->_index];
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
    virtual bool error(const char* file, const char* text) {
        return [delegate file: file error: text];
    }
    virtual const wchar_t* password(P7Z*) { return [delegate password]; }
    virtual bool moveToTrash(P7Z*, const char* pathname) {
        return [delegate moveToTrash: pathname];
    }
    virtual int askOverwrite(P7Z*, const char* fromName, int64_t fromTime, int64_t fromSize,
                                         const char* toName,   int64_t toTime,   int64_t toSize) {
        return [delegate askOverwriteFrom: fromName time: fromTime size: fromSize
                                       to: toName time: toTime size: toSize];
    }
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
        return [delegate itemProperties: itemIndex names: names values: values];
    }
};

@implementation ZG7zip

- (id) init {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        d = new D(self);
        if (d) {
            a = new P7Z(d);
        }
        _pathIndex = -1;
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    if (a != null) {
        [self close];
    }
}

- (NSString*) pathname: (int) i {
    return _paths[i];
}

- (NSDictionary*) props: (int) i {
    return _props[i];
}

- (void) close {
    // trace(@"");
    if (a != null) {
        // trace("folders %d items %d", _numberOfFolders, _numberOfItems);
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

static void reportProgress(ZG7zip* z, int ix) {
    z->_open_progress++;
    if (ix % 100 == 0 && z->_open_progress < z->_open_total) {
        [z progressFile: z->_open_progress ofTotal: z->_open_total];
    }
}

- (BOOL) buildTree {
    _numberOfFolders = 0;
    for (int i = 0; i < _numberOfItems; i++) {
        if (i % 100 == 0 && self.isCancelled) {
            return false;
        }
        const NSString* pathname = _paths[i];
        if (ignore(pathname)) {
            continue;
        }
        // ignore duplicates inside archive for now
        if (_items[pathname] == null) {
            BOOL isFolder = false;
            NSObject* isDir = _props[i][@"IsDir"];
            if ([isDir isKindOfClass: ZGNumber.class] && ((ZGNumber*)isDir).kind == kB) {
                isFolder = ((NSNumber*)isDir).boolValue;
            }
            if (isDir == null && !isFolder) {
                isFolder = a->isDir(i);
            }
            if (isDir == null && isFolder) {
                _props[i][@"IsDir"] = _TRUE;
            }
            ZG7zipItem* item = [ZG7zipItem.alloc initWith: self name: pathname.lastPathComponent index: i isLeaf: !isFolder];
            if (item == null) {
                return false;
            }
            // NSObject* attr = _props[i][@"Attrib"];
            // trace("%@ attr=%@ isDir=%@ isFolder=%d", item.name, attr, isDir, isFolder);
            if (isFolder) {
                [_isFolders setBit: i to: true];
                [_isLeafFolders setBit: i to: true];
                _numberOfFolders++;
            }
            _items[pathname] = item;
        } else {
            console(@"duplicate file \"%@\" ignored", pathname);
        }
        reportProgress(self, i);
    }
    for (int i = 0; i < _numberOfItems; i++) {
        if (i % 100 == 0 && self.isCancelled) {
            return false;
        }
        const NSString* pathname = _paths[i];
        if (ignore(pathname)) {
            continue;
        }
        ZG7zipItem* item = _items[pathname];
        assert(item != null);
        // NSArray* components = [pathname pathComponents]; might be faster
        for (;;) {
            NSString* parentComponents = pathname.stringByDeletingLastPathComponent;
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
                NSString* last = parentComponents.lastPathComponent;
                p = [ZG7zipItem.alloc initWith: self name: last index: -1 isLeaf: false];
                if (p == null) {
                    return false;
                }
                _items[parentComponents] = p;
                _numberOfFolders++;
            }
            item.parent = p;
            // trace(@"0x%016llX.parent:=0x%016llX %@ -> %@", (unsigned long long)item, (unsigned long long)p, p.name, item.name);
            if (p->_index >= 0 && (item->_index < 0 || [_isFolders isSet: item->_index])) {
                [_isLeafFolders setBit: p->_index to: false];
            }
            item = p;
            pathname = parentComponents;
        }
        reportProgress(self, i);
    }
    // because of synthetic parents _items can have more elements than the rest
    int i = 0;
    for (NSString* pathname in _items) {
        if (ignore(pathname)) {
            continue;
        }
        ZG7zipItem* item = _items[pathname];
        assert(item != null);
        assert(item.parent != null);
        [(ZG7zipItem*)item.parent addChild: item];
        i++;
        reportProgress(self, i);
    }
    if (self.isCancelled) {
        return false;
    }
    NSComparator c = ^NSComparisonResult(id f, id s) {
        ZG7zipItem *first = (ZG7zipItem*)f;
        ZG7zipItem *second = (ZG7zipItem*)s;
        return [first.name compare: second.name];
    };
    i = 0;
    for (NSString* pathname in _items) {
        if (ignore(pathname)) {
            continue;
        }
        ZG7zipItem* item = _items[pathname];
        if (item->_index >= 0 && [_isFolders isSet: item->_index]) {
            continue;
        }
        [item.children sortUsingComparator: c];
        i++;
        reportProgress(self, i);
    }
    [_root.children sortUsingComparator: c];
    return !self.isCancelled;
}

// http://regexkit.sourceforge.net/RegexKitLite/#ICUSyntax_ICURegularExpressionSyntax
static const char* kCharsNeedEscaping = "?+[(){}^$|\\./";

- (void) setFilter: (NSString*) filterText operation: (ZGOperation*) op done: (void(^)(BOOL)) block {
    assert(![NSThread isMainThread]);
    // trace("filterText=%@ _filterText=%@", filterText, _filterText);
    bool e0 =  filterText == null ||  filterText.length == 0;
    bool e1 = _filterText == null || _filterText.length == 0;
    if (e0 && e1) {
        return;
    }
    bool e = (e0 && [_filterText isEqualToString: filterText]) ||
             (e1 && [filterText isEqualToString: _filterText]) ||
             [filterText isEqualToString: _filterText];
    // trace("filterText=%@ _filterText=%@ filterText isEqualToString:_filterText=%d", filterText, _filterText, e);
    if (e) {
        return;
    }
    BOOL __block b = false;
    int in = _numberOfItems;
    ZGBitset* isFilteredOut = null;
    if (!filterText || filterText.length == 0) {
        _filterText = null;
        [_isFilteredOut clear];
        b = true;
    } else {
        // timestamp("setFilter");
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
        isFilteredOut = [ZGBitset bitsetWithCapacity: _numberOfItems];
        b = isFilteredOut != null;
        [isFilteredOut fill];
        for (int i = 0; b && i < _numberOfItems; i++) {
            if (i % 100 == 0 && op.isCancelled) {
                b = false;
            } else {
                const NSString* pathname = _paths[i];
                ZG7zipItem* it = _items[pathname];
                if ([pathname rangeOfString:_filterText options:opts].location != NSNotFound) {
                    while (it) {
                        if (it->_index >= 0) {
                            if (![isFilteredOut isSet: it->_index]) {
                                break;
                            } else {
                                [isFilteredOut setBit: it->_index to: false];
                                in--;
                            }
                        }
                        it = (ZG7zipItem*)it.parent;
                    }
                }
            }
        }
        // timestamp("setFilter");
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        // I have to post asynchronously because I need to replace
        // _isFilteredOut and _filterText and it can only be done in main thread
        // However - no deadlock here in sight because in worse case scenario search
        // results will just accumulate on the main queue if it is blocked by e.g.
        // modal dialog box. If main queue is blocked for prolonged period of time
        // it means no new searches are coming in.
        if (b) {
            // trace("search done");
            assert([NSThread isMainThread]);
            bool found = true;
            if (in == _numberOfItems) {
                _filterText = null;
                [_isFilteredOut clear];
                found = false; // not found
            } else {
                _filterText = filterText;
                _isFilteredOut = isFilteredOut;
            }
            // trace("block(%d)", found);
            block(found);
        } else {
            // trace("search cancelled");
            block(false);
        }
    });
}

- (BOOL) isFiltered {
    return _filterText != null;
}

- (BOOL) isCancelled {
    if (_op != null && _op.cancelRequested && !_op.isCancelled) {
        if ([document askCancel: _op]) {
            [_op cancel];
        }
        _op.cancelRequested = false;
    }
    if (_op != null && _op.isCancelled) {
        // trace(@"cancelled");
    }
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
    return _numberOfItems;
}

- (int) numberOfFolders {
    return _numberOfFolders;
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

static NSString* starifyMultipartFilename(NSString* s) {
    NSString* ext = [s pathExtension];
    // http://stackoverflow.com/questions/14928298/garbage-in-the-end-of-nsstring-on-xcode-after-stringbyreplacingcharactersinra
    if ([ext equalsIgnoreCase: @"rar"] || [ext equalsIgnoreCase: @"zip"] || [ext equalsIgnoreCase: @"7z"]) {
        int i = [s lastIndexOfIgnoreCase:@".part"];
        if (i > 0 && isdigit([s characterAtIndex: i + 5])) {
            int j = i + 6; // no need to check s.length because s ends with ".rar"
            while (isdigit([s characterAtIndex: j])) {
                j++;
            }
            if ([s characterAtIndex: j] == '.' && j == s.length - 4) {
                s = [[s substringFrom: 0 to: i + 5] stringByAppendingString: @"*."];
                s = [s stringByAppendingString: ext];
            }
        }
    }
    return s;
}

- (BOOL) readFromURL: (NSURL*) url ofType: (NSString*) type encoding:(NSStringEncoding) enc
            document: (NSObject<ZGArchiveCallbacks>*) doc
           operation: (ZGOperation*) op error: (NSError**) err
                done: (void(^)(NSObject<ZGItemFactory>* factory, NSError* error)) done {
    // This method must be called on the background thread
    assert(![NSThread isMainThread]);
    assert(err != null);
    assert(doc != null);
    assert(op != null);
    document = doc;
    if (self == null || a == null || d == null) {
        *err = ZGOutOfMemoryError();
        return false;
    }
    if (![url isFileURL]) {
        NSMutableDictionary *details = [NSMutableDictionary dictionary];
        [details setValue: ZG_ERROR_LOCALIZED_DESCRIPTION(kIsNotAFile) forKey:NSLocalizedDescriptionKey];
        [details setValue: url forKey:NSURLErrorKey];
        *err = [NSError errorWithDomain: ZGAppErrorDomain code: kIsNotAFile userInfo:details];
        // [NSApp presentError:*err];
        return false;
    }
    _archiveFilePath = [url path];
    // trace("filePath=%@", _archiveFilePath);
    // "/Users/leo/code/zipeg/test/attachment(password-123456).rar"
    // _archiveFilePath = @"/Users/leo/tmp/test-xp-zip 試験.zip";
    // _archiveFilePath = @"/Users/leo/tmp/Райкин-birthday-export-2013-06-24.zip";
    NSString* rn = starifyMultipartFilename(_archiveFilePath.lastPathComponent);
    _root = [ZG7zipItem.alloc initWith: self name: rn index: -1 isLeaf: false];
    assert(self.root != null);
    bool b = false;
    @try {
        _op = op;
        b = a->open(_archiveFilePath.fileSystemRepresentation) && a->getNumberOfItems() > 0 && a->getNumberOfProperties() > 0;
        if (b) {
            _numberOfItems = a->getNumberOfItems();
            _properties = a->getNumberOfProperties();
            _isFilteredOut = [ZGBitset bitsetWithCapacity:_numberOfItems];
            _isFolders = [ZGBitset bitsetWithCapacity:_numberOfItems];
            _isLeafFolders = [ZGBitset bitsetWithCapacity:_numberOfItems];
            _items = [NSMutableDictionary dictionaryWithCapacity:_numberOfItems * 3 / 2];
            _paths = [NSMutableArray arrayWithCapacity:_numberOfItems];
            _props = [NSMutableArray arrayWithCapacity:_numberOfItems];
            _pnames = [NSMutableArray arrayWithCapacity:_properties];
            if (!_isFolders || !_isLeafFolders || !_isFilteredOut || !_items || !_paths || !_pnames || !_props) {
                a->close();
                *err = ZGOutOfMemoryError();
                return false;
            }
            b = a->iterateArchiveProperies();
            if (b) {
                CFStringEncoding encoding = [self detectEncoding];
                a->setCodePage(encoding);
                _open_total = _numberOfItems * 5; // I do about 5 passes through
                _open_progress = 0;
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
                                             code: kArchiverError
                                         userInfo: @{ NSLocalizedDescriptionKey: _error }
                         ];
            *err = [NSError errorWithDomain: NSCocoaErrorDomain code: NSFileReadCorruptFileError
                                   userInfo: @{NSFilePathErrorKey: _archiveFilePath, NSUnderlyingErrorKey: e
                    }];
        }
    } @finally {
        _op = null; // we must release the opearation here
        // [NSThread sleepForTimeInterval: 5]; // seconds
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
            o = [[NSString alloc] initWithBytes: v.str length: wcslen(v.str) * sizeof(wchar_t)
                                       encoding: NSUTF32LittleEndianStringEncoding];
            break;
        case P7Z::Value::kFiletime:
            o = [ZGNumber numberWithLongLong: (unsigned long long)v.num];
            break;
        case P7Z::Value::kBool:
            o = [ZGNumber numberWithBool: v.num != 0];
            break;
        case P7Z::Value::kI1:
            o = [ZGNumber numberWithUnsignedChar: (unsigned char)v.num];
            break;
        case P7Z::Value::kI2:
            o = [ZGNumber numberWithUnsignedShort: (unsigned short)v.num];
            break;
        case P7Z::Value::kI4:
            o = [ZGNumber numberWithUnsignedInt: (unsigned int)v.num];
            break;
        case P7Z::Value::kI8:
            o = [ZGNumber numberWithUnsignedLongLong: (unsigned long long)v.num];
            break;
        default:
            assert(false);
            break;
    }
    return o;
}

- (BOOL) itemProperties: (int) itemIndex names: (const char*[]) names values: (P7Z::Value*[])values {
    NSMutableDictionary* dic = [NSMutableDictionary dictionaryWithCapacity: _properties * 3 / 2];
    if (itemIndex == 0) {
        for (int i = 0; i < _properties; i++) {
            _pnames[i] = [NSString stringWithFileSystemRepresentation: names[i]];
            if (_pathIndex < 0 && [_pnames[i] isEqualToString:@"Path"]) {
                _pathIndex = i;
            }
        }
        if (_pathIndex < 0) {
            return false;
        }
    }
    int codePage = a->getCodePage();
    NSString* path = null;
    for (int i = 0; i < _properties; i++) {
        NSObject *o = p7zValueToObject(*values[i]);
        if (i == _pathIndex) {
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
            if ([_pnames[i] hasSuffix: @"Time"] || [_pnames[i] hasSuffix: @"MTime"] || [_pnames[i] hasSuffix: @"ATime"]) {
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
    if ([path hasPrefix: @"/"]) {
        path = [path substringFromIndex: 1]; // fix the absolute path if present
    }
    if ([path hasSuffix: @"/"]) {
        dic[@"IsDir"] = _TRUE;
        path = [path substringFrom: 0 to: (int)path.length - 1];
    }
    _paths[itemIndex] = path;
    _props[itemIndex] = dic;

    reportProgress(self, itemIndex);
/*  // KEEP this code around, uncommenting it is handy to archive problems debugging
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

- (BOOL) file: (const char*) file error: (const char*) message {
    NSString* path = file == null ? @"" : [NSString stringWithFileSystemRepresentation: file];
    bool b = [document askToContinue: _op
                                path: path
              error: [NSString stringWithUTF8String: message]];
    if (!b && _op != null) {
        [_op cancel];
    }
    return b;
}

- (BOOL) moveToTrash: (const char*) pathname {
    return [document moveToTrash: pathname];
}

- (int) askOverwriteFrom: (const char*) fromName time: (int64_t) fromTime size: (int64_t) fromSize
                      to: (const char*) toName time: (int64_t) toTime size: (int64_t) toSize {
    int r = [document askOverwrite: _op
                              from: (const char*) fromName
                              time: (int64_t) fromTime size: (int64_t) fromSize
                                to: (const char*) toName time: (int64_t) toTime
                              size: (int64_t) toSize];
    if (r == kCancel && _op != null) {
        [_op cancel];
    }
    return r;
}

- (const wchar_t*) password {
    if (!_password || _password.length == 0) {
        // [NSThread sleepForTimeInterval: 5]; // seconds
        _password = [document askPassword: _op];
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

- (BOOL) progress: (long long) pos ofTotal: (long long) total {
    assert(![NSThread isMainThread]);
    return [document progress: _op pos: pos ofTotal: total] && !self.isCancelled;
}

- (BOOL) progressFile:(long long) fileno ofTotal: (long long)totalNumberOfFiles {
    assert(![NSThread isMainThread]);
    return [document progressFiles: _op fileno: fileno ofTotal: totalNumberOfFiles] && !self.isCancelled;
}

- (int) countChildren: (NSArray*) itms {
    int c = 0;
    for (ZG7zipItem* it in itms) {
        if (it.children != 0 && it.children > 0) {
            c+= [self countChildren: it.children];
        }
        c += it->_index >= 0;
    }
    return c;
}

- (int) collectChildren: (NSArray*) itms to: (int*) indices position: (int) p size: (int) n {
    for (ZG7zipItem* it in itms) {
        if (it.children != 0 && it.children > 0) {
            p = [self collectChildren: it.children to: indices position: p size: n];
        }
        if (it->_index >= 0) {
            // children first because we want to set correct time attributes on parent folders:
            assert(p < n);
            indices[p++] = it->_index;
        }
    }
    return p;
}

- (const char**) prefixComponents: (NSArray*) itms count: (int*) count {
    const char** prefixComponents = null;
    int pc = 0;
    // all items MUST have the same parent and it's pathname will be stripped
    ZG7zipItem* parent = null;
    for (ZG7zipItem* it in itms) {
        if (parent == null) {
            parent = (ZG7zipItem*)it.parent;
        } else {
            assert(isEqual(parent, it.parent));
        }
    }
    if (!isEqual(parent, _root)) {
        pc = 0;
        NSObject<ZGItemProtocol>* p = parent;
        while (!isEqual(p, _root)) {
            pc++;
            p = p.parent;
        }
        prefixComponents = new const char*[pc];
        if (prefixComponents == null) {
            *count = -1;
            return null;
        }
        int i = pc;
        p = parent;
        while (!isEqual(p, _root)) {
            NSString* name = p.name;
            prefixComponents[--i] = name.fileSystemRepresentation;
            p = p.parent;
        }
        assert(i == 0);
    }
    *count = pc;
    return prefixComponents;
}

- (void) extract: (NSArray*) itms to: (NSURL*) url operation: (ZGOperation*) op fileDescriptor: (int) fd
            done: (void(^)(NSError* e)) block {
    assert(![NSThread isMainThread]);
    if (![url isFileURL]) {
        NSMutableDictionary *details = [NSMutableDictionary dictionary];
        [details setValue: ZG_ERROR_LOCALIZED_DESCRIPTION(kIsNotAFile) forKey: NSLocalizedDescriptionKey];
        [details setValue:url forKey: NSURLErrorKey];
        NSError* err = [NSError errorWithDomain: ZGAppErrorDomain code: kIsNotAFile userInfo: details];
        block(err);
        return;
    }
    @try {
        _op = op;
        NSString* path = [url path];
        int n = -1;
        int* indices = null;
        const char** prefixComponents = null;
        int pc = 0;
        if (itms != null && itms.count > 0) {
            prefixComponents = [self prefixComponents: itms count: &pc];
            if (pc < 0) {
                block(ZGOutOfMemoryError());
                return;
            }
            int max = [self countChildren: itms];
            indices = new int[max];
            if (indices == null) {
                delete[] prefixComponents;
                block(ZGOutOfMemoryError());
                return;
            }
            n = [self collectChildren: itms to: indices position: 0 size: max];
        }
        _error = null;
        // timestamp("extract");
        bool b = a->extract(indices, n, path.fileSystemRepresentation, prefixComponents, pc, fd);
        // timestamp("extract");
        delete[] indices;
        delete[] prefixComponents;
        if (_error == null && !b && self.isCancelled) {
            NSError* err = [NSError errorWithDomain: NSCocoaErrorDomain code: NSUserCancelledError
                                           userInfo: @{ NSFilePathErrorKey: _archiveFilePath }];
            block(err);
        } else if (_error == null) {
            block(b ? null : ZGInternalError());
        } else {
            // TODO: better diag
            NSError* err = [NSError errorWithDomain: ZGAppErrorDomain code: kIsNotAFile
                                           userInfo: @{ NSFilePathErrorKey: _archiveFilePath,
                          NSLocalizedDescriptionKey: _error }];
            block(err);
        }
    } @finally {
        _op = null;
    }
}

@end
