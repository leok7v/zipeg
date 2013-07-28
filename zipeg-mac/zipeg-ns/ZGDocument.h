
@interface ZGDocument : NSDocument
@property NSOutlineView* outlineView;
@property NSTableView *tableView;

- (void) sizeOutlineViewToContents;
- (NSString*) askForPasswordFromBackgroundThread;
- (BOOL) progress:(long long)pos ofTotal:(long long)total;
- (BOOL) progressFile:(long long)fileno ofTotal:(long long)totalNumberOfFiles;

@end
