#import "ZGItemProtocol.h"

@interface ZGTableViewDelegate : NSObject<NSTableViewDelegate>

- (id) initWithDocument: (ZGDocument*) doc;
- (void) cancelDelayed;
- (void) sizeToContent: (NSTableView*) v;
- (void) tableView: (NSTableView*) v enterFolder: (NSInteger) row;
- (void) tableViewBecameFirstResponder: (NSTableView*) v;
- (void) outlineViewSelectionWillChange;
- (void) outlineViewSelectionDidChange;
+ (NSSize) minMaxVisibleColumnContentSize: (NSTableView*) v columnIndex: (int) cx;

@end
