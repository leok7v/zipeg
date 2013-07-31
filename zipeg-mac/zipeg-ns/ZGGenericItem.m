#import "ZGGenericItem.h"

@implementation ZGGenericItem {
    NSObject<ZGItemProtocol>* __weak _parent;
    NSMutableArray* _children;
    NSMutableArray* _folderChildren;
    NSString* _name;
}
@synthesize name = _name;
@synthesize children = _children;
@synthesize folderChildren = _folderChildren;
@synthesize parent = _parent;

- (id) initWithChild: (NSObject<ZGItemProtocol>*) r {
    self = [super init];
    if (self != null) {
        _parent = null;
        _children = _folderChildren = [NSMutableArray arrayWithObject:r];
        _name = @"/";
    }
    return self;
}

- (id) initWithItem: (NSObject<ZGItemProtocol>*) i {
    self = [super init];
    if (self != null) {
        _parent = i.parent;
        _children = i.children;
        _folderChildren = i.folderChildren;
        _name = i.name;
    }
    return self;
}

- (BOOL) isGroup {
    return true;
}

+ (id) itemWithItem: (NSObject<ZGItemProtocol>*) i {
    return [[ZGGenericItem alloc] initWithItem: i];
}

@end
