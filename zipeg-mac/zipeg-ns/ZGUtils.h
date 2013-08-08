#ifndef __ZGUtils_h__
#define __ZGUtils_h__

#ifdef __OBJC__

FOUNDATION_EXPORT uint64_t timestamp(const char* label);
FOUNDATION_EXPORT uint64_t nanotime();

FOUNDATION_EXPORT uint64_t alloc_count(id i);
FOUNDATION_EXPORT uint64_t dealloc_count(id i);

FOUNDATION_EXPORT void trace_allocs();

FOUNDATION_EXPORT void dumpViews(NSView* v);
FOUNDATION_EXPORT void dumpAllViews();
FOUNDATION_EXPORT void subtreeDescription(NSView* v);

FOUNDATION_EXPORT id addObserver(NSString* name, id object, void(^block)(NSNotification*));
FOUNDATION_EXPORT id removeObserver(id observer); // always returns null - see usages

FOUNDATION_EXPORT BOOL responds(id o, SEL sel);
FOUNDATION_EXPORT id call(id o, SEL sel);
FOUNDATION_EXPORT id call1(id o, SEL sel, id p);
FOUNDATION_EXPORT id call2(id o, SEL sel, id p1, id p2);

enum {
    kSizableWH = NSViewWidthSizable | NSViewHeightSizable,
    kSizableLR = NSViewMinXMargin   | NSViewMaxXMargin,
    kSizableTB = NSViewMinYMargin   | NSViewMaxYMargin
};

FOUNDATION_EXPORT BOOL isEqual(NSObject* o1, NSObject* o2);

@interface NSString(ZGExtensions)
- (BOOL) equalsIgnoreCase: (NSString*) s;
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

@interface NSView(ZGExtensions)
- (NSView*) findViewByClassName: (NSString*) cn;
@end

@interface NSOutlineView(ZGExtensions)
- (void)expandParentsOfItem: (id) i;
- (void) selectItem: (id) i;
@end

@interface NSColor(ZGExtensions)
+ (NSColor*) sourceListBackgroundColor;
@end

@interface ZGBlock : NSObject
@property (readonly, nonatomic) BOOL isCanceled;
@property (readonly, nonatomic) BOOL isDone;
-(id) cancel; // always returns null - see usages
-(void) invokeNow;
-(BOOL) isExecuting; // only makes sense called from not main thread
@end

@interface ZGUtils : NSObject
// can be called from any thread, does dispatch_async to main thread
+ (ZGBlock*) invokeLater: (void(^)()) block;
@end

#endif

#endif
