#import "ZGWindowController.h"
#import "ZGDocument.h"

static NSString* ZGWindowAutosaveName = @"ZGWindow";

@implementation ZGWindowController {
    __weak id _windowDidBecomeKeyObserver;
    __weak id _windowDidResignKey;
}

static NSPoint cascadePoint;

- (id)init {
    NSWindow* window = NSWindow.new;
    self = [super initWithWindow: window];
    if (self) {
        alloc_count(self);
        if (cascadePoint.x == 0 && cascadePoint.y == 0) {
            cascadePoint = NSMakePoint(40, 40);
        }
        window.restorable = true; // TODO: true for now but should be false when Welcome is implemented
        // see notes and link in AppDelegate
        window.delegate = self;
        self.shouldCloseDocument = true;
        self.shouldCascadeWindows = true; // this has no effect for overwriten NSWindowController
        window.backingType = NSBackingStoreBuffered;
        window.oneShot = true;
        window.releasedWhenClosed = false; // if set to true - it will crash ARC when closing window
        window.styleMask = NSTitledWindowMask | NSClosableWindowMask |
                           NSMiniaturizableWindowMask | NSResizableWindowMask |
                           NSUnifiedTitleAndToolbarWindowMask |
                           NSTexturedBackgroundWindowMask;
        window.minSize = NSMakeSize(kWindowMinWidth, kWindowMinHeight);
        window.hasShadow = true;
        window.showsToolbarButton = true;
        window.frameAutosaveName = ZGWindowAutosaveName; // otherwise it will try to reopen all archives on startup
        window.frameUsingName = ZGWindowAutosaveName;
        if (window.frame.size.width < window.minSize.width || window.frame.size.height < window.minSize.height) {
            [window setFrame: NSMakeRect(0, 0, window.minSize.width, window.minSize.height) display: true animate: false];
        }
        [window addObserver:self forKeyPath: @"firstResponder" options: 0 context: null];
        _windowDidBecomeKeyObserver = addObserver(NSWindowDidBecomeKeyNotification, self,
                                          ^(NSNotification* n) { [self windowDidBecomeKey: n]; });
        _windowDidResignKey = addObserver(NSWindowDidResignKeyNotification, self,
                                          ^(NSNotification* n) { [self windowDidResignKey: n]; });
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (NSRect) window: (NSWindow*) window willPositionSheet: (NSWindow*) sheet usingRect: (NSRect) rect {
    NSView* cv = (NSView*)window.contentView;
    NSView* cvs = (NSView*)cv.superview;
    NSView* tbv = [cvs findViewByClassName: @"NSToolbarView"];
    rect.origin.y = tbv.frame.origin.y;
    return rect;
}

- (void) windowDidBecomeKey: (NSNotification *) notification  {
    ZGDocument* d = self.document;
    return [d windowDidBecomeKey];
}

- (void) windowDidResignKey: (NSNotification *) notification  {
    ZGDocument* d = self.document;
    return [d windowDidResignKey];
}

- (void) showWindow: (id) sender {
    cascadePoint = [self.window cascadeTopLeftFromPoint: cascadePoint];
    [super showWindow: sender];
}

- (BOOL) windowShouldClose: (id) sender {
    assert([self.document isKindOfClass: ZGDocument.class]);
    ZGDocument* d = self.document;
    return [d documentCanClose];
}

- (void) windowWillClose: (NSNotification*) notification {
    [self.window removeObserver:self forKeyPath: @"firstResponder"];
    _windowDidBecomeKeyObserver = removeObserver(_windowDidBecomeKeyObserver);
    _windowDidResignKey = removeObserver(_windowDidResignKey);
}

- (void) observeValueForKeyPath: (NSString*) kp ofObject: (id) o change: (NSDictionary*) ch context: (void*) ctx {
/*
    NSKeyValueChange kind = ((NSNumber*)ch[NSKeyValueChangeKindKey]).unsignedIntegerValue;
    NSString* action;
    switch (kind) {
        case NSKeyValueChangeSetting: action = @"set"; break;
        case NSKeyValueChangeInsertion: action = @"set"; break;
        case NSKeyValueChangeRemoval: action = @"set"; break;
        case NSKeyValueChangeReplacement: action = @"set"; break;
        default: action = [NSString stringWithFormat:@"%ld ???", kind]; break;
    }
    
    id newKey = ch[NSKeyValueChangeNewKey];
    id oldKey = ch[NSKeyValueChangeOldKey];
    NSIndexSet* indexes = ch[NSKeyValueChangeIndexesKey]; // indexes of inserted/remove/replaced objects
    BOOL prio = ch[NSKeyValueChangeNotificationIsPriorKey] != null;
    trace(@"keyPath=%@ action=%@ %@ newKey=%@ oldKey=%@ indexes=%@  prio=%d", kp, action, o, newKey, oldKey, indexes, prio);
*/
    if ([kp isEqualToString: @"firstResponder"]) {
        assert([self.document isKindOfClass: ZGDocument.class]);
        ZGDocument* d = self.document;
        [d firstResponderChanged];
        return;
    }
}

@end
