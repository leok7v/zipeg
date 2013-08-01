#import "ZGWindowController.h"
#import "ZGDocument.h"

@implementation ZGWindowController {
    NSPoint cascadePoint;
}

- (id)init {
    NSWindow* window = [NSWindow new];
    self = [super initWithWindow: window];
    if (self) {
        alloc_count(self);
        if (cascadePoint.x == 0 && cascadePoint.y == 0) {
            cascadePoint = NSMakePoint(40, 40);
        }
        window.restorable = true; // TODO: true for now but should be false
        // see notes and link in AppDelegate
        window.delegate = self;
        self.shouldCloseDocument = true;
        self.shouldCascadeWindows = true;
        window.backingType = NSBackingStoreBuffered;
        [window setOneShot: true];
        window.releasedWhenClosed = false; // this will crash close window with ARC if true
        window.styleMask = NSTitledWindowMask | NSClosableWindowMask |
                           NSMiniaturizableWindowMask | NSResizableWindowMask |
                           NSUnifiedTitleAndToolbarWindowMask |
                           NSTexturedBackgroundWindowMask;
        window.minSize = NSMakeSize(640, 480);
        window.hasShadow = true;
        window.showsToolbarButton = true;
        // window.frameAutosaveName = @""; // otherwise it will try to reopen all archives on startup
        if (window.frame.size.width < window.minSize.width || window.frame.size.height < window.minSize.height) {
            [window setFrame: NSMakeRect(0, 0, window.minSize.width, window.minSize.height) display: true animate: false];
        }
        [window addObserver:self forKeyPath:@"firstResponder" options: 0 context: null];

        [NSNotificationCenter.defaultCenter addObserver:self
                                               selector:@selector(windowDidBecomeKey:)
                                                   name:NSWindowDidBecomeKeyNotification
                                                 object:null];
        [NSNotificationCenter.defaultCenter addObserver:self
                                               selector:@selector(windowDidResignKey:)
                                                   name:NSWindowDidResignKeyNotification
                                                 object:null];
        cascadePoint = [window cascadeTopLeftFromPoint: cascadePoint]; // TODO: ??? may be it is in a wrong place. windowDidLoad is suggested place but it is not called for nib-less windows
    }
    return self;
}

- (void) dealloc {
    trace(@"%@", self);
    dealloc_count(self);
}

- (NSRect) window:(NSWindow *) window willPositionSheet: (NSWindow*) sheet usingRect: (NSRect) rect {
    trace(@"willPositionSheet %@ window.frame=%@" , NSStringFromRect(rect), NSStringFromRect(window.frame));
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


- (BOOL)windowShouldClose:(id)sender {
    assert([self.document isKindOfClass: ZGDocument.class]);
    ZGDocument* d = self.document;
    return [d documentCanClose];
}

- (void)windowWillClose:(NSNotification *)notification {
    [self.window removeObserver:self forKeyPath:@"firstResponder"];
    NSView* cv = self.window.contentView;
    cv.subviews = @[];
//  [self setDocument:null];
}


- (void)observeValueForKeyPath: (NSString*) keyPath ofObject: (id) o change: (NSDictionary*)change context: (void*) context {
/*
    NSKeyValueChange kind = ((NSNumber*)change[NSKeyValueChangeKindKey]).unsignedIntegerValue;
    NSString* action;
    switch (kind) {
        case NSKeyValueChangeSetting: action = @"set"; break;
        case NSKeyValueChangeInsertion: action = @"set"; break;
        case NSKeyValueChangeRemoval: action = @"set"; break;
        case NSKeyValueChangeReplacement: action = @"set"; break;
        default: action = [NSString stringWithFormat:@"%ld ???", kind]; break;
    }
    
    id newKey = change[NSKeyValueChangeNewKey];
    id oldKey = change[NSKeyValueChangeOldKey];
    NSIndexSet* indexes = change[NSKeyValueChangeIndexesKey]; // indexes of inserted/remove/replaced objects
    BOOL prio = change[NSKeyValueChangeNotificationIsPriorKey] != null;
    trace(@"keyPath=%@ action=%@ %@ newKey=%@ oldKey=%@ indexes=%@  prio=%d",
          keyPath, action, o, newKey, oldKey, indexes, prio);
*/
    if ([keyPath isEqualToString:@"firstResponder"]) {
        // NSResponder* fr = [self.window firstResponder];
        // trace(@"first responder changed to %@", fr);
        assert([self.document isKindOfClass: ZGDocument.class]);
        ZGDocument* d = self.document;
        [d firstResponderChanged];
        return;
    }
}

@end
