#import "ZGItemProtocol.h"

@interface ZGFileSystemItem : NSObject<ZGItemProtocol>

@property NSString *name;
@property (nonatomic, readonly) NSMutableArray *children;
@property (weak) NSObject<ZGItemProtocol> *parent;

@end

@interface ZGFileSystem : NSObject<ZGItemFactory>
@property (nonatomic, readonly) NSObject<ZGItemProtocol>* root;
@end
