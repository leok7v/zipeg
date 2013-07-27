#import "ZGItemProtocol.h"

@class ZGDocument;

@interface ZGTableViewDataSource : NSObject<NSTableViewDataSource>

- (id) initWithDocument: (ZGDocument*) doc;
- (id) itemAtRow: (NSInteger) row;

@end
