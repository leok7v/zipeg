#import "ZGGenericItem.h"

@implementation ZGGenericItem {
    NSString* _name;
    NSString* _fullpath;
    NSMutableArray* _children;
    NSMutableArray* _folderChildren;
    NSObject<ZGItemProtocol>* __weak _parent;
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

+ (NSString*) fullPath: (NSObject<ZGItemProtocol>*) i {
    NSUInteger n = i.name.length;
    NSObject<ZGItemProtocol>* p = i.parent;
    while (p != null) {
        n += p.name.length + 1;
        p = p.parent;
    }
    NSMutableString *s = [NSMutableString stringWithCapacity: n];
    [s appendString: i.name];
    p = i.parent;
    while (p != null) {
        if (![p.name isEqualToString: @"/"]) {
            [s insertString: @"/" atIndex: 0];
        }
        [s insertString:p.name atIndex: 0];
        p = p.parent;
    }
    trace("fullPath=%@", s);
    return s;
}

- (NSString*) fullPath {
    if (_fullpath == null) {
        _fullpath = [ZGGenericItem fullPath: self];
    }
    return _fullpath;
}

- (NSDictionary*) properties {
    return @{};
}

- (NSNumber*) size {
    return null;
}

- (NSDate*) time {
    return null;
}


@end
