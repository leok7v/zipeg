#import "ZGItemProtocol.h"
#import "ZGToolbar.h"

enum {              // answers for askOnBackgroundThreadOverwriteFrom
    kYes = 0,       // ORDER IS VERY IMPORTANT. DO NOT CHANGE!  see:
    kYesToAll = 1,  // IFileExtractCallback.h NOverwriteAnswer::EEnum consts
    kNo = 2,        // and p7z.hpp
    kNoToAll = 3,
    kKeepBoth = 4, // just one file
    kCancel = 5,
    kKeepBothToAll = 6,
};


@interface ZGDocument : NSDocument<NSToolbarDelegate> {

}
@property NSURL* url;
@property NSOutlineView* outlineView;
@property NSTableView*   tableView;
@property NSSplitView*   splitView;
@property ZGToolbar*     toolbar;
@property (nonatomic, readonly) BOOL isNew;

@property (nonatomic, readonly, weak) NSWindow* window;
@property (nonatomic, readonly) NSObject<ZGItemProtocol>* root;

- (void) requestCancel;
- (void) cancelAll; // cancel all operations (if any), otherwise nop
- (void) extract;
- (void) extract: (NSArray*) items to: (NSURL*) url DnD: (BOOL) dnd;
- (NSImage*) itemImage: (NSObject<ZGItemProtocol>*) i open: (BOOL) o;
- (int) viewStyle;
- (void) setViewStyle: (int) s;
- (void) search: (NSString*) s;
- (void) firstResponderChanged;
- (BOOL) documentCanClose;
- (void) sizeToContent;
- (NSInteger) runModalAlert: (NSString*) message defaultButton: (NSString*) db
            alternateButton: (NSString*) ab info: (NSString*) info;
- (void) alertModalSheet: (NSString*) message defaultButton: (NSString*) db
         alternateButton: (NSString*) ab info: (NSString*) info done: (void(^)(NSInteger rc)) d;
- (BOOL) askOnBackgroundThreadForCancel;
- (NSString*) askOnBackgroundThreadForPassword;
- (int) askOnBackgroundThreadOverwriteFrom: (const char*) fromName time: (int64_t) fromTime size: (int64_t) fromSize
                                        to: (const char*) toName time: (int64_t) toTime size: (int64_t) toSize;
- (BOOL) progressOnBackgroundThread: (int64_t) pos ofTotal: (int64_t) total;
- (BOOL) progressFileOnBackgroundThread: (int64_t) fileno ofTotal: (int64_t) totalNumberOfFiles;
- (void) windowDidBecomeKey;
- (void) windowDidResignKey;

@end
