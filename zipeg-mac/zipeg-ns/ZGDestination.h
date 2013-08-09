@class ZGDocument;

@interface ZGDestination : NSView<NSPathControlDelegate>

- (id) initWithFrame: (NSRect) r for: (ZGDocument*) d;
- (BOOL) isAsking;
- (NSURL*) URL;
- (void) progress: (int64_t) pos of: (int64_t) total; // -1, -1 hide

@end
