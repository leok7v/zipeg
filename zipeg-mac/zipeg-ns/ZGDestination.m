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
        _disclosure = createButton(@"W", font, r, _to.frame);
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

static NSPathControl* createPathControl(NSFont* font, NSRect r, NSRect lr) {
    NSRect pr = r;
    pr.origin.x = lr.origin.x + lr.size.width;
    pr.size.width -= pr.origin.x;
    pr.origin.y = lr.origin.y;
    pr.size.height = lr.size.height;
    NSPathControl* _pathControl = [[NSPathControl alloc] initWithFrame: pr];
    NSArray* path = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSAllDomainsMask, true);
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

static void insertMenuItem(NSMenu* m, NSString* title, NSImage* image, int tag) {
    NSMenuItem *it = [[NSMenuItem alloc] initWithTitle: NSLocalizedString(title, @"") action: null keyEquivalent:@""];
    it.tag = tag;
    it.image = image;
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
        insertMenuItem(m, s, null, tag++);
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
    btn.menu = [NSMenu new];
    NSSearchPathDirectory dirs[] = {
        NSDocumentDirectory,
        NSDesktopDirectory,
        NSDownloadsDirectory,
        NSMoviesDirectory,
        NSMusicDirectory,
        NSPicturesDirectory,
        NSSharedPublicDirectory
    };
    insertMenuItem(btn.menu, @"", null, -1);
    NSURL* u = [[NSURL alloc] initFileURLWithPath: NSHomeDirectory() isDirectory: true];
    NSImage* image = [NSWorkspace.sharedWorkspace iconForFile: [u path]];
    image.size = NSMakeSize(16, 16);
    insertMenuItem(btn.menu, [[u path] lastPathComponent], image, 0);
    for (int i = 0; i < countof(dirs); i++) {
        NSArray* path = NSSearchPathForDirectoriesInDomains(dirs[i], NSAllDomainsMask, true);
        if (path.count > 0 && [path[0] isKindOfClass: NSString.class]) {
            u = [[NSURL alloc] initFileURLWithPath: path[0] isDirectory: true];
            NSString* p = [u path];
            NSImage *image = [NSWorkspace.sharedWorkspace iconForFile: p];
            image.size = NSMakeSize(16, 16);
            insertMenuItem(btn.menu, [p lastPathComponent], image, i + 1);
        }
    }
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

/* NSPathControl deligates */

- (void) pathControlSingleClick: (id) sender {
    _pathControl.URL = _pathControl.clickedPathComponentCell.URL;
}

- (void) pathControlDoubleClick : (id)sender {
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

- (void)menuItemAction: (id) sender {
    NSURL* url = _pathControl.clickedPathComponentCell.URL;
    url = url != null ? url : _pathControl.URL;
    if (url != null) {
        [NSWorkspace.sharedWorkspace openURLs: @[url]
                      withAppBundleIdentifier: @"com.apple.Finder"
                                      options: NSWorkspaceLaunchDefault
               additionalEventParamDescriptor: null  launchIdentifiers: null];
    }
}

- (void) pathControl: (NSPathControl*) pathControl willPopUpMenu: (NSMenu*) menu {
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

/*
 TODO: magic - by the type of the documents in the archive choose most appropriate directory
 and add it as MenuItem.cell rendered item to the menu
 NSUserDirectory,
 NSDocumentDirectory,
 NSDesktopDirectory,
 NSDownloadsDirectory
 NSMoviesDirectory
 NSMusicDirectory
 NSPicturesDirectory
 NSSharedPublicDirectory

 NSApplicationDirectory
 NSAdminApplicationDirectory
 NSAllApplicationsDirectory // multiple
 */

- (NSArray*) pathComponentArray {
    NSMutableArray* pathComponentArray = [NSMutableArray new];
    NSPathComponentCell* componentCell;
    NSURL* u = [NSURL URLWithString: @"http://www.zipeg.com"];
    componentCell = [self componentCellForType: kAppleLogoIcon withTitle: @"Zipeg" url: u];
    [pathComponentArray addObject:componentCell];
    return pathComponentArray;
}

- (NSPathComponentCell*) componentCellForType: (OSType) withIconType
                                    withTitle: (NSString*) title
                                          url: (NSURL*) url {
    NSPathComponentCell* c = [NSPathComponentCell new];
    c.image =  [NSWorkspace.sharedWorkspace iconForFileType: NSFileTypeForHFSTypeCode(withIconType)];
    c.URL = url;
    c.title = title;
    return c;
}

- (NSDragOperation) pathControl: (NSPathControl*) pc validateDrop: (id<NSDraggingInfo>) info {
    return NSDragOperationCopy;
}

- (BOOL) pathControl: (NSPathControl*) pathControl acceptDrop: (id <NSDraggingInfo>)info {
    BOOL result = false;
    NSURL *URL = [NSURL URLFromPasteboard:[info draggingPasteboard]];
    if (URL != null) {
        _pathControl.URL = URL;
        result = true;
    }
    return result;
}

- (BOOL) pathControl: (NSPathControl*) pc shouldDragPathComponentCell: (NSPathComponentCell*) pcc
      withPasteboard: (NSPasteboard*)  pb {
    NSURL* u = pcc.URL;
    return u.isFileURL && u.path.pathComponents.count >= 4; // Don't allow dragging volumes.
}

@end
