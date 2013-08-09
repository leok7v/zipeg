#import "ZGItemProtocol.h"
#import "ZGToolbar.h"

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

- (void) extract: (NSArray*) items to: (NSURL*) url;
- (NSImage*) itemImage: (NSObject<ZGItemProtocol>*) i open: (BOOL) o;
- (void) setViewStyle: (int) s;
- (void) search: (NSString*) s;
- (void) firstResponderChanged;
- (BOOL) documentCanClose;
- (void) sizeToContent;
- (NSString*) askForPasswordFromBackgroundThread;
- (BOOL) progressOnBackgroundThread:(long long)pos ofTotal:(long long)total;
- (BOOL) progressFileOnBackgroundThread:(long long)fileno ofTotal:(long long)totalNumberOfFiles;
- (void) windowDidBecomeKey;
- (void) windowDidResignKey;

@end
