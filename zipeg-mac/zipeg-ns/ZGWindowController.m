#import "ZGWindowController.h"

@interface ZGWindowController ()
@property (weak) IBOutlet NSView* contentView;
@property (weak) IBOutlet NSToolbar* toolbar;
@property (weak) IBOutlet NSSplitView* splitView;
@property (weak) IBOutlet NSLevelIndicator* levelIndicator;
@property (strong) IBOutlet NSMenu *tableRowContextMenu;
@property (weak) IBOutlet NSOutlineView* outlineView;
@property (weak) IBOutlet NSTableView *tableView;
- (IBAction) searchFieldAction: (id) sender;
@end

@implementation ZGWindowController

- (id)init {
    self = [super initWithWindowNibName: @"ZGDocument"];
    if (self) {
        
    }
    return self;
}

- (void) setWindow: (NSWindow*) w {
    [super setWindow: w];
}

- (void) loadWindow {
    [super loadWindow];
}

- (void)windowWillLoad {
}

- (void)windowDidLoad {
    [super windowDidLoad];
    [self.document windowControllerDidLoadNib: self];
}

- (IBAction) searchFieldAction: (id) sender {
/*
    if (!archive) {
        return;
    }
    NSString* s = [_searchField stringValue];
    // trace(@"%@", s);
    if (!_searchTextColor) {
        _searchTextColor = [_searchField textColor];
    }
    if ([archive setFilter:s]) {
        [_outlineView reloadData];
        [_outlineView expandItem:null expandChildren:true];
        [self sizeOutlineViewToContents];
        _searchField.textColor = _searchTextColor;
    } else {
        _searchField.textColor = NSColor.redColor;
    }
*/ 
}

@end
