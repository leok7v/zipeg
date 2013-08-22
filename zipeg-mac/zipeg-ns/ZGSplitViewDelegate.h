#import "ZGDocument.h"

@interface ZGSplitViewDelegate : NSObject<NSSplitViewDelegate>

- (id)initWithDocument:(ZGDocument*) document;
- (void) setMinimumSize: (CGFloat) minSize atIndex: (NSInteger) viewIndex;
- (void) setWeight: (CGFloat) weight atIndex: (NSInteger) viewIndex;
- (void) layout: (NSSplitView*) sv;

@end
