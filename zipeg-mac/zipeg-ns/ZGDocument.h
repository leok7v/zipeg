#import "ZGItemProtocol.h"
#import "ZGToolbar.h"

enum {              // answers for askOnBackgroundThreadOverwriteFrom
    kYes = 0,       // ORDER IS VERY IMPORTANT. DO NOT CHANGE!  see:
    kYesToAll = 1,  // IFileExtractCallback.h NOverwriteAnswer::EEnum consts
    kNo = 2,        // and p7z.hpp
    kNoToAll = 3,
    kAutoRename = 4, // just one file
    kCancel = 5,
    kAutoRenameAll = 6
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

- (void) extract;
- (void) extract: (NSArray*) items to: (NSURL*) url;
- (NSImage*) itemImage: (NSObject<ZGItemProtocol>*) i open: (BOOL) o;
- (void) setViewStyle: (int) s;
- (void) search: (NSString*) s;
- (void) firstResponderChanged;
- (BOOL) documentCanClose;
- (void) sizeToContent;
- (NSString*) askOnBackgroundThreadForPassword;
- (int) askOnBackgroundThreadOverwriteFrom: (const char*) fromName time: (int64_t) fromTime size: (int64_t) fromSize
                                        to: (const char*) toName time: (int64_t) toTime size: (int64_t) toSize;
- (BOOL) progressOnBackgroundThread:(long long)pos ofTotal:(long long)total;
- (BOOL) progressFileOnBackgroundThread:(long long)fileno ofTotal:(long long)totalNumberOfFiles;
- (void) windowDidBecomeKey;
- (void) windowDidResignKey;

@end
