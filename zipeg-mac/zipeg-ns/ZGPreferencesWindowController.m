#import "ZGPreferencesWindowController.h"

NSString* const kZGPreferencesWindowControllerDidChangeViewNotification =
               @"ZGPreferencesWindowControllerDidChangeViewNotification";

static NSString* const kZGPreferencesPosition = @"com.zipeg.preferences.frame.top.left";
static NSString* const kZGPreferencesSelected = @"com.zipeg.preferences.selected.view.ident";

static NSString* const PreferencesKeyForViewBounds (NSString* identifier) {
    return [NSString stringWithFormat: @"com.zipeg.preferences.%@.frame", identifier];
}

@interface ZGPreferencesWindowController ()  {
    NSArray* _viewControllers;
    NSMutableDictionary* _minimumViewRects;
    NSString* _title;
    NSViewController <ZGPreferencesViewControllerProtocol>* _selectedViewController;
}

- (NSViewController <ZGPreferencesViewControllerProtocol>*) viewControllerForIdentifier: (NSString*) identifier;

@property (readonly) NSArray* toolbarItemIds;
@property (nonatomic, retain) NSViewController <ZGPreferencesViewControllerProtocol>* selectedViewController;

@end

@implementation ZGPreferencesWindowController

@synthesize viewControllers = _viewControllers;
@synthesize selectedViewController = _selectedViewController;
@synthesize title = _title;

- (id) initWithViewControllers: (NSArray*) viewControllers {
    return [self initWithViewControllers: viewControllers title: null];
}

- (id) initWithViewControllers: (NSArray*) viewControllers title: (NSString*) title {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        _viewControllers = viewControllers;
        _minimumViewRects = [NSMutableDictionary dictionaryWithCapacity: 16];
        _title = title;
        self.window = NSWindow.new;
        self.window.delegate = self;
        self.window.showsResizeIndicator = true;
        self.window.styleMask = NSResizableWindowMask | NSTitledWindowMask | NSClosableWindowMask | NSUnifiedTitleAndToolbarWindowMask;
        self.window.hasShadow = true;
        self.window.showsToolbarButton = true;
        NSView* cv = NSView.new;
        cv.frameSize = NSMakeSize(360, 270);
        cv.autoresizesSubviews = true;
        self.window.contentView = cv;
        self.window.contentSize = cv.frame.size;
        NSToolbar* tb = [NSToolbar.alloc initWithIdentifier: @"com.zipeg.preferences.toolbar"];
        tb.allowsUserCustomization = false;
        tb.autosavesConfiguration = false;
        tb.displayMode = NSToolbarDisplayModeIconAndLabel;
        tb.sizeMode = NSToolbarSizeModeRegular;
        tb.delegate = self;
        self.window.toolbar = tb;
        if (self.title.length > 0) {
            self.window.title = self.title;
        }
        if (self.viewControllers.count > 0) {
            NSString* id = [NSUserDefaults.standardUserDefaults stringForKey: kZGPreferencesSelected];
            NSViewController<ZGPreferencesViewControllerProtocol>* vc = [self viewControllerForIdentifier: id];
            self.selectedViewController = vc != null ? vc : self.firstViewController;
        }
        NSString* origin = [NSUserDefaults.standardUserDefaults stringForKey: kZGPreferencesPosition];
        if (origin != null) {
            self.window.frameTopLeftPoint = NSPointFromString(origin);
        }
        // TODO: this will actually hold the ARC references forever unless we do observe Window close
        // not a big issue since the Preferences are never really closed...
        [NSNotificationCenter.defaultCenter addObserver: self
                                               selector: @selector(windowDidMove:)
                                                   name: NSWindowDidMoveNotification object: self.window];
        [NSNotificationCenter.defaultCenter addObserver: self
                                               selector: @selector(windowDidResize:)
                                                   name: NSWindowDidResizeNotification object: self.window];
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    [NSNotificationCenter.defaultCenter removeObserver: self];
    self.window.delegate = null;
    _viewControllers = null;
    _selectedViewController = null;
    _minimumViewRects = null;
}

- (NSViewController <ZGPreferencesViewControllerProtocol>*) firstViewController {
    for (id vc in self.viewControllers) {
        if ([vc isKindOfClass: NSViewController.class]) {
            return vc;
        }
    }
    return null;
}

- (BOOL) windowShouldClose: (id) sender {
    return !self.selectedViewController || [self.selectedViewController commitEditing];
}

- (void) windowDidMove: (NSNotification*) n {
    NSString* s = NSStringFromPoint(NSMakePoint(self.window.frame.origin.x, NSMaxY(self.window.frame)));
    [NSUserDefaults.standardUserDefaults setObject: s forKey: kZGPreferencesPosition];
}

- (void) windowDidResize: (NSNotification*) n {
    NSViewController <ZGPreferencesViewControllerProtocol>* viewController = self.selectedViewController;
    if (viewController != null) {
        NSString* s = NSStringFromRect(viewController.view.bounds);
        NSString* key = PreferencesKeyForViewBounds(viewController.ident);
        [NSUserDefaults.standardUserDefaults setObject: s forKey: key];
    }
}

- (NSArray*) toolbarItemIds {
    NSMutableArray* ids = [NSMutableArray arrayWithCapacity: _viewControllers.count];
    for (NSViewController<ZGPreferencesViewControllerProtocol>* viewController in _viewControllers) {
        if (viewController == null) {
            [ids addObject: NSToolbarFlexibleSpaceItemIdentifier];
        } else {
            [ids addObject: viewController.ident];
        }
    }
    return ids;
}

- (NSUInteger) indexOfSelectedController {
    return [self.toolbarItemIds indexOfObject: self.selectedViewController.ident];
}

- (NSArray*) toolbarAllowedItemIdentifiers: (NSToolbar*) toolbar {
    return self.toolbarItemIds;
}
                   
- (NSArray*) toolbarDefaultItemIdentifiers: (NSToolbar*) toolbar {
    return self.toolbarItemIds;
}

- (NSArray*) toolbarSelectableItemIdentifiers: (NSToolbar*) toolbar {
    return self.toolbarItemIds;
}

- (NSToolbarItem*) toolbar: (NSToolbar*) toolbar
     itemForItemIdentifier: (NSString*) ident
 willBeInsertedIntoToolbar: (BOOL) flag {
    NSToolbarItem* tbi = [NSToolbarItem.alloc initWithItemIdentifier: ident];
    NSArray* ids = self.toolbarItemIds;
    NSUInteger ix = [ids indexOfObject: ident];
    if (ix != NSNotFound) {
        id <ZGPreferencesViewControllerProtocol> c = _viewControllers[ix];
        tbi.image = c.image;
        tbi.label = c.label;
        tbi.target = self;
        tbi.action = @selector(toolbarItemDidClick:);
    }
    return tbi;
}

- (void) clearResponderChain {
    // Remove view controller from the responder chain
    NSResponder* next = self.window.nextResponder;
    if ([self.viewControllers indexOfObject: next] == NSNotFound) {
        return;
    }
    self.window.nextResponder = next.nextResponder;
    next.nextResponder = null;
}

- (void) patchResponderChain {
    [self clearResponderChain];
    NSViewController* c = self.selectedViewController;
    if (c != null) { // Add current controller to the responder chain
        NSResponder* nextResponder = self.window.nextResponder;
        self.window.nextResponder = c;
        c.nextResponder = nextResponder;
    }
}

- (NSViewController<ZGPreferencesViewControllerProtocol>*) viewControllerForIdentifier: (NSString*) ident {
    for (NSViewController<ZGPreferencesViewControllerProtocol>* vc in self.viewControllers) {
        if (isEqual(vc.ident, ident)) {
            return vc;
        }
    }
    return null;
}

- (void) setSelectedViewController: (NSViewController <ZGPreferencesViewControllerProtocol>*) c {
    if (_selectedViewController == c) {
        return;
    }
    if (_selectedViewController != null) {
        // Check if we can commit changes for old controller
        if (!_selectedViewController.commitEditing) {
            self.window.toolbar.selectedItemIdentifier = _selectedViewController.ident;
            return;
        }
        self.window.contentView = NSView.new;
        if ([_selectedViewController respondsToSelector: @selector(viewDidDisappear)]) {
            [_selectedViewController viewDidDisappear];
        }
        _selectedViewController = null;
    }
    if (c == null) {
        return;
    }
    // Retrieve the new window tile from the controller view
    if (self.title.length == 0) {
        NSString* label = c.label;
        self.window.title = label;
    }
    self.window.toolbar.selectedItemIdentifier = c.ident;
    // Record new selected controller in user defaults
    [NSUserDefaults.standardUserDefaults setObject: c.ident
                                            forKey: kZGPreferencesSelected];
    NSView* controllerView = c.view;
    // Retrieve current and minimum frame size for the view
    NSString* key = PreferencesKeyForViewBounds(c.ident);
    NSString* oldViewRectString = [NSUserDefaults.standardUserDefaults stringForKey: key];
    NSString* minViewRectString = [_minimumViewRects objectForKey: c.ident];
    if (minViewRectString == null) {
        [_minimumViewRects setObject: NSStringFromRect(controllerView.bounds)
                              forKey: c.ident];
    }
    BOOL sizableWidth  = controllerView.autoresizingMask & NSViewWidthSizable;
    BOOL sizableHeight = controllerView.autoresizingMask & NSViewHeightSizable;
    NSRect oldViewRect = oldViewRectString ? NSRectFromString(oldViewRectString) : controllerView.bounds;
    NSRect minViewRect = minViewRectString ? NSRectFromString(minViewRectString) : controllerView.bounds;
    oldViewRect.size.width  = oldViewRect.size.width  < minViewRect.size.width  || !sizableWidth  ?
                                                      minViewRect.size.width  : oldViewRect.size.width;
    oldViewRect.size.height = oldViewRect.size.height < minViewRect.size.height || !sizableHeight ?
                                                      minViewRect.size.height : oldViewRect.size.height;
    controllerView.frame = oldViewRect;
    // Calculate new window size and position
    NSRect oldFrame = [self.window frame];
    NSRect newFrame = [self.window frameRectForContentRect:oldViewRect];
    newFrame = NSOffsetRect(newFrame, oldFrame.origin.x, NSMaxY(oldFrame) - NSMaxY(newFrame));
    // Setup min/max sizes and show/hide resize indicator
    self.window.contentMinSize = minViewRect.size;
    self.window.contentMaxSize = NSMakeSize(sizableWidth  ? CGFLOAT_MAX : oldViewRect.size.width,
                                            sizableHeight ? CGFLOAT_MAX : oldViewRect.size.height);
    self.window.showsResizeIndicator = sizableWidth || sizableHeight;
    [self.window standardWindowButton: NSWindowZoomButton].enabled = sizableWidth || sizableHeight;
    [self.window setFrame: newFrame display: true animate: self.window.isVisible];
    _selectedViewController = c;
    if ([c respondsToSelector: @selector(viewWillAppear)]) {
        [c viewWillAppear];
    }
    self.window.contentView = controllerView;
    [self.window recalculateKeyViewLoop];
    if (self.window.firstResponder == self.window) {
        if ([c respondsToSelector: @selector(initialKeyView)]) {
            [self.window makeFirstResponder: c.initialKeyView];
        } else {
            [self.window selectKeyViewFollowingView: controllerView];
        }
    }
    // Insert view controller into responder chain
    [self patchResponderChain];
    [NSNotificationCenter.defaultCenter
       postNotificationName: kZGPreferencesWindowControllerDidChangeViewNotification
                     object: self];
}

- (void) toolbarItemDidClick: (id) sender {
    if ([sender respondsToSelector: @selector(itemIdentifier)]) {
        self.selectedViewController = [self viewControllerForIdentifier: [sender itemIdentifier]];
    }
}

- (void) selectControllerAtIndex: (NSUInteger) ix {
    if (NSLocationInRange(ix, NSMakeRange(0, _viewControllers.count)))
        self.selectedViewController = self.viewControllers[ix];
}

- (IBAction) goNextTab: (id) sender {
    NSUInteger ix = self.indexOfSelectedController;
    NSUInteger n = _viewControllers.count;
    do {
        ix = (ix + 1) % n;
    } while (_viewControllers[ix] == null);
    [self selectControllerAtIndex: ix];
}

- (IBAction) goPreviousTab: (id) sender {
    NSUInteger ix = self.indexOfSelectedController;
    NSUInteger n = _viewControllers.count;
    do {
        ix = (ix + n - 1) % n;
    } while (_viewControllers[ix] == null);
    [self selectControllerAtIndex: ix];
}

@end
