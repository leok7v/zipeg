#import "ZGItemProtocol.h"

@interface ZGOutlineViewDataSource : NSObject<NSOutlineViewDataSource>

- (id)initWithDocument: (ZGDocument*) doc andRootItem:(NSObject<ZGItemProtocol>*)rootItem;

@end
