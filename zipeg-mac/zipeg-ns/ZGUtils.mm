#include "p7z.hpp"
#include "HashMapS2L.hpp"
#include "NanoTime.hpp"
#import "MacMem.h"
#import "ZGItemProtocol.h"
#import "ZGDocument.h"

FOUNDATION_EXPORT uint64_t timestamp(const char* label) {
    return NanoTime::timestamp(label);
}

#define NOT_A_VALUE 0xFFFFFFFFFFFFFFFFULL

static HashMapS2L map(500, NOT_A_VALUE);

FOUNDATION_EXPORT uint64_t alloc_count(id i) {
    @synchronized (ZGUtils.class) {
        NSObject* o = (NSObject*)i;
        const char* cn = [NSStringFromClass([o class]) cStringUsingEncoding:NSUTF8StringEncoding];
        uint64_t v = map.get(cn);
        if (v == NOT_A_VALUE) {
            v = 0;
        }
        map.put(cn, ++v);
        return v;
    }
}

FOUNDATION_EXPORT uint64_t dealloc_count(id i) {
    @synchronized (ZGUtils.class) {
        NSObject* o = (NSObject*)i;
        const char* cn = [NSStringFromClass([o class]) cStringUsingEncoding:NSUTF8StringEncoding];
        uint64_t v = map.get(cn);
        assert(v != NOT_A_VALUE); // dealloc before alloc?!
        assert(v > 0); // too many deallocs
        map.put(cn, --v);
        return v;
    }
}

FOUNDATION_EXPORT void trace_allocs() {
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
        NSLog(@"%lld bytes in %lld allocs\n", mstat.bytes, mstat.allocs);
    }
}

static void _dumpViews(NSView* v, int level) {
    NSString* indent = @"";
    for (int i = 0; i < level; i++) {
        indent = [indent stringByAppendingString:@"    "];
    }
    NSString* debugDelegate = @"";
    if ([v respondsToSelector:@selector(delegate)]) {
        id delegate = [(id)v delegate];
        if (delegate != null) {
            debugDelegate = [delegate debugDescription];
        }
    }
    NSLog(@"%@%@ %@ %@", indent, v.class, NSStringFromRect(v.frame), debugDelegate);
    if (v.subviews != null) {
        for (id s in v.subviews) {
            _dumpViews(s, level + 1);
        }
    }
}

FOUNDATION_EXPORT void dumpViews(NSView* v) {
    _dumpViews(v, 0);
}

FOUNDATION_EXPORT void dumpAllViews() {
    NSDocumentController* dc = NSDocumentController.sharedDocumentController;
    NSArray* docs = dc.documents;
    if (docs != null && docs.count > 0) {
        for (int i = 0; i < docs.count; i++) {
            ZGDocument* doc = (ZGDocument*)docs[i];
            if (doc.window != null) {
                NSLog(@"%@", doc.displayName);
                dumpViews(doc.window.contentView);
                NSLog(@"");
            }
        }
    }
}


@implementation NSString(ZGExtensions)

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
        sourceListBackgroundColor =
        [NSColor colorWithCatalogName:@"System" colorName:@"_sourceListBackgroundColor"];
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
    void(^done)();
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

-(void) cancel {
    if (done == null) {
        NSLog(@"ZGBlock.cancel: too late, already executing or executed");
    } else {
        trace("cancelled %@", done);
        _isCanceled = true;
        done = null;
    }
}

-(BOOL) isExecuting {
    return done == null && !_isDone;
}

-(void) invokeNow {
    if (![NSThread isMainThread]) {
        @throw @"invokeNow can be called only on main thread";
    } else {
        
    }
}

@end

@implementation ZGUtils

+ (ZGBlock*) invokeLater: (void(^)()) b {
    assert(b != null);
    ZGBlock* block = [ZGBlock new];
    block->done = b;
    dispatch_async(dispatch_get_current_queue(), ^(){
        if (!block.isCanceled && !block.isDone) {
            void(^d)() = block->done;
            block->done = null;
            d();
        }
    });
    return block;
}

@end
