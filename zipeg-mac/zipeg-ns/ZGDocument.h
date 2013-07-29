
@interface ZGDocument : NSDocument<NSToolbarDelegate> {
    
}
@property NSOutlineView* outlineView;
@property NSTableView *tableView;
@property (nonatomic, readonly) NSWindow* window;

- (void) firstResponderChanged;
- (BOOL) documentCanClose;
- (void) sizeOutlineViewToContents;
- (NSString*) askForPasswordFromBackgroundThread;
- (BOOL) progress:(long long)pos ofTotal:(long long)total;
- (BOOL) progressFile:(long long)fileno ofTotal:(long long)totalNumberOfFiles;

@end
