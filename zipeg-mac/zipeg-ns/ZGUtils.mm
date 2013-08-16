#include "p7z.hpp"
#include "HashMapS2L.hpp"
#include "NanoTime.hpp"
#import "MacMem.h"
#import "ZGItemProtocol.h"
#import "ZGDocument.h"

id addObserver(NSString* n, id o, void(^b)(NSNotification*)) {
    assert(n != null && b != null);
    return [NSNotificationCenter.defaultCenter addObserverForName: n object: o
                                                            queue: NSOperationQueue.mainQueue usingBlock: b];
}

id removeObserver(id observer) {
    [NSNotificationCenter.defaultCenter removeObserver: observer];
    return null;
}

uint64_t timestamp(const char* label) {
    return NanoTime::timestamp(label);
}

uint64_t nanotime() {
    return NanoTime::time();
}

#define NOT_A_VALUE 0xFFFFFFFFFFFFFFFFULL

static HashMapS2L map(500, NOT_A_VALUE);

uint64_t alloc_count(id i) {
    @synchronized (ZGUtils.class) {
        NSObject* o = (NSObject*)i;
        const char* cn = NSStringFromClass(o.class).UTF8String;
        uint64_t v = map.get(cn);
        if (v == NOT_A_VALUE) {
            v = 0;
        }
        map.put(cn, ++v);
        return v;
    }
}

uint64_t dealloc_count(id i) {
    @synchronized (ZGUtils.class) {
        NSObject* o = (NSObject*)i;
        const char* cn = NSStringFromClass(o.class).UTF8String;
        uint64_t v = map.get(cn);
        assert(v != NOT_A_VALUE); // dealloc before alloc?!
        assert(v > 0); // too many deallocs
        map.put(cn, --v);
        return v;
    }
}

void trace_allocs() {
    @synchronized (ZGUtils.class) {
        int n = map.getCapacity();
        for (int i = 0; i < n; i++) {
            const char* k = map.keyAt(i);
            if (k != null) {
                int64_t v = map.get(k);
                if (v != NOT_A_VALUE) {
                    NSLog(@"%s %lld", k, v);
                }
            }
        }
        NSNumberFormatter* nf = [NSNumberFormatter new];
        nf.numberStyle = NSNumberFormatterDecimalStyle;
        NSString* b = [nf stringFromNumber: [NSNumber numberWithLongLong: mstat.bytes] ];
        NSString* a = [nf stringFromNumber: [NSNumber numberWithLongLong: mstat.allocs] ];
        NSLog(@"%@ bytes in %@ allocs\n", b, a);
    }
}

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Warc-performSelector-leaks"

BOOL responds(id o, SEL sel) {
    return [o respondsToSelector: sel];
}

id call(id o, SEL sel) {
    return [o respondsToSelector: sel] ? [o performSelector: sel] : nil;
}

id call1(id o, SEL sel, id p) {
    return [o respondsToSelector: sel] ? [o performSelector: sel withObject: p] : nil;
}

id call2(id o, SEL sel, id p1, id p2) {
    return [o respondsToSelector: sel] ? [o performSelector: sel withObject: p1 withObject: p2] : nil;
}

#pragma clang diagnostic pop

static id getBySelector(id o, SEL sel) {
    return responds(o, sel) ? call(o, sel) : nil;
}

static NSString* debugDescription(id o, SEL sel) {
    id s = [getBySelector(o, sel) debugDescription];
    return s ? s : @"";
}

static void _dumpViews(NSView* v, int level) {
    if (v == null) {
        return;
    }
    NSString* indent = @"";
    for (int i = 0; i < level; i++) {
        indent = [indent stringByAppendingString:@"    "];
    }
    NSString* delegate = debugDescription(v, @selector(delegate));
    NSString* dataSource = debugDescription(v, @selector(dataSource));
    NSString* dataCell = debugDescription(v, @selector(dataCell));
    NSString* frame = [v respondsToSelector: @selector(frame)] ?
          [@" frame=" stringByAppendingString: NSStringFromRect(v.frame)] : @"";
    NSString* bounds = [v respondsToSelector: @selector(bounds)] ?
          [@" bounds=" stringByAppendingString: NSStringFromRect(v.bounds)] : @"";
    NSString* tag = [v respondsToSelector: @selector(tag)] ?
          [@" tag=" stringByAppendingFormat: @"%ld", [v tag]] : @"";
    NSString* t = call(v, @selector(title));
    NSString* title = t != null ? [@" title=" stringByAppendingString: t] : @"";
    NSString* sv = [v isKindOfClass: NSTextField.class] ? [(id)v stringValue] : null;
    NSString* text = sv != null ? [@" stringValue=" stringByAppendingString: sv] : @"";
    NSLog(@"%@%@%@%@ %@%@%@%@%@%@", indent, v.class, frame, bounds, delegate, dataSource, dataCell, tag, title, text);
    id subviews = getBySelector(v, @selector(subviews));
    if (subviews != null) {
        for (id s in subviews) {
            _dumpViews(s, level + 1);
        }
        _dumpViews(getBySelector(v, @selector(headerView)), level + 1);
        NSArray* tcs = getBySelector(v, @selector(tableColumns));
        if (tcs != null) {
            for (NSTableColumn* tc in tcs) {
                _dumpViews(getBySelector(tc, @selector(dataCell)), level + 2);
            }
        }
    }
}

void dumpViews(NSView* v) {
    _dumpViews(v, 0);
}

void dumpAllViews() {
    NSArray* docs = ((NSDocumentController*)NSDocumentController.sharedDocumentController).documents;
    for (int i = 0; i < docs.count; i++) {
        ZGDocument* doc = (ZGDocument*)docs[i];
        if (doc.window != null) {
            NSLog(@"%@", doc.displayName);
            dumpViews([doc.window.contentView superview]);
            NSLog(@"");
        }
    }
}

BOOL rmdirs(NSString* path) {
    // http://www.unix.com/man-page/POSIX/3posix/rmdir/
    // If path names a symbolic link, then rmdir() shall fail and set errno to [ENOTDIR]
    BOOL b = true;
    NSDictionary* a = [NSFileManager.defaultManager attributesOfItemAtPath: path error: null];
    if (a[NSFileType] == NSFileTypeSymbolicLink) {
        // http://linux.die.net/man/2/unlink
        trace("unlink(%@) - symbolic link", path);
        b = unlink(path.UTF8String) == 0 && b; // If the name referred to a symbolic link the link is removed.
        return b; // do not follow symbolic links
    }
    NSArray* filenames = [NSFileManager.defaultManager contentsOfDirectoryAtPath: path error: null];
    for (NSString* fn in filenames) {
        if (![fn isEqualToString: @".."] && ![fn isEqualToString: @"."]) {
            NSString* p = [path stringByAppendingPathComponent: fn];
            BOOL d = false;
            if ([NSFileManager.defaultManager fileExistsAtPath: p isDirectory: &d] && !d) {
                b = rmdirs(p) == 0 && b;
            } else {
                trace("unlink(%@)", p);
                b = unlink(p.UTF8String) == 0 && b;
            }
        }
    }
    trace("rmdir(%@)", path);
    b = rmdir(path.UTF8String) == 0 && b;
    return b;
}

void subtreeDescription(NSView* v) {
    NSLog(@"%@", [v performSelector: @selector(_subtreeDescription)]);
}

BOOL isEqual(NSObject* o1, NSObject* o2) {
    return o1 == o2 || (o1 == null ? o2 == null : [o1 isEqual: o2]);
}

@implementation NSString(ZGExtensions)

- (BOOL) equalsIgnoreCase: (NSString*) s {
    return isEqual(self, s) || (self.length == s.length && [self indexOfIgnoreCase: s] == 0);
}

- (int) indexOf: (NSString*) s {
    NSRange r = [self rangeOfString: s];
    return r.length > 0 ? (int)r.location : -1;
}

- (int) indexOfIgnoreCase: (NSString*) s {
    NSRange r = [self rangeOfString: s options: NSCaseInsensitiveSearch];
    return r.length > 0 ? (int)r.location : -1;
}

- (int) lastIndexOf: (NSString*) s {
    NSRange r = [self rangeOfString: s options: NSBackwardsSearch];
    return r.length > 0 ? (int)r.location : -1;
}

- (int) lastIndexOfIgnoreCase: (NSString*) s {
    NSRange r = [self rangeOfString: s options: NSCaseInsensitiveSearch|NSBackwardsSearch];
    return r.length > 0 ? (int)r.location : -1;
}

- (int) endsWith: (NSString*) s {
    return [self lastIndexOf: s] == self.length - s.length;
}

- (int) endsWithIgnoreCase: (NSString*) s {
    return [self lastIndexOfIgnoreCase: s] == self.length - s.length;
}

- (int) startsWith: (NSString*) s {
    return [self indexOf: s] == 0;
}

- (int) startsWithIgnoreCase: (NSString*) s {
    return [self indexOfIgnoreCase: s] == 0;
}

- (BOOL) contains: (NSString*) s {
    return [self indexOf: s] >= 0;
}

- (BOOL) containsIgnoreCase: (NSString*) s {
    return [self indexOfIgnoreCase: s] >= 0;
}

- (NSString*) substringFrom: (int) from to: (int) to {
    if (to == from) {
        return @"";
    } else if (from < to) {
        NSRange r = NSMakeRange(from, to - from);
        return [self substringWithRange: r];
    } else {
        @throw NSRangeException;
    }
}

@end

@implementation NSView(ZGExtensions)

+ (NSView*) findView: (NSView*) v byClassName: (NSString*) cn tag: (int64_t) t {
    if ([cn  isEqualToString: NSStringFromClass(v.class)]) {
        if (t < 0 || v.tag == LLONG_MIN) {
            return v;
        }
    }
    NSView* r = null;
    if (v.subviews != null) {
        for (id s in v.subviews) {
            r = [self findView: (NSView*)s byClassName: cn tag: t];
        }
    }
    return r;
}

- (NSView*) findViewByClassName: (NSString*) className {
    return [NSView findView: self byClassName: className tag: LLONG_MIN];
}

- (NSView*) findViewByClassName: (NSString*) className tag: (int) t {
    return [NSView findView: self byClassName: className tag: t];
}


- (void) setOrigin: (NSPoint) pt {
    NSRect f = self.frame;
    f.origin = pt;
    self.frame = f;
}

- (void) setSize: (NSSize) sz {
    NSRect f = self.frame;
    f.size = sz;
    self.frame = f;
}

@end

@implementation ZGOperation
@synthesize cancelRequested;
@end

@implementation NSOutlineView(ZGExtensions)

- (void)expandParentsOfItem: (id) item {
    // NOTE: [self parentForItem: item] always returns null
    // (I guess for the absence of the method in DataSource)
    NSObject<ZGItemProtocol>* i = item;
    while (i != nil) {
        NSObject<ZGItemProtocol>* parent = i.parent;
        if (parent != null) {
            [self expandItem: parent expandChildren: false];
        }
        i = parent;
    }
}

- (void) selectItem: (id) item {
    NSInteger itemIndex = [self rowForItem: item];
    if (itemIndex < 0) {
        [self expandParentsOfItem: item];
        itemIndex = [self rowForItem: item];
        if (itemIndex < 0) {
            return;
        }
    }
    [self selectRowIndexes: [NSIndexSet indexSetWithIndex: itemIndex] byExtendingSelection: false];
}

@end

@implementation NSColor(ZGExtensions)

+ (NSColor*) sourceListBackgroundColor {
    static NSColor* sourceListBackgroundColor = null;
    if (sourceListBackgroundColor == null) {
        sourceListBackgroundColor = [NSColor colorWithCatalogName:@"System" colorName: @"_sourceListBackgroundColor"];
        if (sourceListBackgroundColor == null) {
            NSTableView *tv = [[NSTableView alloc] initWithFrame: NSZeroRect];
            tv.selectionHighlightStyle = NSTableViewSelectionHighlightStyleSourceList;
            sourceListBackgroundColor = tv.backgroundColor;
        }
        if (sourceListBackgroundColor == null) {
            sourceListBackgroundColor =
            [NSColor colorWithCalibratedRed:0.905882
                                      green:0.929412
                                       blue:0.964706 alpha:1.0];
            
        }
    }
    return sourceListBackgroundColor;
}

@end

@implementation ZGBlock {
@public
    void (^_done)();
    BOOL _isCanceled;
    BOOL _isDone;
}
@synthesize isCanceled;
@synthesize isDone;

- (id) init {
    self = [super init];
    alloc_count(self);
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (id) cancel {
    _isCanceled = true;
    if (_done != null) {
//       trace("cancelled %@", self);
        _done = null;
    }
    return null; // always - see usages
}

- (BOOL) isExecuting {
    return _done == null && !_isDone;
}

- (void) invokeNow {
    if (![NSThread isMainThread]) {
        @throw @"invokeNow can be called only on main thread";
    } else {
        void(^d)() = _done;
        if (d != null) {
            _done = null;
            d();
        }
    }
}

@end

@implementation ZGUtils

+ (ZGBlock*) invokeLater: (void(^)()) b {
    return [ZGUtils invokeLater: b delay: 0];
}

+ (ZGBlock*) invokeLater: (void(^)()) b delay: (double) seconds {
    assert(b != null);
    ZGBlock* block = [ZGBlock new];
    if (block) {
        block->_done = b;
        void(^i)() = ^() {
            if (!block.isCanceled && !block.isDone) {
                void(^d)() = block->_done;
                if (d != null) {
                    block->_done = null;
                    d();
                }
            }
        };
        if (seconds > 0) {
            const dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(seconds * NSEC_PER_SEC));
            dispatch_after(popTime, dispatch_get_main_queue(), i);
        } else {
            dispatch_async(dispatch_get_current_queue(), i);
        }
    }
    return block;
}

// done will be called on the same background thread
+ (void) rmdirsOnBackgroundThread: (NSString*) path done: (void(^)(BOOL)) done {
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_LOW, 0), ^{
        done(rmdirs(path));
    });
}

@end
