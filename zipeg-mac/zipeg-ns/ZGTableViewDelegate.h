#import "ZGDocument.h"


@interface NSOutlineView(SelectItem)

- (void)expandParentsOfItem: (NSObject<ZGItemProtocol>*) item;
- (void) selectItem: (id) item;

@end

@interface ZGTableViewDelegate : NSObject<NSTableViewDelegate>

- (id) initWithDocument: (ZGDocument*) doc;
- (void) sizeTableViewToContents: (NSTableView*) v;
- (void) tableView: (NSTableView*) v enterFolder: (NSInteger) row;
- (void) tableViewBecameFirstResponder: (NSTableView*) v;

+ (NSSize) minMaxVisibleColumnContentSize: (NSTableView*) v columnIndex: (int) cx;

@end
