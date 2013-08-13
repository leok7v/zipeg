#import "ZGItemProtocol.h"

@interface ZG7zipItem : NSObject<ZGItemProtocol>

@end


@interface ZG7zip : NSObject<ZGItemFactory>
@property (nonatomic, readonly) NSObject<ZGItemProtocol>* root;
@end
