#import "ZGItemProtocol.h"

@interface ZGTableViewDelegate : NSObject<NSTableViewDelegate>

- (id) initWithDocument: (ZGDocument*) doc;
- (void) sizeToContent: (NSTableView*) v;
- (void) tableView: (NSTableView*) v enterFolder: (NSInteger) row;
- (void) tableViewBecameFirstResponder: (NSTableView*) v;

+ (NSSize) minMaxVisibleColumnContentSize: (NSTableView*) v columnIndex: (int) cx;

@end
