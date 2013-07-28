#ifndef __ZGUtils_h__
#define __ZGUtils_h__

#ifdef __OBJC__

FOUNDATION_EXPORT uint64_t timestamp(const char* label);

FOUNDATION_EXPORT uint64_t alloc_count(id i);
FOUNDATION_EXPORT uint64_t dealloc_count(id i);

FOUNDATION_EXPORT void trace_allocs();

@interface ZGUtils : NSObject

@end

#endif

#endif
