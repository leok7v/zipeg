#import "ZGToolbarDelegate.h"
#import "ZGDocument.h"


@interface ZGToolbarDelegate() {
    NSTextField*  _searchFieldOutlet;  // "Template" textfield needed to create our toolbar searchfield item.
    NSToolbarItem* _activeSearchItem;  // A reference to the search field in the toolbar, null if
}

@property (weak) ZGDocument* document;

@end

@interface ZGValidatedViewToolbarItem : NSToolbarItem
@end

@implementation ZGToolbarDelegate

static NSString* SaveId   = @"SaveId";
static NSString* SearchId = @"SearchId";
static NSString* ViewsId  = @"ViewsId";


+ (void) intialize {
    [ZGToolbarDelegate exposeBinding: @"enableTBI"];
}

- (id) initWithDocument: (ZGDocument*) doc {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        _document = doc;
    }
    return self;
}

- (void) dealloc {
    trace(@"%@", self);
    dealloc_count(self);
}


- (void) searchUsingToolbarSearchField:(id) sender {
    // This message is sent when the user strikes return in the search field in the toolbar
    NSString *s = ((NSTextField*)_activeSearchItem.view).stringValue;
    trace(@"search: %@", s);
}

- (void) searchMenuFormRepresentationClicked:(id) sender {
    [[_document.window toolbar] setDisplayMode: NSToolbarDisplayModeIconOnly];
    [_document.window makeFirstResponder:[_activeSearchItem view]];
}

- (void) searchUsingSearchPanel:(id) sender {
    NSAlert* a = [NSAlert alertWithMessageText: @""
                                     defaultButton: @"OK"
                                   alternateButton: @"Cancel"
                                       otherButton: null
                         informativeTextWithFormat: @"Search"];
    NSTextField* input = [[NSTextField alloc] initWithFrame:NSMakeRect(0, 0, 300, 24)];
    NSTextFieldCell* cell = input.cell;
    cell.usesSingleLineMode = true;
    input.stringValue = @"";
    a.accessoryView = input;
    [a beginSheetModalForWindow: _document.window modalDelegate: self
                 didEndSelector: @selector(didEndPresentedAlert:returnCode:contextInfo:)
                    contextInfo: null];
}

- (void) didEndPresentedAlert: (NSAlert*) a returnCode: (NSInteger)rc contextInfo: (void*) ctx {
    if (rc == NSAlertDefaultReturn) {
        NSTextField* input = (NSTextField*)a.accessoryView;
        [input validateEditing];
        trace(@"%@", input.stringValue);
    } else {
        trace(@"cancel");
    }
}

static void addControl(NSSegmentedControl* segmControl, int ix, NSString* imageName, NSString* tooltip) {
    NSSegmentedCell* segmCell = [segmControl cell];
    segmCell.trackingMode = NSSegmentSwitchTrackingMomentary;
    NSImage* image = [NSImage imageNamed: imageName];
    assert(image != null); 
    image.size = NSMakeSize(16, 16);
    [segmControl setWidth: image.size.width + 8 forSegment: ix];
    [segmControl setImage: image forSegment: ix];
    [segmCell    setToolTip: tooltip forSegment: ix];
    [segmCell    setLabel:@"" forSegment: ix]; // otherwise it will show up inline after image
    segmControl.segmentStyle = NSSegmentStyleTexturedRounded;
}

- (NSToolbarItem*) toolbar: (NSToolbar*) toolbar itemForItemIdentifier: (NSString*) itemIdent willBeInsertedIntoToolbar:(BOOL) willBeInserted {
    // Required delegate method:  Given an item identifier, this method returns an item
    // The toolbar will use this method to obtain toolbar items that can be displayed in the customization sheet, or in the toolbar itself
    NSToolbarItem *toolbarItem = null;
    
    if ([itemIdent isEqual: SaveId]) {
        toolbarItem = [[NSToolbarItem alloc] initWithItemIdentifier: itemIdent];
	
        // Set the text label to be displayed in the toolbar and customization palette
	[toolbarItem setLabel: @"Save"];
	[toolbarItem setPaletteLabel: @"Save"];
	
	// Set up a reasonable tooltip, and image   Note, these aren't localized, but you will likely want to localize many of the item's properties
	[toolbarItem setToolTip: @"Save Your Document"];
	[toolbarItem setImage: [NSImage imageNamed: @"save-64x64"]];
	
	// Tell the item what message to send when it is clicked
	[toolbarItem setTarget: self];
	[toolbarItem setAction: @selector(saveDocument:)];
    } else if([itemIdent isEqual: SearchId]) {
        // NSToolbarItem doens't normally autovalidate items that hold custom views, but we want this guy to be disabled when there is no text to search.
        toolbarItem = [[ZGValidatedViewToolbarItem alloc] initWithItemIdentifier: itemIdent];
        
	NSMenu *submenu = null;
	NSMenuItem *submenuItem = null, *menuFormRep = null;
	
	// Set up the standard properties
	[toolbarItem setLabel: @"Search"];
	[toolbarItem setPaletteLabel: @"Search"];
	[toolbarItem setToolTip: @"Search Your Document"];
	
        _searchFieldOutlet = [[NSSearchField alloc] initWithFrame:[_searchFieldOutlet frame]];
	// Use a custom view, a text field, for the search item
	[toolbarItem setView: _searchFieldOutlet];
	[toolbarItem setMinSize:NSMakeSize(30, NSHeight([_searchFieldOutlet frame]))];
	[toolbarItem setMaxSize:NSMakeSize(400,NSHeight([_searchFieldOutlet frame]))];
        
	// By default, in text only mode, a custom items label will be shown as disabled text, but you can provide a
	// custom menu of your own by using <item> setMenuFormRepresentation]
	submenu = [NSMenu new];
	submenuItem = [[NSMenuItem alloc] initWithTitle: @"Search Panel" action: @selector(searchUsingSearchPanel:) keyEquivalent: @""];
	menuFormRep = [[NSMenuItem alloc] init];
        
	[submenu addItem: submenuItem];
	[submenuItem setTarget: self];
	[menuFormRep setSubmenu: submenu];
	[menuFormRep setTitle: [toolbarItem label]];
        
        // Normally, a menuFormRep with a submenu should just act like a pull down.  However, in 10.4 and later, the menuFormRep can have its own target / action.  If it does, on click and hold (or if the user clicks and drags down), the submenu will appear.  However, on just a click, the menuFormRep will fire its own action.
        [menuFormRep setTarget: self];
        [menuFormRep setAction: @selector(searchMenuFormRepresentationClicked:)];
        
        // Please note, from a user experience perspective, you wouldn't set up your search field and menuFormRep like we do here.  This is simply an example which shows you all of the features you could use.
	[toolbarItem setMenuFormRepresentation: menuFormRep];
    } else if ([itemIdent isEqual: ViewsId]) {
        toolbarItem = [[ZGValidatedViewToolbarItem alloc] initWithItemIdentifier: itemIdent];
	// Set up the standard properties
	[toolbarItem setLabel: @"View Style"];
	[toolbarItem setPaletteLabel: @"View Style"];
	[toolbarItem setToolTip: @"Show items in different views"]; // TODO: better description
        // see: https://github.com/cocos2d/CocosBuilder/blob/master/CocosBuilder/ccBuilder/MainToolbarDelegate.m
        
        NSSegmentedControl* sc = [[NSSegmentedControl alloc] initWithFrame: NSMakeRect(0, 0, 80, 20)];
        sc.segmentCount = 2;
        sc.segmentStyle = NSSegmentStyleTexturedSquare;
        addControl(sc, 0, @"folders-blue.png", @"Modern");
        addControl(sc, 1, @"folders-white.png", @"Legacy");
        [sc sizeToFit];
        [sc setAction: @selector(viewStyleClicked:)];
        [sc setTarget: self];
        [sc setEnabled: true];
        
        [toolbarItem setView:sc];
    } else {
	// itemIdent refered to a toolbar item that is not provide or supported by us or cocoa
	// Returning null will inform the toolbar this kind of item is not supported
	toolbarItem = null;
    }
    return toolbarItem;
}

- (void) saveDocument: (id) sender {
    trace(@"saveDocument %@", sender);
}

- (void) viewStyleClicked: (id) sender {
    int clickedSegment = (int)[sender selectedSegment];
    int clickedSegmentTag = (int)[[sender cell] tagForSegment:clickedSegment];
    _document.viewStyle = clickedSegment;
    trace(@"selectedItem %@ %d %d", sender, clickedSegment, clickedSegmentTag);
}

- (NSArray*) toolbarDefaultItemIdentifiers: (NSToolbar*) toolbar {
    // Required delegate method:  Returns the ordered list of items to be shown in the toolbar by default
    // If during the toolbar's initialization, no overriding values are found in the user defaults, or if the
    // user chooses to revert to the default items this set will be used
    return @[SaveId,
             NSToolbarSeparatorItemIdentifier,
             ViewsId,
             NSToolbarSeparatorItemIdentifier,
             NSToolbarFlexibleSpaceItemIdentifier,
             NSToolbarSpaceItemIdentifier,
             SearchId];
}

- (NSArray*) toolbarAllowedItemIdentifiers: (NSToolbar*) toolbar {
    // Required delegate method:  Returns the list of all allowed items by identifier.  By default, the toolbar
    // does not assume any items are allowed, even the separator.  So, every allowed item must be explicitly listed
    // The set of allowed items is used to construct the customization palette
    return @[SearchId,
             SaveId,
             NSToolbarSeparatorItemIdentifier,
             ViewsId,
             NSToolbarCustomizeToolbarItemIdentifier,
             NSToolbarFlexibleSpaceItemIdentifier,
             NSToolbarSpaceItemIdentifier,
             NSToolbarSeparatorItemIdentifier];
}

- (void) toolbarWillAddItem: (NSNotification*) notif {
    // Optional delegate method:  Before an new item is added to the toolbar, this notification is posted.
    // This is the best place to notice a new item is going into the toolbar.  For instance, if you need to
    // cache a reference to the toolbar item or need to set up some initial state, this is the best place
    // to do it.  The notification object is the toolbar to which the item is being added.  The item being
    // added is found by referencing the @"item" key in the userInfo
    NSToolbarItem *addedItem = [[notif userInfo] objectForKey: @"item"];
    if([[addedItem itemIdentifier] isEqual: SearchId]) {
	_activeSearchItem = addedItem;
	[_activeSearchItem setTarget: self];
	[_activeSearchItem setAction: @selector(searchUsingToolbarSearchField:)];
    }
}

- (void) toolbarDidRemoveItem: (NSNotification*) notif {
    // Optional delegate method:  After an item is removed from a toolbar, this notification is sent.   This allows
    // the chance to tear down information related to the item that may have been cached.   The notification object
    // is the toolbar from which the item is being removed.  The item being added is found by referencing the @"item"
    // key in the userInfo
    NSToolbarItem *removedItem = [[notif userInfo] objectForKey: @"item"];
    if (removedItem==_activeSearchItem) {
	_activeSearchItem = null;
    }
}

- (BOOL) validateToolbarItem: (NSToolbarItem*) toolbarItem {
    // Optional method:  This message is sent to us since we are the target of some toolbar item actions
    // (for example:  of the save items action)
    BOOL enable = false;
    if ([[toolbarItem itemIdentifier] isEqual: SaveId]) {
	// We will return true (ie  the button is enabled) only when the document is dirty and needs saving
	enable = true; // _document.isDocumentEdited;
    } else if ([[toolbarItem itemIdentifier] isEqual: NSToolbarPrintItemIdentifier]) {
	enable = true;
    } else if ([[toolbarItem itemIdentifier] isEqual: SearchId]) {
	enable = _document.isEntireFileLoaded;
    } else if ([[toolbarItem itemIdentifier] isEqual: ViewsId]) {
	enable = _document.isEntireFileLoaded;
    }
    return enable;
}

- (BOOL) validateMenuItem: (NSMenuItem*) item {
    BOOL enabled = true;
    if (item.action == @selector(searchMenuFormRepresentationClicked:) ||
        item.action == @selector(searchUsingSearchPanel:)) {
        enabled = [self validateToolbarItem: _activeSearchItem];
    }
    return enabled;
}


@end

@implementation ZGValidatedViewToolbarItem

- (void)validate {
    [super validate]; // Let super take care of validating the menuFormRep, etc.
    if ([[self view] isKindOfClass:[NSControl class]]) {
        NSControl *control = (NSControl*)[self view];
        id target = [control target];
        SEL action = [control action];
        if ([target respondsToSelector:action]) {
            BOOL enable = true;
            if ([target respondsToSelector:@selector(validateToolbarItem:)]) {
                enable = [target validateToolbarItem:self];
            }
            self.enabled = enable;
            control.enabled = enable;
        }
    }
}

@end
