#import "ZGItemProtocol.h"

@interface ZGOutlineViewDataSource : NSObject<NSOutlineViewDataSource>

@property (weak) NSObject<ZGItemProtocol>* root;

- (id)initWithDocument: (ZGDocument*) doc andRootItem: (NSObject<ZGItemProtocol>*) root;

@end
