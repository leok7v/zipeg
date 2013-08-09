@class ZGDocument;

@interface ZGDestination : NSView<NSPathControlDelegate>

- (id) initWithFrame: (NSRect) r for: (ZGDocument*) d;

@end
