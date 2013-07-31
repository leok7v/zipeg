#import "ZGItemProtocol.h"

@interface ZGGenericItem : NSObject<ZGItemProtocol>
- (id) initWithChild: (NSObject<ZGItemProtocol>*) r;
@end
