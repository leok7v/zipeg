@class ZGDocument;
@protocol ZGItemProtocol;

@interface ZGOutlineViewDelegate : NSObject<NSOutlineViewDelegate>

- (id) initWithDocument: (ZGDocument*) doc;
- (void) expandOne: (NSOutlineView*) outlineView;
- (void) sizeOutlineViewToContents:(NSOutlineView*) outlineView;
- (void) selectItem: (NSObject<ZGItemProtocol>*) it;

@end
