#import "ZGItemProtocol.h"

@interface ZGGenericItem : NSObject<ZGItemProtocol>
+ (NSString*) fullPath: (NSObject<ZGItemProtocol>*) i;
- (id) initWithChild: (NSObject<ZGItemProtocol>*) r;
@end
