#include "p7z.hpp"
#include "HashMapS2L.hpp"
#include "NanoTime.hpp"
#import "MacMem.h"

FOUNDATION_EXPORT uint64_t timestamp(const char* label) {
    return NanoTime::timestamp(label);
}

#define NOT_A_VALUE 0xFFFFFFFFFFFFFFFFULL

static HashMapS2L map(500, NOT_A_VALUE);

FOUNDATION_EXPORT uint64_t alloc_count(id i) {
    NSObject* o = (NSObject*)i;
    const char* cn = [NSStringFromClass([o class]) cStringUsingEncoding:NSUTF8StringEncoding];
    uint64_t v = map.get(cn);
    if (v == NOT_A_VALUE) {
        v = 0;
    }
    map.put(cn, ++v);
    return v;
}

FOUNDATION_EXPORT uint64_t dealloc_count(id i) {
    NSObject* o = (NSObject*)i;
    const char* cn = [NSStringFromClass([o class]) cStringUsingEncoding:NSUTF8StringEncoding];
    uint64_t v = map.get(cn);
    assert(v != NOT_A_VALUE); // dealloc before alloc?!
    assert(v > 0); // too many deallocs
    map.put(cn, --v);
    return v;
}

FOUNDATION_EXPORT void trace_allocs() {
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
    NSLog(@"%lld bytes in %lld allocs", mstat.bytes, mstat.allocs);
}

static void _dumpViews(NSView* v, int level) {
    NSString* indent = @"";
    for (int i = 0; i < level; i++) {
        indent = [indent stringByAppendingString:@"    "];
    }
    NSLog(@"%@%@ %@", indent, v.class, NSStringFromRect(v.frame));
    if (v.subviews != null) {
        for (id s in v.subviews) {
            _dumpViews(s, level + 1);
        }
    }
}

FOUNDATION_EXPORT void dumpViews(NSView* v) {
    _dumpViews(v, 0);
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

@implementation NSOutlineView(SelectItem)

- (void)expandParentsOfItem: (id) i {
    while (i != nil) {
        id parent = [self parentForItem: i];
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


@implementation ZGUtils


@end
