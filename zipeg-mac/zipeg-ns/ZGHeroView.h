#import <Cocoa/Cocoa.h>

@class ZGDocument;

@interface ZGHeroView : NSView

- (id)initWithDocument: (ZGDocument*) doc andFrame:(NSRect)frame;

@end
