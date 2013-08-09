#import "ZGDestination.h"
#import "ZGDocument.h"
#import "ZGApp.h"

#define kHorizontalGap 10

@interface ZGAskButtonCell : NSPopUpButtonCell

@end

@implementation ZGAskButtonCell

+ (BOOL) prefersTrackingUntilMouseUp {
    return true;
}

- (id) initWithMenu: (NSMenu*) m {
    self = [super init];
    if (self != null) {
        alloc_count(self);
    }
    self.menu = m;
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    self.action = null;
    self.target = null;
    self.menu = null;
}

- (void) drawWithFrame: (NSRect) r inView: (NSView*)  v {
    [[NSColor redColor] setFill];
    [super drawWithFrame: r inView: v];
}

- (BOOL) trackMouse: (NSEvent*) e inRect: (NSRect) r ofView: (NSView*) btn untilMouseUp: (BOOL) up {
    if ([btn isKindOfClass: NSPopUpButton.class]) {
        NSPopUpButton* popup = (NSPopUpButton*)btn;
        NSInteger tag = popup.selectedItem.tag;
        tag = (tag + 1) % popup.menu.itemArray.count;
        [popup selectItemWithTag: tag]; // flip in place w/o menu
        call1(self.target, self.action, self);
        return false;
    } else {
        return [super trackMouse: e inRect: r ofView: btn untilMouseUp: up];
    }
}

@end


@implementation ZGDestination {
    ZGDocument* __weak _document;
    NSPathControl* _pathControl;
    NSTextField* _label;
    NSPopUpButton* _ask;
    NSTextField* _to;
    NSButton* _disclosure;
}

- (id) initWithFrame: (NSRect) r for: (ZGDocument*) doc {
    self = [super initWithFrame: r];
    if (self) {
        alloc_count(self);
        _document = doc;
        self.autoresizingMask = NSViewWidthSizable | NSViewMinYMargin;
        self.autoresizesSubviews = true;
        NSFont* font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize - 1];
        _label = createLabel(4, @"Unpack ", font, r); // tailing space is important
        _ask = createPopUpButton(@[@"asking ", @"always "], font, r, _label.frame);
        _to = createLabel(_ask.frame.origin.x + _ask.frame.size.width, @" to folder: ", font, r);
        _disclosure = createButton(@"", font, r, _to.frame);
        _pathControl = createPathControl(font, r, _disclosure.frame);
        _pathControl.action = @selector(pathControlSingleClick:);
        _pathControl.target = self;
        self.subviews = @[_label, _ask, _pathControl, _disclosure, _to];
        _pathControl.delegate = self;
        _ask.target = self;
        _ask.action = @selector(askPressed:);
        _disclosure.target = self;
        _disclosure.action = @selector(disclosurePressed:);
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    _pathControl.target = null;
    _pathControl.action = null;
    _pathControl.delegate = null;
    _pathControl = null;
    _label = null;
    _ask = null;
    _to = null;
}

static NSTextField* createLabel(int x, NSString* text, NSFont* font, NSRect r) {
    NSRect lr = r;
    lr.origin.x = x;
    NSDictionary* a = @{NSFontAttributeName: font};
    lr.size = [text sizeWithAttributes: a];
    lr.origin.y = (r.size.height - lr.size.height) / 2;
    NSTextField* label = [[NSTextField alloc] initWithFrame: lr];
    label.focusRingType = NSFocusRingTypeNone;
    label.stringValue = text;
    NSTextFieldCell* tc = label.cell;
    tc.usesSingleLineMode = true;
    tc.font = font;
    tc.scrollable = true;
    tc.selectable = false;
    tc.editable = false;
    tc.bordered = false;
    tc.backgroundColor = [NSColor clearColor];
    lr.size = [label.attributedStringValue size];
    label.frame = lr;
    return label;
}
/*

 NSUserDirectory,                        // user home directories (Users)
 NSDocumentationDirectory,               // documentation (Documentation)
 NSDocumentDirectory,                    // documents (Documents)
 NSDesktopDirectory = 12,                // location of user's desktop
 NSDownloadsDirectory NS_ENUM_AVAILABLE(10_5, 2_0) = 15,              // location of the user's "Downloads" directory
 NSMoviesDirectory NS_ENUM_AVAILABLE(10_6, 4_0) = 17,                 // location of user's Movies directory (~/Movies)
 NSMusicDirectory NS_ENUM_AVAILABLE(10_6, 4_0) = 18,                  // location of user's Music directory (~/Music)
 NSPicturesDirectory NS_ENUM_AVAILABLE(10_6, 4_0) = 19,               // location of user's Pictures directory (~/Pictures)
 NSSharedPublicDirectory NS_ENUM_AVAILABLE(10_6, 4_0) = 21,           // location of user's Public sharing directory (~/Public)

 NSApplicationDirectory = 1,             // supported applications (Applications)
 NSAllApplicationsDirectory = 100,       // all directories where applications can occur
 NSAdminApplicationDirectory,            // system and network administration applications (Administration)

typedef NS_OPTIONS(NSUInteger, NSSearchPathDomainMask) {
    NSUserDomainMask = 1,       // user's home directory --- place to install user's personal items (~)
    NSLocalDomainMask = 2,      // local to the current machine --- place to install items available to everyone on this machine (/Library)
    NSNetworkDomainMask = 4,    // publically available location in the local area network --- place to install items available on the network (/Network)
    NSSystemDomainMask = 8,     // provided by Apple, unmodifiable (/System)
    NSAllDomainsMask = 0x0ffff  // all domains: all of the above and future items
};

FOUNDATION_EXPORT NSArray *NSSearchPathForDirectoriesInDomains(NSSearchPathDirectory directory, NSSearchPathDomainMask domainMask, BOOL expandTilde);
*/


static NSPathControl* createPathControl(NSFont* font, NSRect r, NSRect lr) {
    NSRect pr = r;
    pr.origin.x = lr.origin.x + lr.size.width;
    pr.size.width -= pr.origin.x;
    pr.origin.y = lr.origin.y;
    pr.size.height = lr.size.height;
    NSPathControl* _pathControl = [[NSPathControl alloc] initWithFrame: pr];
    NSArray* path = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true);
    NSString* s = path != null && path.count > 0 && [path[0] isKindOfClass: NSString.class] ?
                  (NSString*)path[0] : @"~/Documents";
    NSURL* u = [[NSURL alloc] initFileURLWithPath: s isDirectory: true];
    _pathControl.URL = u;
    _pathControl.pathStyle = NSPathStyleStandard;
    _pathControl.backgroundColor = [NSColor clearColor];
    NSPathCell* c = _pathControl.cell;
    // c.placeholderString = @"You can drag folders here";
    c.controlSize = NSSmallControlSize; // NSSmallControlSize
    c.font = font;
    _pathControl.autoresizingMask = NSViewWidthSizable | NSViewMinYMargin;
    _pathControl.doubleAction = @selector(pathControlDoubleClick:);
    _pathControl.focusRingType = NSFocusRingTypeNone; // because it looks ugly
    _pathControl.pathStyle = NSPathStylePopUp;
    return _pathControl;
}

static void insertMenuItem(NSMenu* m, NSString* title, int tag) {
    NSMenuItem *it = [[NSMenuItem alloc] initWithTitle: NSLocalizedString(title, @"") action: null keyEquivalent:@""];
    it.tag = tag;
    [m insertItem: it atIndex: m.itemArray.count];
}

static NSPopUpButton* createPopUpButton(NSArray* texts, NSFont* font, NSRect r, NSRect lr) {
    NSRect br = r;
    br.origin.x = lr.origin.x + lr.size.width - 8;
    br.origin.y = lr.origin.y;
    br.size.height = lr.size.height;
    NSMenu* m = [NSMenu new];
    NSDictionary* a = @{NSFontAttributeName: font};
    int tag = 0;
    int w = 20;
    for (NSString* s in texts) {
        insertMenuItem(m, s, tag++);
        NSSize sz = [s sizeWithAttributes: a];
        w = MAX(w, sz.width);
    }
    br.size.width = w + 20;
    NSPopUpButton* btn = [[NSPopUpButton alloc] initWithFrame: br pullsDown: true];
    btn.focusRingType = NSFocusRingTypeNone;
    btn.menu = m;
    ZGAskButtonCell* bc = [[ZGAskButtonCell alloc] initWithMenu: m];
    //NSPopUpButtonCell* bc = btn.cell;
    btn.cell = bc;
    bc.arrowPosition = NSPopUpArrowAtBottom;
    bc.preferredEdge = NSMaxYEdge;
    bc.bezelStyle = NSShadowlessSquareBezelStyle;
    bc.highlightsBy = NSChangeGrayCellMask;
    bc.pullsDown = false;
    bc.usesItemFromMenu = true;
    bc.font = font;
    bc.bordered = false;
    return btn;
}

static NSButton* createButton(NSString* label, NSFont* font, NSRect r, NSRect lr) {
    NSRect br = r;
    br.origin.x = lr.origin.x + lr.size.width + 4;
    br.origin.y = lr.origin.y - 4;
    br.size.width = [label sizeWithAttributes: @{ NSFontAttributeName: font }].width;
    br.size.height = lr.size.height + 8;
    NSButton* btn = [[NSPopUpButton alloc] initWithFrame: br pullsDown: true];
    btn.focusRingType = NSFocusRingTypeNone;
    btn.buttonType = NSToggleButton;
    NSButtonCell* bc = btn.cell;
    bc.bezelStyle = NSTexturedSquareBezelStyle; // NSRoundedDisclosureBezelStyle
    bc.highlightsBy = NSPushInCellMask;
    bc.controlTint = NSBlueControlTint;
    bc.font = font;
    bc.bordered = false;
    btn.enabled = true;
    return btn;
}


- (void) drawRect: (NSRect) r {
    NSColor* t = [NSColor colorWithCalibratedRed: .92 green: .95 blue: .97 alpha: 1];
    NSColor* b = [NSColor colorWithCalibratedRed: .79 green: .82 blue: .87 alpha: 1];
    NSGradient *gradient = [[NSGradient alloc] initWithStartingColor: b endingColor: t];
    [gradient drawInRect: r angle: 90];
}

- (void) askPressed: (id) sender {
    trace(@"%ld", _ask.selectedItem.tag);
}

- (void) disclosurePressed: (id) sender {
    trace(@"%ld", _ask.selectedItem.tag);
}

///////////////////////////////////////////

- (void) pathControlSingleClick: (id) sender {
    _pathControl.URL = _pathControl.clickedPathComponentCell.URL;
}


- (void) pathControlDoubleClick :(id)sender {
    if (_pathControl.clickedPathComponentCell != null) {
        [NSWorkspace.sharedWorkspace openURL: _pathControl.URL];
    }
}


- (void) pathControl: (NSPathControl*) pc willDisplayOpenPanel: (NSOpenPanel*) op {
    [ZGApp modalWindowToSheet: op for: _document.window];
    op.allowsMultipleSelection = false;
    op.canChooseDirectories = true;
    op.canChooseFiles = false;
    op.resolvesAliases = true;
    op.title = NSLocalizedString(@"Zipeg: Choose a folder to unpack to", @"");
    op.prompt = NSLocalizedString(@"Choose", @""); // this is localized by OS X to .ru
}

- (void)menuItemAction:(id)sender {
    NSURL* url = _pathControl.clickedPathComponentCell.URL;
    url = url != null ? url : _pathControl.URL;
    if (url != null) {
        [NSWorkspace.sharedWorkspace openURLs: @[url]
                      withAppBundleIdentifier: @"com.apple.Finder"
                                      options: NSWorkspaceLaunchDefault
               additionalEventParamDescriptor: null  launchIdentifiers: null];
    }
}

// Delegate method on NSPathControl (as NSPathStylePopUp) that determines what popup menu will look like.  In our case we add "Reveal in Finder".
- (void)pathControl: (NSPathControl*) pathControl willPopUpMenu: (NSMenu*) menu {
    if (false) { // self.useCustomPath)
        // Because we have a custom path, remove the "Choose..." and separator menu items.
        [menu removeItemAtIndex:0];
        [menu removeItemAtIndex:0];
    } else {
        // For file system paths, add the "Reveal in Finder" menu item.
        NSString *title = NSLocalizedString(@"Reveal in Finder", @"Used in dynamic popup menu");
        NSMenuItem *newItem = [[NSMenuItem alloc] initWithTitle:title action:@selector(menuItemAction:) keyEquivalent:@""];
        [newItem setTarget:self];
        [menu addItem:[NSMenuItem separatorItem]];
        [menu addItem:newItem];
    }
}

#pragma mark - Custom path support

/*
 // Shows how to create a custom generated path for NSPathControl.
- (IBAction)toggleUseCustomPath:(id)sender
{
    if (self.useCustomPath)
    {
        // User checked the "Custom Path" checkbox: create an array of custom cells and pass to the path control.
        NSArray * pathComponentArray = [self pathComponentArray];
        [self.pathControl setPathComponentCells:pathComponentArray];
    }
    else
    {
        // User unchecked the "Custom Path" checkbox: remove the custom path items (if any) by setting an empty array.
        NSArray *emptyArray = [[NSMutableArray alloc] init];
        [self.pathControl setPathComponentCells:emptyArray];
    }

    // Update the user interface.
    [self.pathSetButton setEnabled:!self.useCustomPath];
    [self updateExplainText]; // Update the explanation text to show the user how they can reveal the path component.
}
*/

// Assemble a set of custom cells to display into an array to pass to the path control.
- (NSArray *) pathComponentArray {
    NSMutableArray *pathComponentArray = [[NSMutableArray alloc] init];
    NSURL *URL;
    NSPathComponentCell *componentCell;
    // Use utility method to obtain a NSPathComponentCell based on icon, title and URL.
    URL = [NSURL URLWithString:@"http://www.apple.com"];
    componentCell = [self componentCellForType:kAppleLogoIcon withTitle:@"Apple" URL:URL];
    [pathComponentArray addObject:componentCell];
    URL = [NSURL URLWithString:@"http://www.apple.com/macosx/"];
    componentCell = [self componentCellForType:kInternetLocationNewsIcon withTitle:@"OS X" URL:URL];
    [pathComponentArray addObject:componentCell];
    URL = [NSURL URLWithString:@"http://developer.apple.com/macosx/"];
    componentCell = [self componentCellForType:kGenericURLIcon withTitle:@"Developer" URL:URL];
    [pathComponentArray addObject:componentCell];
    URL = [NSURL URLWithString:@"http://developer.apple.com/cocoa/"];
    componentCell = [self componentCellForType:kHelpIcon withTitle:@"Cocoa" URL:URL];
    [pathComponentArray addObject:componentCell];
    return pathComponentArray;
}

 // This method is used by pathComponentArray to create a NSPathComponent cell based on icon, title and URL information. Each path component needs an icon, URL and title.
- (NSPathComponentCell *)componentCellForType:(OSType)withIconType withTitle:(NSString *)title URL:(NSURL *)url {
    NSPathComponentCell *componentCell = [[NSPathComponentCell alloc] init];
    NSImage *iconImage = [[NSWorkspace sharedWorkspace] iconForFileType:NSFileTypeForHFSTypeCode(withIconType)];
    [componentCell setImage:iconImage];
    [componentCell setURL:url];
    [componentCell setTitle:title];
    return componentCell;
}

#pragma mark - Drag and drop

// This method is called when an item is dragged over the control. Return NSDragOperationNone to refuse the drop, or anything else to accept it.
- (NSDragOperation)pathControl:(NSPathControl *)pathControl validateDrop:(id <NSDraggingInfo>)info {
    return NSDragOperationCopy;
}

// Implement this method to accept the dropped contents previously accepted from validateDrop:.  Get the new URL from the pasteboard and set it to the path control.
-(BOOL)pathControl:(NSPathControl *)pathControl acceptDrop:(id <NSDraggingInfo>)info {
    BOOL result = false;
    NSURL *URL = [NSURL URLFromPasteboard:[info draggingPasteboard]];
    if (URL != null) {
        _pathControl.URL = URL;
        result = true;
    }
    return result;
}

 // This method is called when a drag is about to begin. It shows how to customize dragging by preventing "volumes" from being dragged.
- (BOOL) pathControl:(NSPathControl *)pathControl shouldDragPathComponentCell:(NSPathComponentCell *)pathComponentCell withPasteboard:(NSPasteboard *)pasteboard {
    BOOL result = YES;
    NSURL *URL = [pathComponentCell URL];
    if ([URL isFileURL]) {
        NSArray* pathPieces = [[URL path] pathComponents];
        if ([pathPieces count] < 4) {
            result = NO;	// Don't allow dragging volumes.
        }
    }
    return result;
}

@end
