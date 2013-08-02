@class ZGDocument;
@protocol ZGItemProtocol;

@interface ZGOutlineViewDelegate : NSObject<NSOutlineViewDelegate>

- (NSObject<ZGItemProtocol>*) selectedItem;
- (id) initWithDocument: (ZGDocument*) doc;
- (void) expandAll: (NSOutlineView*) outlineView;
- (void) sizeToContent:(NSOutlineView*) outlineView;
- (void) selectItem: (NSObject<ZGItemProtocol>*) it;

@end
