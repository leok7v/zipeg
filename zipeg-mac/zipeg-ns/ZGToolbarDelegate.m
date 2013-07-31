#import "ZGToolbarDelegate.h"
#import "ZGDocument.h"


@interface ZGToolbarDelegate() {
    NSSearchField* _searchFieldOutlet;
    NSToolbarItem* _activeSearchItem;
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
    NSString *s = ((NSTextField*)_activeSearchItem.view).stringValue;
    [self.document search: s];
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

static NSToolbarItem* createToolbarItem(NSString* id, NSString* label, NSString* tooltip) {
    NSToolbarItem* ti = [[NSToolbarItem alloc] initWithItemIdentifier: id];
    ti.label = label;
    ti.paletteLabel = label;
    ti.toolTip = tooltip;
    return ti;
}

static NSToolbarItem* createButton(NSString* id, NSString* label, NSString* tooltip, NSString* imageName, SEL sel) {
    NSToolbarItem* ti = createToolbarItem(id, label, tooltip);
    ti.image = [NSImage imageNamed: imageName];
    ti .action = sel;
    return ti;
}

static NSMenuItem* createSearchMenu(NSString* label, NSString* title, SEL selPanel, SEL sel) {
    NSMenu* submenu = [NSMenu new];
    NSMenuItem* submenuItem = [[NSMenuItem alloc] initWithTitle: title action: selPanel keyEquivalent: @""];
    NSMenuItem* menuFormRep = [NSMenuItem new];
    [submenu addItem: submenuItem];
    [menuFormRep setSubmenu: submenu];
    [menuFormRep setTitle: label];
    [menuFormRep setAction: sel];
    return menuFormRep;
}

static NSToolbarItem* createSearch(NSString* id, NSString* label, NSString* tooltip, NSMenuItem* mi, NSSearchField* sf) {
    NSToolbarItem* ti = createToolbarItem(id, label, tooltip);
    ti.view = sf;
    ti.minSize = NSMakeSize(30,  sf.frame.size.height);
    ti.maxSize = NSMakeSize(400, sf.frame.size.height);
    ti.menuFormRepresentation = mi;
    return ti;
}

static void addControl(NSSegmentedControl* sc, int ix, NSString* imageName, NSString* tooltip) {
    NSSegmentedCell* c = sc.cell;
    c.trackingMode = NSSegmentSwitchTrackingSelectOne;
    c.controlSize = NSRegularControlSize;
    NSImage* image = [NSImage imageNamed: imageName];
    assert(image != null);
    image.size = NSMakeSize(32, 32);
    [sc setWidth: image.size.width + 8 forSegment: ix];
    [sc setImageScaling:NSImageScaleProportionallyUpOrDown forSegment: ix];
    [sc setImage: image forSegment: ix];
    [c  setToolTip: tooltip forSegment: ix];
    [c  setLabel:@"" forSegment: ix]; // otherwise it will show up inline after image
    sc.segmentStyle = NSSegmentStyleTexturedRounded;
    
}

static NSToolbarItem* createSegmentedControl(NSString* id, NSString* label, NSString* tooltip,
                                             NSArray* imageNames,
                                             NSArray* imageLabels,
                                             SEL sel) {
    NSToolbarItem* ti = createToolbarItem(id, label, tooltip);
    // see: https://github.com/cocos2d/CocosBuilder/blob/master/CocosBuilder/ccBuilder/MainToolbarDelegate.m
    NSSegmentedControl* sc = [[NSSegmentedControl alloc] initWithFrame: NSMakeRect(0, 0, 80, 20)];
    sc.segmentCount = imageNames.count;
    sc.segmentStyle = NSSegmentStyleTexturedRounded;
    for (int i = 0; i < imageNames.count; i++) {
        addControl(sc, i, imageNames[i], imageLabels[i]);
    }
    sc.action = sel;
    sc.selectedSegment = 0;
    [sc sizeToFit]; // order is important
    ti.view = sc;
    return ti;
}

- (NSToolbarItem*) toolbar: (NSToolbar*) toolbar itemForItemIdentifier: (NSString*) itemIdent willBeInsertedIntoToolbar:(BOOL) willBeInserted {
    NSToolbarItem* ti = null;
    if ([itemIdent isEqual: SaveId]) {
        ti = createButton(SaveId, @"Save", @"Save the Document", @"save-64x64.png", @selector(saveDocument:));
        ti.target = self;
    } else if([itemIdent isEqual: SearchId]) {
        NSMenuItem* mi = createSearchMenu(@"Search", @"Search Panel",
                                          @selector(searchUsingSearchPanel:),
                                          @selector(searchMenuFormRepresentationClicked:));
        mi.target = self;
        for (NSMenuItem* m in mi.submenu.itemArray) {
            m.target = self;
        }
        _searchFieldOutlet = [[NSSearchField alloc] initWithFrame:[_searchFieldOutlet frame]];
        ti = createSearch(SearchId, @"Search", @"Search Your Document", mi, _searchFieldOutlet);
    } else if ([itemIdent isEqual: ViewsId]) {
        ti = createSegmentedControl(ViewsId, @"View Style", @"Show items in different views",
                                             @[@"folders-blue.png", @"folders-white.png"],
                                             @[@"Modern", @"Legacy"],
                                             @selector(viewStyleClicked:));
        NSSegmentedControl* sc = (NSSegmentedControl*)ti.view;
        sc.target = self;
    } else {
        assert(false);
	ti = null;
    }
    return ti;
}

- (void) saveDocument: (id) sender {
    trace(@"saveDocument %@", sender);
}

- (void) viewStyleClicked: (id) sender {
    NSSegmentedControl* sc = sender;
    int ss = (int)sc.selectedSegment;
    _document.viewStyle = ss;
    trace(@"selectedItem %@ %d", sender, ss);
}

- (NSArray*) toolbarDefaultItemIdentifiers: (NSToolbar*) toolbar {
    return @[SaveId, NSToolbarSeparatorItemIdentifier, ViewsId,
             NSToolbarSeparatorItemIdentifier,
             NSToolbarFlexibleSpaceItemIdentifier,
             NSToolbarSpaceItemIdentifier, SearchId];
}

- (NSArray*) toolbarAllowedItemIdentifiers: (NSToolbar*) toolbar {
    return @[SaveId, NSToolbarSeparatorItemIdentifier, ViewsId,
             NSToolbarSeparatorItemIdentifier,
             NSToolbarFlexibleSpaceItemIdentifier,
             NSToolbarSpaceItemIdentifier, SearchId];
}

- (void) toolbarWillAddItem: (NSNotification*) notif {
    NSToolbarItem *addedItem = [[notif userInfo] objectForKey: @"item"];
    if ([addedItem.itemIdentifier isEqual: SearchId]) {
	_activeSearchItem = addedItem;
	_activeSearchItem.target = self;
	_activeSearchItem.action = @selector(searchUsingToolbarSearchField:);
    }
}

- (void) toolbarDidRemoveItem: (NSNotification*) notif {
    NSToolbarItem *removedItem = [[notif userInfo] objectForKey: @"item"];
    if (removedItem ==_activeSearchItem) {
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
    [super validate];
    if ([[self view] isKindOfClass:[NSControl class]]) {
        NSControl *control = (NSControl*)[self view];
        id t = control.target;
        SEL a = control.action;
        if ([t respondsToSelector:a]) {
            BOOL e = true;
            if ([t respondsToSelector:@selector(validateToolbarItem:)]) {
                e = [t validateToolbarItem:self];
            }
            self.enabled = e;
            control.enabled = e;
        }
    }
}

@end
