#ifndef __ZGUtils_h__
#define __ZGUtils_h__

#ifdef __OBJC__

FOUNDATION_EXPORT uint64_t timestamp(const char* label);

FOUNDATION_EXPORT uint64_t alloc_count(id i);
FOUNDATION_EXPORT uint64_t dealloc_count(id i);

FOUNDATION_EXPORT void trace_allocs();

FOUNDATION_EXPORT void dumpViews(NSView* v);

@interface NSString(ZGExtensions)
- (int) indexOf: (NSString*) s;
- (int) indexOfIgnoreCase: (NSString*) s;
- (int) lastIndexOf: (NSString*) s;
- (int) lastIndexOfIgnoreCase: (NSString*) s;
- (int) endsWith: (NSString*) s;
- (int) endsWithIgnoreCase: (NSString*) s;
- (int) startsWith: (NSString*) s;
- (int) startsWithIgnoreCase: (NSString*) s;
- (BOOL) contains: (NSString*) s;
- (BOOL) containsIgnoreCase: (NSString*) s;
- (NSString*) substringFrom: (int) fromInclusive to: (int) toExclusive;
@end

@interface NSOutlineView(ZGExtensions)
- (void)expandParentsOfItem: (id) i;
- (void) selectItem: (id) i;
@end

@interface NSColor(ZGExtensions)
+ (NSColor*) sourceListBackgroundColor;
@end

@interface ZGUtils : NSObject

@end

#endif

#endif
