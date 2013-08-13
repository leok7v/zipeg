#import "ZGItemProtocol.h"

@interface ZGFileSystemItem : NSObject<ZGItemProtocol>

@end

@interface ZGFileSystem : NSObject<ZGItemFactory>
@property (nonatomic, readonly) NSObject<ZGItemProtocol>* root;
@end
