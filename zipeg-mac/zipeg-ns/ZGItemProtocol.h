@class ZGDocument;

@protocol ZGItemProtocol
@property (nonatomic, readonly) NSString* name;
@property (nonatomic, readonly) NSNumber* size;
@property (nonatomic, readonly) NSDate*   time;
@property (nonatomic, readonly) NSDictionary* properties;
@property (nonatomic, readonly) NSString* fullPath;
@property (nonatomic, readonly) NSMutableArray *children; // nil for leaf
@property (nonatomic, readonly) NSMutableArray *folderChildren; // nil for leaf
@property (weak) NSObject<ZGItemProtocol> *parent;
@property (nonatomic, readonly) BOOL isGroup;
@end

enum {              // answers for askOverwrite
    kYes = 0,       // ORDER IS VERY IMPORTANT. DO NOT CHANGE!  see:
    kYesToAll = 1,  // IFileExtractCallback.h NOverwriteAnswer::EEnum consts
    kNo = 2,        // and p7z.hpp
    kNoToAll = 3,
    kKeepBoth = 4, // just one file
    kCancel = 5,
    kKeepBothToAll = 6,
};

@protocol ZGArchiveCallbacks // ALL these methods are called on background thread and expected to block

- (BOOL) moveToTrash: (const char*) pathname;
- (BOOL) askCancel;
- (NSString*) askPassword;
- (int) askOverwrite: (const char*) from time: (int64_t) fromTime size: (int64_t) fromSize
                  to: (const char*) to   time: (int64_t) toTime   size: (int64_t) toSize;
- (BOOL) askToContinue: (NSString*) path error: (NSString*) message;
- (BOOL) progress: (int64_t) pos ofTotal: (int64_t) total; // true means "carry on"
- (BOOL) progressFiles: (int64_t) fileno ofTotal: (int64_t) totalNumberOfFiles; // true means "carry on"

@end


@protocol ZGItemFactory
@property (nonatomic, readonly) NSObject<ZGItemProtocol>* root;
@property (nonatomic, readonly) int numberOfItems;
@property (nonatomic, readonly) int numberOfFolders;
- (BOOL) readFromURL: (NSURL*) url ofType: (NSString*) type encoding:(NSStringEncoding) enc
            document: (NSObject<ZGArchiveCallbacks>*) doc
           operation: (ZGOperation*) op
               error: (NSError**) err
                done: (void(^)(NSObject<ZGItemFactory>* factory, NSError* error)) block;
// setFilter is called on background thread. block must be called back on the main thread
- (void) setFilter: (NSString*) filterText operation: (ZGOperation*) op done: (void(^)(BOOL)) block;
- (void) extract: (NSArray*) items to: (NSURL*) url operation: (ZGOperation*) op done: (void(^)(NSError* e)) block;
- (void) close;
@end
