@class ZGDocument;

@protocol ZGItemProtocol
@property NSString *name;
@property (nonatomic, readonly) NSMutableArray *children; // nil for leaf
@property (nonatomic, readonly) NSMutableArray *folderChildren; // nil for leaf
@property (weak) NSObject<ZGItemProtocol> *parent;
@property (nonatomic, readonly) BOOL isGroup;
@end


@protocol ZGItemFactory
@property (nonatomic, readonly) NSObject<ZGItemProtocol>* root;
@property (nonatomic, readonly) int numberOfItems;
@property (nonatomic, readonly) int numberOfFolders;
- (BOOL) readFromURL: (NSURL*) url ofType: (NSString*) type encoding:(NSStringEncoding) enc
            document: (ZGDocument*) doc
           operation: (NSOperation*) op
               error: (NSError**) err
                done: (void(^)(NSObject<ZGItemFactory>* factory, NSError* error)) block;
// setFilter is called on background thread. block must be called back on the main thread
- (void) setFilter: (NSString*) filterText operation: (NSOperation*) op done: (void(^)(BOOL)) block;
- (void) extract: (NSArray*) items to: (NSURL*) url operation: (NSOperation*) op done: (void(^)(NSError* e)) block;
- (void) close;
@end
