#import "ZGFileSystem.h"
#import "ZGGenericItem.h"

static NSMutableArray *leafNode;

static ZGFileSystemItem *g_root;

@implementation ZGFileSystemItem {
    NSString* _name;
    NSString* _fullpath;
    NSObject<ZGItemProtocol>* __weak _parent;
    NSMutableArray *_children;
    NSMutableArray *_folderChildren;
}
@synthesize name = _name;
@synthesize parent = _parent;

+ (void)initialize {
    assert(self == [ZGFileSystemItem class]);
    leafNode = [[NSMutableArray alloc] init];
}

- (id) initWithPath: (NSString*) path parent:(ZGFileSystemItem*) parentItem {
    self = [super init];
    if (self) {
        _name = path.lastPathComponent.copy;
        _parent = parentItem;
    }
    return self;
}

- (void) dealloc {
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

- (NSString*) fullPath {
    if (_fullpath == null) {
        _fullpath = [ZGGenericItem fullPath: self];
    }
    return _fullpath;
}

- (NSMutableArray*) children { // Creates, caches, and returns the array of children. Loads children incrementally
    if (_children == leafNode) {
        return null;
    } else if (_children == null) {
        NSFileManager* fm = NSFileManager.defaultManager;
        NSString* fp = self.fullPath;
        BOOL isDir, valid;
        valid = [fm fileExistsAtPath: fp isDirectory: &isDir];
        if (valid && isDir) {
            trace(@"fullPath %@", fp);
            NSArray* array = [fm contentsOfDirectoryAtPath: fp error: null];
            NSUInteger numChildren = array.count;
            _children = [[NSMutableArray alloc] initWithCapacity: numChildren];
            for (int i = 0; i < numChildren; i++) {
                NSObject<ZGItemProtocol> *newChild = [[ZGFileSystemItem alloc] initWithPath: array[i] parent: self];
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
    } else if (_folderChildren != null) {
        return _folderChildren;
    } else {
        NSFileManager* fm = NSFileManager.defaultManager;
        _folderChildren = [NSMutableArray new];
        if (_children == null) {
            _children = self.children;
        }
        if (_children != leafNode) {
            for (NSObject<ZGItemProtocol>* child in _children) {
                NSString* fp = self.fullPath;
                BOOL isDir, valid;
                valid = [fm fileExistsAtPath: fp isDirectory: &isDir];
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


- (id) init {
    self = [super init];
    if (self) {
        _root = g_root;
    }
    return self;
}

- (BOOL) readFromURL: (NSURL*) url ofType: (NSString*) type encoding:(NSStringEncoding) enc
            document: (NSObject<ZGArchiveCallbacks>*) doc
           operation: (ZGOperation*) op error:(NSError**) err
                done: (void(^)(NSObject<ZGItemFactory>* factory, NSError* error)) done {
    trace(@"ZGFileSystem is using shared instance of root object");
    done(self, null);
    return true;
}

- (void) setFilter: (NSString*) filterText operation: (ZGOperation*) op done: (void(^)(BOOL)) block {
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

- (void) extract: (NSArray*) items
              to: (NSURL*) url
       operation: (ZGOperation*) op
  fileDescriptor: (int) fd
            done: (void(^)(NSError* e)) block {
    block(null);
}

@end
