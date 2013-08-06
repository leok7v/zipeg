#import "ZGFileSystem.h"

static NSMutableArray *leafNode;

static ZGFileSystemItem *g_root;

@implementation ZGFileSystemItem {
    NSMutableArray *_children;
    NSMutableArray *_folderChildren;
}

+ (void)initialize {
    assert(self == [ZGFileSystemItem class]);
    leafNode = [[NSMutableArray alloc] init];
}

- (id)initWithPath:(NSString *)path parent:(ZGFileSystemItem *)parentItem {
    self = [super init];
    if (self) {
        _name = [[path lastPathComponent] copy];
        _parent = parentItem;
    }
    return self;
}

- (void)dealloc {
//    trace(@"");
}

- (NSString*)fullPath {
    NSUInteger n = _name.length;
    NSObject<ZGItemProtocol>* p = _parent;
    while (p != null) {
        n += p.name.length + 1;
        p = p.parent;
    }
    NSMutableString *s = [NSMutableString stringWithCapacity:n];
    [s appendString:_name];
    p = _parent;
    while (p != null) {
        if (![p.name isEqualToString:@"/"]) {
            [s insertString:@"/" atIndex:0];
        }
        [s insertString:p.name atIndex:0];
        p = p.parent;
    }
    //trace("fullPath=%@", s);
    return s;
}

- (NSMutableArray *)children { // Creates, caches, and returns the array of children. Loads children incrementally
    if (_children == leafNode) {
        return null;
    } else if (_children == nil) {
        NSFileManager *fileManager = [NSFileManager defaultManager];
        NSString *fullPath = [self fullPath];
        BOOL isDir, valid;
        valid = [fileManager fileExistsAtPath:fullPath isDirectory:&isDir];
        if (valid && isDir) {
            trace(@"fullPath %@", fullPath);
            NSArray *array = [fileManager contentsOfDirectoryAtPath:fullPath error:nil];
            NSUInteger numChildren = [array count];
            _children = [[NSMutableArray alloc] initWithCapacity:numChildren];
            for (int i = 0; i < numChildren; i++) {
                NSObject<ZGItemProtocol> *newChild = [[ZGFileSystemItem alloc] initWithPath: array[i] parent:self];
                trace(@"  %@", array[i]);
                [_children addObject:(ZGFileSystemItem*)newChild];
            }
        } else {
            _children = leafNode;
        }
    }
    return _children;
}

- (NSMutableArray*) folderChildren {
    if (_children == leafNode) {
        return null;
    } else if (_folderChildren != nil) {
        return _folderChildren;
    } else {
        NSFileManager *fileManager = [NSFileManager defaultManager];
        _folderChildren = [NSMutableArray new];
        if (_children == null) {
            _children = self.children;
        }
        if (_children != leafNode) {
            for (NSObject<ZGItemProtocol>* child in _children) {
                NSString *fullPath = [self fullPath];
                BOOL isDir, valid;
                valid = [fileManager fileExistsAtPath:fullPath isDirectory:&isDir];
                if (valid && isDir) {
                    [_folderChildren addObject:(ZGFileSystemItem*)child];
                }
            }
        } else {
            _folderChildren = leafNode;
        }
        return _folderChildren;
    }
}

- (BOOL) isGroup {
    return self == g_root;
}

@end

@implementation ZGFileSystem {
}

+ (void)initialize {
    assert(self == [ZGFileSystem class]);
    g_root = [[ZGFileSystemItem alloc] initWithPath: @"/" parent: null];
}


- (id)init {
    self = [super init];
    if (self) {
        _root = g_root;
    }
    return self;
}

- (BOOL) readFromURL: (NSURL*) url ofType: (NSString*) type encoding:(NSStringEncoding) enc
            document: (ZGDocument*) doc
           operation: (NSOperation*) op error:(NSError**) err
                done: (void(^)(NSObject<ZGItemFactory>* factory, NSError* error)) done {
    trace(@"ZGFileSystem is using shared instance of root object");
    done(self, null);
    return true;
}

- (void) setFilter: (NSString*) filterText operation: (NSOperation*) op done: (void(^)(BOOL)) block {
    // not implemented
}

- (void) close {
    // not implemented
}

- (int) numberOfItems {
    return -1;
}

- (int) numberOfFolders {
    return 999999;
}

- (void) extract: (NSArray*) items to: (NSURL*) url operation: (NSOperation*) op done: (void(^)(NSError* e)) block {
    block(null);
}

@end
