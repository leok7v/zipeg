#import "ZGDestination.h"
#import "ZGDocument.h"
#import "ZGApp.h"

#define kHorizontalGap 10

static NSSearchPathDirectory dirs[] = {
    NSDocumentDirectory,
    NSDesktopDirectory,
    NSDownloadsDirectory,
    NSMoviesDirectory,
    NSMusicDirectory,
    NSPicturesDirectory,
    NSSharedPublicDirectory
};

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
    NSFont* _font;
    NSTextField* _label;
    NSPopUpButton* _ask;
    NSTextField* _to;
    NSPopUpButton* _disclosure;
    NSPathControl* _pathControl;
    NSMenuItem* _nextToArchiveMenuItem;
    NSURL* _nextToArchiveURL;

}

- (id) initWithFrame: (NSRect) r for: (ZGDocument*) doc {
    self = [super initWithFrame: r];
    if (self) {
        alloc_count(self);
        _document = doc;
        _nextToArchiveURL = [NSURL URLWithString: @"http://www.zipeg.com"];
        self.autoresizingMask = NSViewWidthSizable | NSViewMinYMargin;
        self.autoresizesSubviews = true;
        _font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize - 1];
        _label = createLabel(4, @"", _font, r); // tailing space is important
        _ask = createAskButton(@[@"Ask to ", @"Always "], _font, r, _label.frame);
        _to = createLabel(_ask.frame.origin.x + _ask.frame.size.width, @" unpack to folder:", _font, r);
        _disclosure = createDirsButton(@"M", _font, r, _to.frame);
        _pathControl = createPathControl(_font, r, _disclosure.frame);
        _pathControl.action = @selector(pathControlSingleClick:);
        _pathControl.target = self;
        self.subviews = @[_label, _ask, _pathControl, _disclosure, _to];
        _pathControl.delegate = self;
        _disclosure.target = self;
        _disclosure.action = @selector(disclosurePressed:);
        self.postsBoundsChangedNotifications = true;
        [_pathControl addObserver: self forKeyPath: @"URL" options: 0 context: null];
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    [_pathControl removeObserver: self forKeyPath: @"URL"];
    _pathControl.target = null;
    _pathControl.action = null;
    _pathControl.delegate = null;
    _pathControl = null;
    _label = null;
    _font = null;
    _ask = null;
    _to = null;
}

- (BOOL) isAsking {
    return _ask.selectedItem.tag == 0;
}

- (NSURL*) URL {
    return _pathControl.URL;
}

- (void) progress: (int64_t) pos of: (int64_t) total {
    
}

- (void) disclosurePressed: (id) sender {
    trace(@"%ld", _disclosure.selectedItem.tag);
    if (_disclosure.selectedItem.tag == -1) {
        _pathControl.URL = [[NSURL alloc] initFileURLWithPath: NSHomeDirectory() isDirectory: true];
    } else {
        NSArray* path = NSSearchPathForDirectoriesInDomains(dirs[_disclosure.selectedItem.tag], NSAllDomainsMask, true);
        if (path.count > 0 && [path[0] isKindOfClass: NSString.class]) {
            _pathControl.URL = [[NSURL alloc] initFileURLWithPath: path[0] isDirectory: true];
        }
    }
}

- (void)observeValueForKeyPath: (NSString*) keyPath ofObject: (id) o
                        change: (NSDictionary*) change context: (void*) context {
    if (isEqual(@"URL", keyPath)) {
        [self pathControlSizeToFit];
    }
}

- (void) pathControlSizeToFit {
    [_pathControl sizeToFit];
    NSDictionary* a = @{NSFontAttributeName: _font};
    NSString* label = [[_pathControl.URL path] lastPathComponent];
    int w = [label sizeWithAttributes: a].width;
    NSRect r = _pathControl.frame;
    if (w > r.size.width) {
        r.size.width = w;
    }
    if (r.origin.x + r.size.width > self.frame.size.width / 2) {
        r.size.width = self.frame.size.width / 2 - r.origin.x;
    }
    _pathControl.frame = r;

}

- (void)resizeSubviewsWithOldSize: (NSSize) was {
    [self pathControlSizeToFit];
    NSRect r = _pathControl.frame;
    [super resizeSubviewsWithOldSize: was];
    _pathControl.frame = r;
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
    [label sizeToFit];
    return label;
}

static NSPathControl* createPathControl(NSFont* font, NSRect r, NSRect lr) {
    NSRect pr = r;
    pr.origin.x = lr.origin.x + lr.size.width;
    pr.size.width = r.size.width / 2 - pr.origin.x;
    pr.origin.y = lr.origin.y;
    pr.size.height = lr.size.height;
    NSPathControl* pc = [[NSPathControl alloc] initWithFrame: pr];
    NSArray* path = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSAllDomainsMask, true);
    NSString* s = path != null && path.count > 0 && [path[0] isKindOfClass: NSString.class] ?
                  (NSString*)path[0] : @"~/Documents";
    NSURL* u = [[NSURL alloc] initFileURLWithPath: s isDirectory: true];
    pc.URL = u;
    pc.pathStyle = NSPathStyleStandard;
    pc.backgroundColor = [NSColor clearColor];
    NSPathCell* c = pc.cell;
    // c.placeholderString = @"You can drag folders here";
    c.controlSize = NSSmallControlSize;
    c.font = font;
    pc.autoresizingMask = NSViewWidthSizable | NSViewMinYMargin;
    pc.doubleAction = @selector(pathControlDoubleClick:);
    pc.focusRingType = NSFocusRingTypeNone; // because it looks ugly
    pc.pathStyle = NSPathStylePopUp;
    [pc sizeToFit];
    return pc;
}

static void insertMenuItem(NSMenu* m, NSString* title, NSImage* image, int tag) {
    NSMenuItem *it = [[NSMenuItem alloc] initWithTitle: NSLocalizedString(title, @"") action: null keyEquivalent:@""];
    it.tag = tag;
    it.image = image;
    [m insertItem: it atIndex: m.itemArray.count];
}

static NSPopUpButton* createAskButton(NSArray* texts, NSFont* font, NSRect r, NSRect lr) {
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

static NSPopUpButton* createDirsButton(NSString* label, NSFont* font, NSRect r, NSRect lr) {
    NSRect br = r;
    br.origin.x = lr.origin.x + lr.size.width;
    br.origin.y = lr.origin.y - 4;
    br.size.width = [label sizeWithAttributes: @{ NSFontAttributeName: font }].width;
    br.size.height = lr.size.height + 8;
    NSPopUpButton* btn = [[NSPopUpButton alloc] initWithFrame: br pullsDown: true];
    btn.focusRingType = NSFocusRingTypeNone;
    btn.buttonType = NSToggleButton;
    NSButtonCell* bc = btn.cell;
    bc.bezelStyle = NSTexturedSquareBezelStyle; // NSRoundedDisclosureBezelStyle
    bc.highlightsBy = NSPushInCellMask;
    bc.controlTint = NSBlueControlTint;
    bc.font = font;
    bc.bordered = false;
    btn.menu = [NSMenu new];
    insertMenuItem(btn.menu, @"", null, -2);
    NSURL* u = [[NSURL alloc] initFileURLWithPath: NSHomeDirectory() isDirectory: true];
    NSImage* image = [NSWorkspace.sharedWorkspace iconForFile: [u path]];
    image.size = NSMakeSize(16, 16);
    insertMenuItem(btn.menu, [[u path] lastPathComponent], image, -1); // Home
    for (int i = 0; i < countof(dirs); i++) {
        NSArray* path = NSSearchPathForDirectoriesInDomains(dirs[i], NSAllDomainsMask, true);
        if (path.count > 0 && [path[0] isKindOfClass: NSString.class]) {
            u = [[NSURL alloc] initFileURLWithPath: path[0] isDirectory: true];
            NSString* p = [u path];
            NSImage *image = [NSWorkspace.sharedWorkspace iconForFile: p];
            image.size = NSMakeSize(16, 16);
            insertMenuItem(btn.menu, [p lastPathComponent], image, i);
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

- (void) revealInFinder: (id) sender {
    NSURL* url = _pathControl.clickedPathComponentCell.URL;
    //    url = url != null ? url : _pathControl.URL;
    if (url == _nextToArchiveURL) {
        url = _document.isNew ? null : _document.url;
        if (url != null) {
            url = [[NSURL alloc] initFileURLWithPath: [[url path] stringByDeletingLastPathComponent]];
        }
    }
    if (url != null) {
        [NSWorkspace.sharedWorkspace openURLs: @[url]
                      withAppBundleIdentifier: @"com.apple.Finder"
                                      options: NSWorkspaceLaunchDefault
               additionalEventParamDescriptor: null  launchIdentifiers: null];
    }
}

- (void) nextToArchive: (id) sender {
    _pathControl.pathComponentCells = [self pathComponentArray];
    [self pathControlSizeToFit];
}

- (NSArray*) pathComponentArray {
    NSMutableArray* pathComponentArray = [NSMutableArray new];
    NSURL* u = [NSURL URLWithString: @"http://www.zipeg.com"];
    NSPathComponentCell* c = [self componentCellWithTitle: @"next to archive" url: u];
    [pathComponentArray addObject: c];
    return pathComponentArray;
}

- (NSPathComponentCell*) componentCellWithTitle: (NSString*) s url: (NSURL*) u {
    NSPathComponentCell* c = [NSPathComponentCell new];
    c.image = [NSWorkspace.sharedWorkspace iconForFile: NSBundle.mainBundle.bundlePath];
    c.URL   = u;
    c.title = s;
    c.state = NSOffState;
    c.font  = _font;
    return c;
}

- (void) pathControl: (NSPathControl*) pc willPopUpMenu: (NSMenu*) m {
    if (_nextToArchiveMenuItem == null) {
        NSString* title = NSLocalizedString(@"next to archive", @"");
        NSMenuItem* mi = [[NSMenuItem alloc] initWithTitle:title action: @selector(nextToArchive:) keyEquivalent:@""];
        mi.target = self;
        mi.image = [ZGApp appIcon16x16];
        _nextToArchiveMenuItem = mi;
    }
    BOOL found = false;
    // containsObject does not work on itemArray:
    for (NSMenuItem* mi in m.itemArray) {
        found = isEqual(mi.title, _nextToArchiveMenuItem.title);
        if (found) {
            break;
        }
    }
    if (!found) {
        [m addItem: NSMenuItem.separatorItem];
        [m addItem: _nextToArchiveMenuItem];
    }
    NSString* title = NSLocalizedString(@"Reveal in Finder", @"");
    NSMenuItem* mi = [[NSMenuItem alloc] initWithTitle:title action: @selector(revealInFinder:) keyEquivalent:@""];
    mi.target = self;
    [m addItem: NSMenuItem.separatorItem];
    [m addItem: mi];
    _nextToArchiveMenuItem.state = NSOffState;
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
