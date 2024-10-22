#import "ZGToolbarDelegate.h"
#import "ZGDocument.h"


// TODO: view type control:
/*
 View Type Template Images
 Images used in segmented controls to switch the current view type. To access this image, pass the specified constant to the imageNamed: method.

 NSString *const NSImageNameIconViewTemplate;
 NSString *const NSImageNameListViewTemplate;
 NSString *const NSImageNameColumnViewTemplate;
 NSString *const NSImageNameFlowViewTemplate;
 
 APPKIT_EXTERN NSString *const NSImageNameRightFacingTriangleTemplate NS_AVAILABLE_MAC(10_5);
 APPKIT_EXTERN NSString *const NSImageNameLeftFacingTriangleTemplate NS_AVAILABLE_MAC(10_5);


 */

@interface ZGToolbarDelegate() {
    ZGDocument* __weak _document;
    NSSearchField* _searchFieldOutlet;
    NSToolbarItem* _activeSearchItem;
    __weak id _windowWillCloseObserver;
}
@end

@interface ZGValidatedViewToolbarItem : NSToolbarItem
@end

@interface ZGPanel : NSSavePanel  {
    bool _asked;
}
@end

@implementation ZGPanel

- (id) init {
    self = [super init];
    if (self) {
        _asked = false;
    }
    return self;
}

- (BOOL) isExpanded {
    if (_asked) {
        _asked = true;
        return false;
    } else {
        return [super isExpanded];
    }
}

- (IBAction) ok: (id) sender {
    trace(@"OK");
}

- (IBAction) cancel: (id) sender {
    trace(@"Cancel");
}

@end


@implementation ZGToolbarDelegate

static NSString* ExtractId   = @"ExtractId";
static NSString* SearchId = @"SearchId";
static NSString* ViewsId  = @"ViewsId";
static NSString* NavsId  = @"NavId";

- (id) initWithDocument: (ZGDocument*) doc {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        _document = doc;
        _windowWillCloseObserver = addObserver(NSWindowWillCloseNotification, _document.window,
            ^(NSNotification* n) {
                _document.toolbar.delegate = null;
                _windowWillCloseObserver = removeObserver(_windowWillCloseObserver);
            });
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (void) searchUsingToolbarSearchField:(id) sender {
    NSTextField* tf = (NSTextField*)_activeSearchItem.view;
    NSString *s = tf.stringValue;
    [tf validateEditing];
    [_document search: s];
}

- (void) searchMenuFormRepresentationClicked: (id) sender {
    _document.window.toolbar.displayMode = NSToolbarDisplayModeIconOnly;
    [_document.window makeFirstResponder: _activeSearchItem.view];
}

- (void) searchUsingSearchPanel: (id) sender {
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
    } else {
//      trace(@"cancel");
    }
}

static NSToolbarItem* createToolbarItem(NSString* id, NSString* label, NSString* tooltip) {
    ZGValidatedViewToolbarItem* ti = [[ZGValidatedViewToolbarItem alloc] initWithItemIdentifier: id];
    ti.label = NSLocalizedString(label, @"");
    ti.paletteLabel = NSLocalizedString(label, @"");
    ti.toolTip = NSLocalizedString(tooltip, @"");
    return ti;
}

static NSToolbarItem* createButton(NSString* id, NSString* label, NSString* tooltip, NSString* imageName, SEL sel) {
    NSToolbarItem* ti = createToolbarItem(id, label, tooltip);
    ti.image = [[NSImage imageNamed: imageName] copy];
    ti .action = sel;
    return ti;
}

static NSMenuItem* createSearchPanelMenu(NSString* label, NSString* title, SEL selPanel, SEL sel) {
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

static void addControl(NSSegmentedControl* sc, int ix, NSObject* imageObj, NSString* tooltip) {
    NSSegmentedCell* c = sc.cell;
    c.trackingMode = NSSegmentSwitchTrackingSelectOne;
    c.controlSize = NSRegularControlSize;
    NSImage* image = [imageObj isKindOfClass: NSImage.class] ?
                     (NSImage*)imageObj : [[NSImage imageNamed: (NSString*) imageObj] copy];
    assert(image != null);
    // image.size = NSMakeSize(32, 32);
    [sc setWidth: 24 forSegment: ix];
    [sc setImageScaling:NSImageScaleProportionallyUpOrDown forSegment: ix];
    [sc setImage: image forSegment: ix];
    [c  setToolTip: tooltip forSegment: ix];
    [c  setLabel:@"" forSegment: ix]; // otherwise it will show up inline after image
    sc.segmentStyle = NSSegmentStyleTexturedRounded;
    
}

static NSToolbarItem* createSegmentedControl(NSString* id, NSString* label, NSString* tooltip,
                                             NSArray* imageObjects,
                                             NSArray* imageLabels,
                                             SEL sel) {
    NSToolbarItem* ti = createToolbarItem(id, label, tooltip);
    // see: https://github.com/cocos2d/CocosBuilder/blob/master/CocosBuilder/ccBuilder/MainToolbarDelegate.m
    NSSegmentedControl* sc = [[NSSegmentedControl alloc] initWithFrame: NSMakeRect(0, 0, 80, 20)];
    sc.segmentCount = imageObjects.count;
    sc.segmentStyle = NSSegmentStyleTexturedRounded;
    for (int i = 0; i < imageObjects.count; i++) {
        addControl(sc, i, imageObjects[i], imageLabels[i]);
    }
    sc.action = sel;
    sc.selectedSegment = 0;
    [sc sizeToFit]; // order is important
    ti.view = sc;
    return ti;
}

static void insertMenuItem(NSMenu* m, NSString* title, int tag) {
    NSMenuItem *it = [[NSMenuItem alloc] initWithTitle: NSLocalizedString(title, @"") action: null keyEquivalent:@""];
    // tag this menu item so NSSearchField can use it and respond to it appropriately
    it.tag = tag;
    [m insertItem: it atIndex: m.itemArray.count];
}

static NSMenu* createSearchMenu() {
    NSMenu *m = [[NSMenu alloc] initWithTitle:NSLocalizedString(@"Search Menu", @"")];
    m.autoenablesItems = true;
    insertMenuItem(m, @"Recent Searches", NSSearchFieldRecentsTitleMenuItemTag);
    insertMenuItem(m, @"No recent searches", NSSearchFieldNoRecentsMenuItemTag);
    insertMenuItem(m, @"Recents", NSSearchFieldRecentsMenuItemTag);
    NSMenuItem *separatorItem = (NSMenuItem*)[NSMenuItem separatorItem];
    // tag this menu item so NSSearchField can use it, by hiding/show it appropriately:
    separatorItem.tag = NSSearchFieldRecentsTitleMenuItemTag;
    [m insertItem: separatorItem atIndex: m.itemArray.count];
    insertMenuItem(m, @"Clear", NSSearchFieldClearRecentsMenuItemTag);
    return m;
}

- (NSToolbarItem*) toolbar: (NSToolbar*) toolbar itemForItemIdentifier: (NSString*) itemIdent
 willBeInsertedIntoToolbar: (BOOL) willBeInserted {
    NSToolbarItem* ti = null;
    if ([itemIdent isEqual: ExtractId]) {
        ti = createButton(ExtractId, @"Unpack", @"Unpack Content of the Archive",
                          @"play-n.png", @selector(extract:));
        ti.target = self;
    } else if([itemIdent isEqual: SearchId]) {
        NSMenuItem* mi = createSearchPanelMenu(@"Search", @"Search Panel",
                                          @selector(searchUsingSearchPanel:),
                                          @selector(searchMenuFormRepresentationClicked:));
        mi.target = self;
        for (NSMenuItem* m in mi.submenu.itemArray) {
            m.target = self;
        }
        
        _searchFieldOutlet = [[NSSearchField alloc] initWithFrame:[_searchFieldOutlet frame]];
        ti = createSearch(SearchId, @"Search", @"Search Your Document", mi, _searchFieldOutlet);
        NSTextFieldCell* c = _searchFieldOutlet.cell;
        c.placeholderString = @"To search start typing part of a filename";
        _searchFieldOutlet.recentsAutosaveName = @"ZipegRecentSearches";
        NSSearchFieldCell* sc = _searchFieldOutlet.cell;
        sc.searchMenuTemplate = createSearchMenu();
        sc.sendsWholeSearchString = false;
        sc.maximumRecents = 16;
        sc.sendsSearchStringImmediately = true;
        _searchFieldOutlet.delegate = self;
    } else if ([itemIdent isEqual: ViewsId]) {
        ti = createSegmentedControl(ViewsId, @"View Style", @"Show items in different views",
                                             @[@"folders-blue.png", @"folders-white.png"],
                                             @[@"Modern", @"Legacy"],
                                             null);
        NSSegmentedControl* sc = (NSSegmentedControl*)ti.view;
        sc.target = self;
        [sc bind: @"selectedIndex" toObject: NSUserDefaultsController.sharedUserDefaultsController
                        withKeyPath: @"values.com.zipeg.preferences.outline.view.style"
                            options: @{@"NSContinuouslyUpdatesValue": @true}];
    } else if ([itemIdent isEqual: NavsId]) {
        // NSImage* back = [NSWorkspace.sharedWorkspace iconForFileType: NSFileTypeForHFSTypeCode(kBackwardArrowIcon)];
        // NSImage* next = [NSWorkspace.sharedWorkspace iconForFileType: NSFileTypeForHFSTypeCode(kForwardArrowIcon)];
        ti = createSegmentedControl(NavsId, @"Back", @"See folders you viewed previously",
                                    @[@"prev.png", @"next.png"],
                                    // @[back, next], // TODO: looks like Shit. Why?
                                    @[@"Previous", @"Next"],
                                    @selector(navigationClicked:));
        NSSegmentedControl* sc = (NSSegmentedControl*)ti.view;
        sc.target = self;
        sc.selectedSegment = -1;
    } else {
        assert(false);
	ti = null;
    }
    return ti;
}

- (void) navigationClicked: (id) sender {
    NSSegmentedControl* sc = sender;
    // int ss = (int)sc.selectedSegment;
    sc.selectedSegment = -1;
    // trace(@"Navigation: selectedItem %@ %d", sender, ss);
}

- (NSArray*) toolbarDefaultItemIdentifiers: (NSToolbar*) toolbar {
    return @[ExtractId, NSToolbarSeparatorItemIdentifier,
             NavsId, NSToolbarSeparatorItemIdentifier,
             NSToolbarFlexibleSpaceItemIdentifier,
             NSToolbarSpaceItemIdentifier, SearchId];
}

- (NSArray*) toolbarAllowedItemIdentifiers: (NSToolbar*) toolbar {
    return @[ExtractId, NSToolbarSeparatorItemIdentifier,
             NavsId, NSToolbarSeparatorItemIdentifier,
             ViewsId, NSToolbarSeparatorItemIdentifier,
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

- (BOOL) validateToolbarItem: (NSToolbarItem*) ti {
    // Optional method:  This message is sent to us since
    // we are the target of some toolbar item actions
    // (for example:  of the save items action)
    BOOL enable = false;
    if ([[ti itemIdentifier] isEqual: ExtractId]) {
	enable = !_document.isNew;
    } else if ([[ti itemIdentifier] isEqual: NSToolbarPrintItemIdentifier]) {
	enable = true;
    } else if ([[ti itemIdentifier] isEqual: SearchId]) {
	enable = _document.isEntireFileLoaded;
    } else if ([[ti itemIdentifier] isEqual: ViewsId]) {
	enable = _document.isEntireFileLoaded && !_document.outlineView.isHidden;
        NSSegmentedControl* sc = (NSSegmentedControl*)ti.view;
        if (!enable) {
            sc.selectedSegment = -1;
        } else {
            sc.selectedSegment = _document.viewStyle;
        }
    } else if ([[ti itemIdentifier] isEqual: ExtractId]) {
	enable = _document.root != null && _document.isEntireFileLoaded;
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

- (NSArray*) control: (NSControl*) control textView:(NSTextView*) textView completions: (NSArray*) words
 forPartialWordRange: (NSRange) charRange indexOfSelectedItem: (int*) index {
    NSMutableArray* keywords = _searchFieldOutlet.recentSearches.mutableCopy;
    // TODO: it might be cool to communicate with archive to get suggestions and add them on the fly.
    // may be yes, may be no - leave it for the future development.
    // [keywords addObjectsFromArray: @[@"Hello", @"World"]];
    NSUInteger count = [keywords count];
    NSString* partialString = [[textView string] substringWithRange:charRange];
    NSMutableArray* matches = [NSMutableArray array];
    // find any match in our keyword array against what was typed -
    for (int i = 0; i < count; i++) {
        NSString* string = [keywords objectAtIndex:i];
        if ([string rangeOfString:partialString
                          options:NSAnchoredSearch | NSCaseInsensitiveSearch
                            range:NSMakeRange(0, [string length])].location != NSNotFound) {
            [matches addObject: string];
        }
    }
    [matches sortUsingSelector:@selector(compare:)];
    return matches;
}

- (void) extract: (id) sender {
    if (_document.root != null) {
        [_document extract];
    }
}

- (BOOL) panel: (id) sender shouldEnableURL: (NSURL*) url {
    if (![url isFileReferenceURL]) {
        return false;
    }
    BOOL d = false;
    NSString* path = url.path;
    BOOL b = [NSFileManager.defaultManager fileExistsAtPath: path isDirectory: &d] && d;
    trace("shouldEnableURL: %@ %d %d", path, d, b);
    return b;
}

- (BOOL) panel: (id) sender validateURL: (NSURL*) url error: (NSError**) outError {
    if (![url isFileReferenceURL]) {
        return false;
    }
    BOOL d = false;
    NSString* path = url.path;
    BOOL b = [NSFileManager.defaultManager fileExistsAtPath: path isDirectory: &d] && d;
    trace("validateURL: %@ %d %d", path, d, b);
    return b;
}

- (void)panel:(id) sender didChangeToDirectoryURL:(NSURL*) url {
}

- (NSString*) panel: (id) sender userEnteredFilename: (NSString*) filename confirmed: (BOOL) ok {
    [NSApp endSheet: _document.window];
    return filename;
}

- (void) panel:(id)sender willExpand: (BOOL) expanding {
}

- (void) panelSelectionDidChange:(id)sender {
}

@end

@implementation ZGValidatedViewToolbarItem

- (void)validate {
    [super validate];
    if ([[self view] isKindOfClass:[NSControl class]]) {
        NSControl *control = (NSControl*)[self view];
        id t = control.target;
        SEL a = control.action;
        if ([t respondsToSelector: a]) {
            BOOL e = true;
            if ([t respondsToSelector: @selector(validateToolbarItem:)]) {
                e = [t validateToolbarItem:self];
            }
            self.enabled = e;
            control.enabled = e;
        }
    }
}

@end
