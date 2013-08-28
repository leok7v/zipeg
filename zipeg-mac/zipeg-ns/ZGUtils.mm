#include "p7z.hpp"
#include "HashMapS2L.hpp"
#include "NanoTime.hpp"
#import "MacMem.h"
#import "ZGItemProtocol.h"
#import "ZGDocument.h"
#include <unistd.h>

#define NOT_A_VALUE 0xFFFFFFFFFFFFFFFFULL

static NSMutableDictionary* observers;

id addObserver(NSString* n, id o, void(^b)(NSNotification*)) {
    assert(n != null && b != null);
    id observer = [NSNotificationCenter.defaultCenter
                   addObserverForName: n
                               object: o
                                queue: NSOperationQueue.mainQueue
                           usingBlock: b];
    if (observers == null) {
        observers = [NSMutableDictionary dictionaryWithCapacity:100];
    }
    NSNumber* a = @((uint64_t)(__bridge void*)observer);
    NSString* v = observers[a];
    assert(v == null);
    observers[a] = n;
    return observer;
}

id removeObserver(id observer) {
    assert(observers != null);
    if (observer != null) {
        NSNumber* a = @((uint64_t)(__bridge void*)observer);
        assert(observers[a] != null);
        [observers removeObjectForKey: a];
    }
    assert(observer == null || [NSStringFromClass(((NSObject*)observer).class) isEqual: @"__NSObserver"]);
    [NSNotificationCenter.defaultCenter removeObserver: observer];
    return null;
}

void traceObservers() {
    for (NSNumber* a in observers.allKeys) {
        NSString* n = observers[a];
        id observer = (__bridge NSObject*)(void*)a.longLongValue;
        NSLog(@"observer[%@]=%@", n, observer);
    }
    if (observers.count == 0) {
        NSLog(@"on observers");
    }
}

uint64_t timestamp(const char* label) {
    return NanoTime::timestamp(label);
}

uint64_t nanotime() {
    return NanoTime::time();
}

static HashMapS2L mem(500, NOT_A_VALUE);

uint64_t alloc_count(id i) {
    @synchronized (ZGUtils.class) {
        NSObject* o = (NSObject*)i;
        const char* cn = NSStringFromClass(o.class).UTF8String;
        uint64_t v = mem.get(cn);
        if (v == NOT_A_VALUE) {
            v = 0;
        }
        mem.put(cn, ++v);
        return v;
    }
}

uint64_t dealloc_count(id i) {
    @synchronized (ZGUtils.class) {
        NSObject* o = (NSObject*)i;
        const char* cn = NSStringFromClass(o.class).UTF8String;
        uint64_t v = mem.get(cn);
        assert(v != NOT_A_VALUE); // dealloc before alloc?!
        assert(v > 0); // too many deallocs
        mem.put(cn, --v);
        return v;
    }
}

void trace_allocs() {
    @synchronized (ZGUtils.class) {
        int n = mem.getCapacity();
        for (int i = 0; i < n; i++) {
            const char* k = mem.keyAt(i);
            if (k != null) {
                int64_t v = mem.get(k);
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
            if ([NSFileManager.defaultManager fileExistsAtPath: p isDirectory: &d] && d) {
                b = rmdirs(p) == 0 && b;
            } else {
                // trace("unlink(%@)", p);
                b = unlink(p.UTF8String) == 0 && b;
            }
        }
    }
    // trace("rmdir(%@)", path);
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

+ (NSArray*) findViews: (NSView*) v byClassName: (NSString*) cn all: (NSMutableArray*) a {
    if ([cn isEqualToString: NSStringFromClass(v.class)]) {
        [a addObject: v];
    }
    if (v.subviews != null) {
        for (id s in v.subviews) {
            [self findViews: (NSView*)s byClassName: cn all: a];
        }
    }
    return a;
}

+ (NSView*) findView: (NSView*) v byClassName: (NSString*) cn tag: (int64_t) t {
    if ([cn isEqualToString: NSStringFromClass(v.class)]) {
        if (t == LLONG_MIN || v.tag == t) {
            return v;
        }
    }
    NSView* r = null;
    if (v.subviews != null) {
        for (id s in v.subviews) {
            r = [self findView: (NSView*)s byClassName: cn tag: t];
            if (r != null) {
                break;
            }
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

- (NSArray*) findViewsByClassName: (NSString*) className {
    NSMutableArray* a = [NSMutableArray arrayWithCapacity: 4];
    return [NSView findViews: self byClassName: className all: a];
}

@end

@implementation NSImage(ZGExtensions)

- (id) initWithCGImage: (CGImageRef) ir {
    if (ir == null) {
        self = null;
    } else {
        NSBitmapImageRep* bir = [NSBitmapImageRep.alloc initWithCGImage: ir];
        if (bir != null) {
            self = [self initWithSize: bir.size];
            if (self != null) {
                [self addRepresentation: bir];
            }
        }
    }
    return self;
}

- (NSImage*) imageRotatedByDegrees: (CGFloat) degrees {
    while (degrees < 0) {
        degrees += 360;
    }
    while (degrees > 360) {
        degrees -= 360;
    }
    [NSGraphicsContext.currentContext saveGraphicsState];
    NSSize rs = NSMakeSize(self.size.height, self.size.width);
    NSImage* ri = [[NSImage alloc] initWithSize: rs];
    NSAffineTransform* t = [NSAffineTransform transform] ;
    [t translateXBy:  self.size.width / 2 yBy: self.size.height / 2] ;
    [t rotateByDegrees: degrees];
    // Then translate the origin system back to the bottom left
    [t translateXBy: -rs.width / 2 yBy: -rs.height / 2] ;
    [ri lockFocus]; // NSGraphicsContext.currentContext = rotatedImage
    NSGraphicsContext.currentContext.imageInterpolation = NSImageInterpolationHigh;
    [t concat];
    [self drawAtPoint: NSMakePoint(0, 0) fromRect: NSZeroRect operation: NSCompositeCopy fraction: 1];
    [ri unlockFocus];
    [NSGraphicsContext.currentContext restoreGraphicsState];
    return ri;
}

- (NSImage*) mirror {
    if (self == 0) {
        return null;
    } else {
        NSImage* m = [NSImage.alloc initWithSize: self.size];
        if (m != null) {
            [NSGraphicsContext.currentContext saveGraphicsState];
            NSAffineTransform *t = [NSAffineTransform transform];
            // if original image was flipped we will render it upsidedown and the
            // resulting image absorbs "flipped" state and is not flipped anymore
            m.flipped = false;
            [t scaleXBy: -1 yBy: 1];
            [t translateXBy: -self.size.width yBy: 0];
            [m lockFocus]; // NSGraphicsContext.currentContext = m
            NSGraphicsContext.currentContext.imageInterpolation = NSImageInterpolationHigh;
            [t concat];
            [self drawAtPoint: NSMakePoint(0, 0) fromRect:NSZeroRect operation: NSCompositeCopy fraction: 1];
            [m unlockFocus];
            [NSGraphicsContext.currentContext restoreGraphicsState];
        }
        return m;
    }
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
            const dispatch_time_t dt = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(seconds * NSEC_PER_SEC));
            dispatch_after(dt, dispatch_get_main_queue(), i);
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

+ (NSString*) createTemporaryFolder: (NSString*) name {
    NSString* folder = null;
    NSString* guid = NSProcessInfo.processInfo.globallyUniqueString;
    NSString* t = [NSTemporaryDirectory() stringByAppendingPathComponent: [name stringByAppendingString: guid]];
    const char* cc = [t fileSystemRepresentation];
    if (cc != null) {
        char* cs = strdup(cc);
        if (cs != null) {
            char* r = mkdtemp(cs);
            if (r != null) {
                NSAssert(r == cs, @"expected mkdtemp return value and parameter to be the same");
                folder = [NSFileManager.defaultManager stringWithFileSystemRepresentation: r length: strlen(r)];
            }
            free(cs);
        }
    }
    return folder;
}

+ (int) createTemporaryFile: (NSString*) path result: (NSString**) res {
    NSAssert([path startsWith: NSTemporaryDirectory()], @"path must start in NSTemporaryDirectory()");
    int fd = -1;
    NSString* ext = path.pathExtension;
    NSString* name = (ext != null && ext.length > 0) ? path.stringByDeletingPathExtension : path;
    int retry = 16;
    while (retry > 0) {
        NSString* guid = NSProcessInfo.processInfo.globallyUniqueString;
        NSString* t = [name stringByAppendingString: guid];
        if (ext != null && ext.length > 0) {
            t = [t stringByAppendingPathExtension: ext];
        }
        const char* cc = [t fileSystemRepresentation];
        if (cc != null) {
            fd = open(cc, O_CREAT|O_RDWR|O_EXLOCK|O_CLOEXEC);
            if (fd != -1) {
                if (res != null) {
                    *res = [NSFileManager.defaultManager stringWithFileSystemRepresentation: cc length: strlen(cc)];
                    // trace("createTemporaryFile OK: %s", cc);
                }
                break;
            } else {
                // trace("createTemporaryFile failed for: %s %d", cc, errno);
            }
        }
        retry--;
    }
    return fd;
}

@end
