@class ZGDocument;

@protocol ZGItemProtocol <NSObject>
@property NSString *name;
@property (nonatomic, readonly) NSMutableArray *children; // nil for leaf
@property (nonatomic, readonly) NSMutableArray *folderChildren; // nil for leaf
@property (weak) NSObject<ZGItemProtocol> *parent;
@end


@protocol ZGItemFactory <NSObject>
@property (nonatomic, readonly) NSObject<ZGItemProtocol>* root;
- (BOOL) readFromURL: (NSURL*) url ofType: (NSString*) type encoding:(NSStringEncoding) enc
            document: (ZGDocument*) doc
           operation: (NSOperation*) op error:(NSError**) err;
- (BOOL) setFilter:(NSString*)filterText;
- (void) close;
@end
