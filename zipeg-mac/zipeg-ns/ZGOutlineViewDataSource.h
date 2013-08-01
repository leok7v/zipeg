#import "ZGItemProtocol.h"

@interface ZGOutlineViewDataSource : NSObject<NSOutlineViewDataSource>

@property (readonly, weak, nonatomic) NSObject<ZGItemProtocol>* __weak root;

- (id)initWithDocument: (ZGDocument*) doc andRootItem: (NSObject<ZGItemProtocol>*) root;

@end
