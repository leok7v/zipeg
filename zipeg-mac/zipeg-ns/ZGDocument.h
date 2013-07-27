
@interface ZGDocument : NSDocument

@property (weak) IBOutlet NSOutlineView* outlineView;
@property (weak) IBOutlet NSTableView *tableView;

- (void) sizeOutlineViewToContents;
- (NSString*) askForPasswordFromBackgroundThread;
- (BOOL) progress:(long long)pos ofTotal:(long long)total;
- (BOOL) progressFile:(long long)fileno ofTotal:(long long)totalNumberOfFiles;


@end
