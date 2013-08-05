@class ZGDocument;
@protocol ZGItemProtocol;

@interface ZGOutlineViewDelegate : NSObject<NSOutlineViewDelegate>

- (NSObject<ZGItemProtocol>*) selectedItem;
- (id) initWithDocument: (ZGDocument*) doc;
- (void) cancelDelayed;
- (void) expandAll;
- (void) selectFirsFile;
- (void) sizeToContent:(NSOutlineView*) outlineView;
- (void) selectItem: (NSObject<ZGItemProtocol>*) it;

@end
