#import "ZGItemProtocol.h"
#import "ZGToolbar.h"

NSSortDescriptor* getSortDescriptor(int i);

@interface ZGDocument : NSDocument<NSToolbarDelegate, ZGArchiveCallbacks> {

}
@property NSURL* url;
@property NSView* __weak lastFirstResponder;
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
- (void) alertModalSheet: (NSString*) message
                 buttons: (NSArray*) buttons
                tooltips: (NSArray*) tips
                    info: (NSString*) info
              suppressed: (BOOL*) s
                    done: (void(^)(NSInteger rc)) d;
- (void) windowDidBecomeKey;
- (void) windowDidResignKey;

@end
