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

@interface ZGDestination()
- (void) writeUserDefaults;
@end

@interface ZGInplaceButtonCell : NSPopUpButtonCell {
    ZGDestination* __weak _notify;
}

@end

@implementation ZGInplaceButtonCell

+ (BOOL) prefersTrackingUntilMouseUp {
    return true;
}

- (id) initWithMenu: (NSMenu*) m notify: (ZGDestination*) n {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        _notify = n;
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
        // DO NOT call: [popup sizeToFit]; // it makes controls too wide!
        [_notify writeUserDefaults];
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
    NSPopUpButton* _selected;
    NSPopUpButton* _ask;
    NSButton* _to;
    NSPopUpButton* _reveal;
    NSPopUpButton* _disclosure;
    NSPathControl* _pathControl;
    NSImage* _nextToArchiveImage;
    NSImage* _selectDestinationImage;
    NSPathComponentCell* _nextToArchivePathComponentCell;
    NSPathComponentCell* _selectDestinationPathComponentCell;
    NSMenuItem* _nextToArchiveMenuItem;
    NSMenuItem* _selectDestinationMenuItem;
    NSURL* _nextToArchiveURL;
    NSURL* _selectDestinationURL;
    // TODO: all observers must be weak because system is holding reference till we call remove... right?
    id __weak _destinationObserver;
    id __weak _windowWillCloseObserver;
    int _notify_count; // prevents readingUserDefaults on own notification and writingUserDefaults right after reading
}

- (id) initWithFrame: (NSRect) r for: (ZGDocument*) doc {
    self = [super initWithFrame: r];
    if (self) {
        alloc_count(self);
        _document = doc;
        _nextToArchiveURL = [NSURL URLWithString: @"http://www.zipeg.com/faq#nextToArchive"];
        _selectDestinationURL = [NSURL URLWithString: @"http://www.zipeg.com/faq#selectDestination"];
        _selectDestinationImage = [NSImage imageNamed: NSImageNameRevealFreestandingTemplate];
        _selectDestinationImage.size = NSMakeSize(16, 16);
        _nextToArchiveImage = ZGApp.appIcon16x16;
        _nextToArchivePathComponentCell = createPathComponentCell(
            @"next to archive", _nextToArchiveURL, _nextToArchiveImage, _font);
        _selectDestinationPathComponentCell = createPathComponentCell(
            @"always choose", _selectDestinationURL, _selectDestinationImage, _font);
        _nextToArchiveMenuItem = [self menuItemForPathComponentCell: _nextToArchivePathComponentCell
                                                             action: @selector(nextToArchive:)];
        _selectDestinationMenuItem = [self menuItemForPathComponentCell: _selectDestinationPathComponentCell
                                                             action: @selector(selectDestination:)];
        self.autoresizingMask = NSViewWidthSizable | NSViewMinYMargin;
        self.autoresizesSubviews = true;
        _font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize - 1];
        _label = createLabel(4, @" ", _font, r); // trailing space is important
        _ask = createInplaceButton(@[@"ask to unpack ", @"always unpack "], _font, r, _label.frame, self);
        _selected = createInplaceButton(@[@"all ", @"selected ", @"all or selected "], _font, r, _ask.frame, self);
        _to = createButton(_selected.frame.origin.x + _selected.frame.size.width,
                           @" items to the folder:", _font, r, NSMomentaryPushInButton);
        _to.action = @selector(openDisclosure:);
        _to.target = self;
        _disclosure = createDirsButton(@" X ", _font, r, _to.frame);

        [_disclosure.menu addItem: NSMenuItem.separatorItem];
        [_disclosure.menu addItem: _nextToArchiveMenuItem];
        [_disclosure.menu addItem: _selectDestinationMenuItem];

        _pathControl = createPathControl(_font, r, _disclosure.frame);
        _reveal = createInplaceButton(@[ @" and show in Finder ", @"don't show in Finder " ],
                                  _font, r, _pathControl.frame, self);
        _pathControl.action = @selector(pathControlSingleClick:);
        _pathControl.target = self;
        _pathControl.delegate = self;
        _disclosure.target = self;
        _disclosure.action = @selector(disclosurePressed:);
        self.postsBoundsChangedNotifications = true;
        [_pathControl addObserver: self forKeyPath: @"URL" options: 0 context: null];
        self.subviews = @[_label, _selected, _ask, _to, _disclosure, _pathControl, _reveal];
        [self readUserDefaults];
        _destinationObserver = addObserver(@"com.zipeg.preferences.destination.update", null, ^(NSNotification* n){
            [self readUserDefaults];
        });
        _windowWillCloseObserver = addObserver(NSWindowWillCloseNotification, _window,
            ^(NSNotification* n) {
                _windowWillCloseObserver = removeObserver(_windowWillCloseObserver);
                _destinationObserver = removeObserver(_destinationObserver);
            });
        // no need to unbind - it's done automatically by NSAutoUnbinder
        [_selected bind: @"selectedIndex"
               toObject: NSUserDefaultsController.sharedUserDefaultsController
            withKeyPath: [NSString stringWithFormat: @"values.%@", @"com.zipeg.preferences.unpackSelection"]
                options: @{@"NSContinuouslyUpdatesValue": @true}];

    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    [_pathControl removeObserver: self forKeyPath: @"URL"];
    [_selected unbind: @"com.zipeg.preferences.unpackSelection"];

    _destinationObserver = removeObserver(_destinationObserver);
    _windowWillCloseObserver = removeObserver(_windowWillCloseObserver);
    _pathControl.target = null;
    _pathControl.action = null;
    _pathControl.delegate = null;
    _ask.target = null;
    _to.target = null;
    _pathControl = null;
    _label = null;
    _font = null;
    _ask = null;
    _to = null;
}

- (void) readUserDefaults {
    if (_notify_count == 0) {
        // trace(@"observed readUserDefaults %@", _document.window.title);
        _notify_count++;
        NSUserDefaults* ud = NSUserDefaults.standardUserDefaults;
        NSNumber* ask = [ud objectForKey: @"com.zipeg.preferences.ask.to.unpack"];
        [_ask selectItemWithTag: ask == null ? 0 : ask.intValue];
        NSNumber* selected = [ud objectForKey: @"com.zipeg.preferences.unpackSelection"];
        [_selected  selectItemWithTag: selected == null ? 0 : selected.intValue];
        NSNumber* reveal = [ud objectForKey: @"com.zipeg.preferences.destination.reveal"];
        [_reveal selectItemWithTag: reveal == null ? 0 : reveal.intValue];
        NSString* s = [ud objectForKey: @"com.zipeg.preferences.destination.url"];
        if (s != null) {
            NSURL* url = [NSURL URLWithString: s];
            if (isEqual(url, _nextToArchiveURL)) {
                [self nextToArchive: _disclosure];
            } else {
                BOOL d = false;
                if (![NSFileManager.defaultManager fileExistsAtPath: url.path isDirectory: &d] || !d) {
                    url = usersDocuments();
                }
                self.pathControlURL = url;
            }
        }
        _notify_count--;
    }
}

- (void) writeUserDefaults {
    if (_notify_count == 0) {
        NSUserDefaults* ud = NSUserDefaults.standardUserDefaults;
        [ud setObject: @(_ask.selectedItem.tag) forKey: @"com.zipeg.preferences.ask.to.unpack"];
        [ud setObject: @(_selected.selectedItem.tag) forKey: @"com.zipeg.preferences.unpackSelection"];
        [ud setObject: @(_reveal.selectedItem.tag) forKey: @"com.zipeg.preferences.destination.reveal"];
        [ud setObject: [_pathControl.URL absoluteString] forKey: @"com.zipeg.preferences.destination.url"];
        [ud synchronize];
        _notify_count++;
        // trace(@"post writeUserDefaults %@", _document.window.title);
        [NSNotificationCenter.defaultCenter postNotificationName: @"com.zipeg.preferences.destination.update" object: null];
        _notify_count--;
    } else {
        // we are already observing ours or somebody-elses changes - do nothing
    }
}

static NSPathComponentCell* createPathComponentCell(NSString* title, NSURL* url, NSImage* icon, NSFont* font) {
    NSPathComponentCell* c = [NSPathComponentCell new];
    c.image = icon;
    c.URL   = url;
    c.title = title;
    c.state = NSOffState;
    c.font  = font;
    return c;
}

- (NSMenuItem*) menuItemForPathComponentCell: (NSPathComponentCell*) cell action: (SEL) sel {
    NSMenuItem* mi = NSMenuItem.new;
    mi.action = sel;
    mi.target = self;
    mi.image = cell.image;
    mi.title = cell.title;
    return mi;
}


- (BOOL) isAsking {
    return _ask.selectedItem.tag == 0;
}

- (void) setAsking: (BOOL) b {
    [_ask selectItemWithTag: b ? 0 : 1];
}

- (BOOL) isSelected {
    return _selected.selectedItem.tag == 0;
}

- (BOOL) isReveal {
    return _reveal.selectedItem.tag == 0;
}

- (BOOL) isNextToArchive {
    return isEqual(_pathControl.URL, _nextToArchiveURL);
}

- (BOOL) isSelectDestination {
    return isEqual(_pathControl.URL, _selectDestinationURL);
}

- (NSURL*) URL {
    return _pathControl.URL;
}

- (void) progress: (int64_t) pos of: (int64_t) total {
    
}

- (void) openDisclosure: (id) sender {
    [_disclosure performClick: sender];
}

- (void) disclosurePressed: (id) sender {
    int tag = (int)_disclosure.selectedItem.tag;
    if (tag < 0) {
        self.pathControlURL = [NSURL fileURLWithPath: NSHomeDirectory() isDirectory: true];
    } else if (tag < countof(dirs)) {
        NSArray* path = NSSearchPathForDirectoriesInDomains(dirs[tag], NSAllDomainsMask, true);
        if (path.count > 0 && [path[0] isKindOfClass: NSString.class]) {
            self.pathControlURL = [NSURL fileURLWithPath: path[0] isDirectory: true];
        }
    } else {
        [self nextToArchive: _disclosure];
    }
}

- (void) setPathControlURL: (NSURL*) url {
    if (!isEqual(_pathControl.URL, url)) {
        _pathControl.URL = url;
        [self writeUserDefaults];
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
    NSString* title = systemPathTitle(_pathControl.URL.path);
    int w = [title sizeWithAttributes: a].width;
    NSRect r = _pathControl.frame;
    if (w > r.size.width) {
        r.size.width = w;
    }
    int maxWidth = self.frame.size.width;
    if (r.origin.x + r.size.width > maxWidth) {
        r.size.width = maxWidth - r.origin.x;
    }
    _pathControl.frame = r;
    _reveal.frameOrigin = NSMakePoint(r.origin.x + r.size.width, _reveal.frame.origin.y);
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
    NSTextField* label = [NSTextField.alloc initWithFrame: lr];
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

static NSButton* createButton(int x, NSString* text, NSFont* font, NSRect r, NSButtonType t) {
    NSRect rc = r;
    rc.origin.x = x;
    NSDictionary* a = @{NSFontAttributeName: font};
    rc.size = [text sizeWithAttributes: a];
    rc.origin.y = (r.size.height - rc.size.height) / 2;
    NSButton* btn = [NSButton.alloc initWithFrame: rc];
    btn.focusRingType = NSFocusRingTypeNone;
    btn.title = text;
    btn.buttonType = t; // NSMomentaryPushInButton | NSSwitchButton (that looks really ugly!)
    NSButtonCell* bc = btn.cell;
    bc.font = font;
    bc.bordered = false;
    bc.bezelStyle = NSShadowlessSquareBezelStyle;
    bc.highlightsBy = NSNoCellMask; // NSChangeGrayCellMask;
    bc.showsStateBy = NSNoCellMask;
    bc.controlTint = NSClearControlTint;
    bc.font = font;
    btn.frameSize = [btn.attributedStringValue size];
    [btn sizeToFit];
    return btn;
}

static NSURL* usersDocuments() {
    NSArray* path = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSAllDomainsMask, true);
    NSString* s = path != null && path.count > 0 && [path[0] isKindOfClass: NSString.class] ?
    (NSString*)path[0] : @"~/Documents";
    return [NSURL fileURLWithPath: s isDirectory: true];
}

static NSPathControl* createPathControl(NSFont* font, NSRect r, NSRect lr) {
    NSRect pr = r;
    pr.origin.x = lr.origin.x + lr.size.width;
    pr.size.width = r.size.width / 2 - pr.origin.x;
    pr.origin.y = lr.origin.y;
    pr.size.height = lr.size.height;
    NSPathControl* pc = [NSPathControl.alloc initWithFrame: pr];
    NSURL* u = usersDocuments();
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
    NSMenuItem *it = [NSMenuItem.alloc initWithTitle: NSLocalizedString(title, @"") action: null keyEquivalent:@""];
    it.tag = tag;
    it.image = image;
    [m insertItem: it atIndex: m.itemArray.count];
}

static NSPopUpButton* createInplaceButton(NSArray* texts, NSFont* font, NSRect r, NSRect lr, ZGDestination* that) {
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
    NSPopUpButton* btn = [NSPopUpButton.alloc initWithFrame: br pullsDown: true];
    btn.focusRingType = NSFocusRingTypeNone;
    btn.menu = m;
    ZGInplaceButtonCell* bc = [ZGInplaceButtonCell.alloc initWithMenu: m notify: that];
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

static NSString* systemPathTitle(NSString* path) {
    NSString* title = [NSFileManager.defaultManager displayNameAtPath: path];
    return title == null || title.length == 0 ? path.lastPathComponent : title;
}

static NSPopUpButton* createDirsButton(NSString* label, NSFont* font, NSRect r, NSRect lr) {
    NSRect br = r;
    br.origin.x = lr.origin.x + lr.size.width;
    br.origin.y = lr.origin.y - 4;
    br.size.width = [label sizeWithAttributes: @{ NSFontAttributeName: font }].width;
    br.size.height = lr.size.height + 8;
    NSPopUpButton* btn = [NSPopUpButton.alloc initWithFrame: br pullsDown: true];
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
    NSURL* u = [NSURL fileURLWithPath: NSHomeDirectory() isDirectory: true];
    NSImage* image = [NSWorkspace.sharedWorkspace iconForFile: u.path];
    image.size = NSMakeSize(16, 16);
    insertMenuItem(btn.menu, systemPathTitle(u.path), image, -1); // Home
    for (int i = 0; i < countof(dirs); i++) {
        NSArray* path = NSSearchPathForDirectoriesInDomains(dirs[i], NSAllDomainsMask, true);
        if (path.count > 0 && [path[0] isKindOfClass: NSString.class]) {
            u = [NSURL fileURLWithPath: path[0] isDirectory: true];
            NSString* p = [u path];
            NSImage *image = [NSWorkspace.sharedWorkspace iconForFile: p];
            image.size = NSMakeSize(16, 16);
            insertMenuItem(btn.menu, systemPathTitle(p), image, i);
        }
    }
    return btn;
}

- (void) drawRect: (NSRect) r {
    NSColor* t = [NSColor colorWithCalibratedRed: .92 green: .95 blue: .97 alpha: 1];
    NSColor* b = [NSColor colorWithCalibratedRed: .79 green: .82 blue: .87 alpha: 1];
    NSGradient *gradient = [NSGradient.alloc initWithStartingColor: b endingColor: t];
    [gradient drawInRect: r angle: 90];
}

/* NSPathControl deligates */

- (void) pathControlSingleClick: (id) sender {
    self.pathControlURL = _pathControl.clickedPathComponentCell.URL;
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
    op.canCreateDirectories = true;
    op.title = NSLocalizedString(@"Zipeg: Choose a folder to unpack to", @"");
    op.prompt = NSLocalizedString(@"Choose", @""); // this is localized by OS X to default locale (e.g. .ru)
}

- (void) revealInFinder: (id) sender {
    NSURL* url = _pathControl.clickedPathComponentCell.URL;
    url = url != null ? url : _pathControl.URL;
    if (url == _nextToArchiveURL) {
        url = _document.isNew ? null : _document.url;
        if (url != null) {
            url = [NSURL fileURLWithPath: url.path.stringByDeletingLastPathComponent];
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
    if (!self.isNextToArchive) {
        self.pathControlURL = _nextToArchiveURL;
        _pathControl.pathComponentCells = @[_nextToArchivePathComponentCell];
        [self pathControlSizeToFit];
    }
}

- (void) selectDestination: (id) sender {
    if (!self.isSelectDestination) {
        self.pathControlURL = _selectDestinationURL;
        _pathControl.pathComponentCells = @[_selectDestinationPathComponentCell];
        [self pathControlSizeToFit];
    }
}

- (void) pathControl: (NSPathControl*) pc willPopUpMenu: (NSMenu*) m {
    // -selectDestination: or -nextToArchive would add the URL to pathControl and
    // it will appear in the menu. We do not want to add it twice.
    BOOL foundNTA = false;
    BOOL foundSD = false;
    for (NSMenuItem* mi in m.itemArray) {
        foundNTA = isEqual(mi.title, _nextToArchiveMenuItem.title);
        foundSD = isEqual(mi.title, _selectDestinationMenuItem.title);
        if (foundNTA && foundSD) {
            break;
        }
    }
    if (!foundNTA && !foundSD) {
        [m addItem: NSMenuItem.separatorItem];
    }
    if (!foundNTA) { // make a copy because menu item cannot be inserted into 2 menus at once
        _nextToArchiveMenuItem.state = NSOffState;
        [m addItem: _nextToArchiveMenuItem.copy];
    }
    if (!foundSD) {
        _selectDestinationMenuItem.state = NSOffState;
        [m addItem: _selectDestinationMenuItem.copy];
    }
    if (!foundSD && !foundNTA) { // if "select destination" is choosen nothing to reveal in Finder
        NSString* title = NSLocalizedString(@"Show in Finder", @"");
        NSMenuItem* mi = [NSMenuItem.alloc initWithTitle:title action: @selector(revealInFinder:) keyEquivalent:@""];
        mi.target = self;
        [m addItem: NSMenuItem.separatorItem];
        [m addItem: mi];
    }
}

- (NSDragOperation) pathControl: (NSPathControl*) pc validateDrop: (id<NSDraggingInfo>) info {
    return NSDragOperationCopy;
}

- (BOOL) pathControl: (NSPathControl*) pathControl acceptDrop: (id <NSDraggingInfo>)info {
    BOOL result = false;
    NSURL *URL = [NSURL URLFromPasteboard: info.draggingPasteboard];
    if (URL != null) {
        self.pathControlURL = URL;
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
