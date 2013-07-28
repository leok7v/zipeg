#import "ZGUtils.h"
#include "p7z.hpp"
#include "HashMapS2L.hpp"
#include "NanoTime.hpp"

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
                trace("%s %lld", k, v);
            }
        }
    }
}

@implementation ZGUtils


@end
