#import "ZGPreferencesWindowController.h"

NSString* const kZGPreferencesWindowControllerDidChangeViewNotification =
               @"ZGPreferencesWindowControllerDidChangeViewNotification";

static NSString* const kZGPreferencesFrameTopLeftKey = @"ZGPreferences.frame.top.left";
static NSString* const kZGPreferencesSelectedViewKey = @"ZGPreferences.selected.view.identifier";

static NSString* const PreferencesKeyForViewBounds (NSString* identifier) {
    return [NSString stringWithFormat: @"ZGPreferences %@ Frame", identifier];
}

@interface ZGPreferencesWindowController ()  {
    NSArray* _viewControllers;
    NSMutableDictionary* _minimumViewRects;
    NSString* _title;
    NSViewController <ZGPreferencesViewController>* _selectedViewController;
}

- (NSViewController <ZGPreferencesViewController>*) viewControllerForIdentifier: (NSString*) identifier;

@property (readonly) NSArray* toolbarItemIdentifiers;
@property (nonatomic, retain) NSViewController <ZGPreferencesViewController>* selectedViewController;

@end

@implementation ZGPreferencesWindowController

@synthesize viewControllers = _viewControllers;
@synthesize selectedViewController = _selectedViewController;
@synthesize title = _title;

- (id) initWithViewControllers: (NSArray*) viewControllers {
    return [self initWithViewControllers:viewControllers title: null];
}

- (id) initWithViewControllers: (NSArray*) viewControllers title: (NSString*) title {
    self = [super initWithWindowNibName: @"ZGPreferencesWindow"];
    if (self != null) {
        alloc_count(self);
        _viewControllers = viewControllers;
        _minimumViewRects = [NSMutableDictionary dictionaryWithCapacity: 16];
        _title = title;
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

- (void) windowDidLoad {
    if (self.title.length > 0) {
        self.window.title = self.title;
    }
    if (self.viewControllers.count > 0) {
        NSString* id = [NSUserDefaults.standardUserDefaults stringForKey: kZGPreferencesSelectedViewKey];
        NSViewController<ZGPreferencesViewController>* vc = [self viewControllerForIdentifier: id];
        self.selectedViewController = vc != null ? vc : self.firstViewController;
    }
    NSString* origin = [NSUserDefaults.standardUserDefaults stringForKey:kZGPreferencesFrameTopLeftKey];
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

- (NSViewController <ZGPreferencesViewController>*) firstViewController {
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
    [NSUserDefaults.standardUserDefaults
         setObject: s forKey: kZGPreferencesFrameTopLeftKey];
}

- (void)windowDidResize: (NSNotification*) n {
    NSViewController <ZGPreferencesViewController>* viewController = self.selectedViewController;
    if (viewController != null) {
        [NSUserDefaults.standardUserDefaults
            setObject: NSStringFromRect(viewController.view.bounds)
               forKey: PreferencesKeyForViewBounds(viewController.identifier)];
    }
}

- (NSArray*) toolbarItemIdentifiers {
    NSMutableArray* ids = [NSMutableArray arrayWithCapacity: _viewControllers.count];
    for (NSViewController<ZGPreferencesViewController>* viewController in _viewControllers) {
        if (viewController == null) {
            [ids addObject: NSToolbarFlexibleSpaceItemIdentifier];
        } else {
            [ids addObject: viewController.identifier];
        }
    }
    return ids;
}

- (NSUInteger) indexOfSelectedController {
    return [self.toolbarItemIdentifiers indexOfObject:self.selectedViewController.identifier];
}

- (NSArray*) toolbarAllowedItemIdentifiers: (NSToolbar*) toolbar {
    return self.toolbarItemIdentifiers;
}
                   
- (NSArray*) toolbarDefaultItemIdentifiers: (NSToolbar*) toolbar {
    return self.toolbarItemIdentifiers;
}

- (NSArray*) toolbarSelectableItemIdentifiers: (NSToolbar*) toolbar {
    NSArray* identifiers = self.toolbarItemIdentifiers;
    return identifiers;
}

- (NSToolbarItem*) toolbar: (NSToolbar*) toolbar
     itemForItemIdentifier: (NSString*) ident
 willBeInsertedIntoToolbar: (BOOL) flag {
    NSToolbarItem* tbi = [NSToolbarItem.alloc initWithItemIdentifier: ident];
    NSArray* identifiers = self.toolbarItemIdentifiers;
    NSUInteger ix = [identifiers indexOfObject: ident];
    if (ix != NSNotFound) {
        id <ZGPreferencesViewController> controller = _viewControllers[ix];
        tbi.image = controller.toolbarItemImage;
        tbi.label = controller.toolbarItemLabel;
        tbi.target = self;
        tbi.action = @selector(toolbarItemDidClick:);
    }
    return tbi;
}

- (void) clearResponderChain {
    // Remove view controller from the responder chain
    NSResponder* chainedController = self.window.nextResponder;
    if ([self.viewControllers indexOfObject: chainedController] == NSNotFound) {
        return;
    }
    self.window.nextResponder = chainedController.nextResponder;
    chainedController.nextResponder = null;
}

- (void) patchResponderChain {
    [self clearResponderChain];
    NSViewController* selectedController = self.selectedViewController;
    if (selectedController == null) {
        return;
    }
    // Add current controller to the responder chain
    NSResponder* nextResponder = self.window.nextResponder;
    self.window.nextResponder = selectedController;
    selectedController.nextResponder = nextResponder;
}

- (NSViewController<ZGPreferencesViewController>*) viewControllerForIdentifier: (NSString*) identifier {
    for (NSViewController<ZGPreferencesViewController>* vc in self.viewControllers) {
        if (isEqual(vc.identifier, identifier)) {
            return vc;
        }
    }
    return null;
}

- (void) setSelectedViewController: (NSViewController <ZGPreferencesViewController>*) controller {
    if (_selectedViewController == controller) {
        return;
    }
    if (_selectedViewController != null) {
        // Check if we can commit changes for old controller
        if (!_selectedViewController.commitEditing) {
            self.window.toolbar.selectedItemIdentifier = _selectedViewController.identifier;
            return;
        }
        self.window.contentView = NSView.new;
        if ([_selectedViewController respondsToSelector: @selector(viewDidDisappear)]) {
            [_selectedViewController viewDidDisappear];
        }
        _selectedViewController = null;
    }
    if (controller == null) {
        return;
    }
    // Retrieve the new window tile from the controller view
    if (self.title.length == 0) {
        NSString* label = controller.toolbarItemLabel;
        self.window.title = label;
    }
    self.window.toolbar.selectedItemIdentifier = controller.identifier;
    // Record new selected controller in user defaults
    [NSUserDefaults.standardUserDefaults setObject: controller.identifier
                                            forKey: kZGPreferencesSelectedViewKey];
    NSView* controllerView = controller.view;
    // Retrieve current and minimum frame size for the view
    NSString* key = PreferencesKeyForViewBounds(controller.identifier);
    NSString* oldViewRectString = [NSUserDefaults.standardUserDefaults stringForKey: key];
    NSString* minViewRectString = [_minimumViewRects objectForKey: controller.identifier];
    if (minViewRectString == null) {
        [_minimumViewRects setObject: NSStringFromRect(controllerView.bounds)
                              forKey: controller.identifier];
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
    _selectedViewController = controller;
    if ([controller respondsToSelector:@selector(viewWillAppear)]) {
        [controller viewWillAppear];
    }
    self.window.contentView = controllerView;
    [self.window recalculateKeyViewLoop];
    if (self.window.firstResponder == self.window) {
        if ([controller respondsToSelector:@selector(initialKeyView)]) {
            [self.window makeFirstResponder: controller.initialKeyView];
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
