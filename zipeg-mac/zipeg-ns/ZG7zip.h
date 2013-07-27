#import "ZGItemProtocol.h"

@interface ZG7zipItem : NSObject<ZGItemProtocol>

@property NSString *name;
@property (nonatomic, readonly) NSMutableArray *children;
@property (weak) NSObject<ZGItemProtocol> *parent;

@end


@interface ZG7zip : NSObject<ZGItemFactory>
@property (nonatomic, readonly) NSObject<ZGItemProtocol>* root;
@end
