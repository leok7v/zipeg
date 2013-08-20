#import "ZGAlerts.h"
#import "ZGDocument.h"
#import "ZGApp.h"

#define FPS 0.25 // frames per second for Alert icons animation

@class ZGProgress;

@interface ZGAnimatedImage : NSObject {
@public ZGBlock* _next;
    NSArray* _sprites;
    double _fps;
    int64_t _start; // milliseconds;
}
// NSProgressIndicator does the same job but I want this thing light and inside ZGProgress view
- (NSImage*) currentSprite;
@end

@interface ZGAlerts() {
    @public NSAlert* _alert; // strong
    void(^_block)(NSInteger rc); // strong
    ZGBlock* _delayedDismiss;
    NSView* __weak _contentView;
    ZGDocument* __weak _document;
    ZGProgress* _progress;
    int _count;
    NSSize _initialContentViewSize;
    ZGAnimatedImage* _boxes;
    dispatch_source_t _timer;
}
- (void) requestCancel;
@end

@implementation ZGAnimatedImage

- (id) initWith: (NSString*) prefix frames: (int) n size: (NSSize) size fps: (double) fps {
    self = super.init;
    if (self != null) {
        alloc_count(self);
        _start = nanotime() / 1000000;
        _fps = fps;
        NSMutableArray* s = [NSMutableArray arrayWithCapacity: n];
        for (int i = 0; i < n; i++) {
            NSString* name = [NSString stringWithFormat: @"%@%d.png", prefix, i];
            NSImage* image = [[NSImage imageNamed: name] copy];
            assert(image != null);
            image.size = size;
            s[i] = image;
        }
        _sprites = s;
    }
    return self;
}

- (void) dealloc {
    _next = _next.cancel;
    dealloc_count(self);
}

- (NSImage*) currentSprite {
    int64_t now = nanotime() / 1000000;
    int64_t delta = now - _start;
    int ix = (int)(delta * _fps / 1000) % (int)_sprites.count; // milliseconds
    return _sprites[ix];
}

- (void) drawIntoView: (NSView*) v point: (NSPoint) pt {
    if (((ZGAlerts*)v.window)->_count > 0) {
        NSImage* image = self.currentSprite;
        [image drawAtPoint: pt fromRect: NSZeroRect operation: NSCompositeSourceOver fraction: 1];
        if (_next == null) {
            _next = [ZGUtils invokeLater: ^{
                v.needsDisplayInRect = NSMakeRect(pt.x, pt.y, image.size.width, image.size.height);
                _next = null;
            } delay: 1.0 / _fps];
        }
    }
}

@end

@interface ZGProgress : NSView {
    @public int64_t _pos;
    @public int64_t _total;
    @public NSString* _topText;
    @public NSString* _bottomText;
    NSDictionary* _textAttributes;
    NSRect _stop_rect;
    NSImage* __weak _stop;
    NSImage* _stop_n;
    NSImage* _stop_p;
    ZGAnimatedImage* _spinner;
}
@end

@implementation ZGProgress

- (id) init {
    self = super.init;
    if (self != null) {
        alloc_count(self);
        self.autoresizingMask = NSViewMinYMargin;
        _stop_n = [[NSImage imageNamed: @"stop-n-32x32@2.png"] copy];
        _stop_n.size = NSMakeSize(18, 18);
        _stop_p = [[NSImage imageNamed: @"stop-p-32x32@2.png"] copy];
        _stop_p.size = NSMakeSize(18, 18);
        _stop = _stop_n;
        _spinner = [ZGAnimatedImage.alloc initWith: @"spinner" frames: 12 size: NSMakeSize(20, 20) fps: 20];
        NSMutableParagraphStyle* style = NSMutableParagraphStyle.new;
        style.alignment = NSCenterTextAlignment;
        _textAttributes = @{ NSFontAttributeName: [NSFont systemFontOfSize: NSFont.smallSystemFontSize],
                   NSParagraphStyleAttributeName: style };
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    _spinner->_next = _spinner->_next.cancel;
    _spinner = null;
}

- (void) progress: (int64_t) pos of: (int64_t) total {
    assert(0 <= pos && pos <= total);
    _pos = MIN(MAX(0, pos), total);
    _total = total;
    self.needsDisplay = true;
}

- (void) setTopText: (NSString*) s {
    _topText = s;
    self.needsDisplay = true;
}

- (void) setBottomText: (NSString*) s {
    _bottomText = s;
    self.needsDisplay = true;
}

- (BOOL) mouseDownCanMoveWindow {
    return false;
}

- (void) mouseDown: (NSEvent*) e {
    NSPoint pt = [self convertPoint: e.locationInWindow fromView: self.window.contentView];
    if (NSPointInRect(pt, _stop_rect)) {
        _stop = _stop_p;
        self.needsDisplay = true;
    }
}

- (void) mouseUp: (NSEvent*) e {
    NSPoint pt = [self convertPoint: e.locationInWindow fromView: self.window.contentView];
    if (NSPointInRect(pt, _stop_rect)) {
        [(ZGAlerts*)self.window requestCancel];
    }
    _stop = _stop_n;
    self.needsDisplay = true;
}

- (BOOL) acceptsFirstResponder {
    return true;
}

- (void) cancelOperation: (id) sender {
    [(ZGAlerts*)self.window requestCancel];
}

- (void) drawRect: (NSRect) r {
    if (((ZGAlerts*)self.window)->_count == 0) {
        return;
    }
    r = self.bounds;
    NSColor* c0 = [NSColor colorWithCalibratedRed: .95 green: .97 blue: 1 alpha: 1];
    NSColor* c1 = [NSColor colorWithCalibratedRed: .89 green: .91 blue: .94 alpha: 1];
    NSGradient* g = [NSGradient.alloc initWithStartingColor: c1 endingColor: c0];
    [g drawInRect: NSMakeRect(1, r.size.height / 2, r.size.width - 2, r.size.height / 2 - 2) angle: 90];

    c0 = [NSColor colorWithCalibratedRed: .84 green: .86 blue: .89 alpha: 1];
    c1 = [NSColor colorWithCalibratedRed: .79 green: .82 blue: .87 alpha: 1];
    g = [NSGradient.alloc initWithStartingColor: c1 endingColor: c0];
    [g drawInRect: NSMakeRect(1, 1, r.size.width - 2, r.size.height / 2) angle: 90];

    NSBezierPath* path = NSBezierPath.bezierPath;
    path.lineWidth = 1;
    for (int y = r.origin.y; y < r.origin.y + r.size.height; y += 3) {
        [path moveToPoint: NSMakePoint(1, y)];
        [path lineToPoint: NSMakePoint(r.size.width - 1, y)];
    }
    NSColor* c = [NSColor colorWithCalibratedRed: .92 green: .95 blue: .97 alpha: 1];
    [c set];
    [path stroke];

    c = [NSColor colorWithCalibratedWhite: .90 alpha: 1];
    [self drawBorder: NSMakeRect(0, 0, r.size.width, r.size.height - 2) color: c0 radius: 4];
    c = [NSColor colorWithCalibratedWhite: .16 alpha: 1];
    [self drawBorder: NSMakeRect(0, 1, r.size.width, r.size.height) color: c radius: 4];
    c = [NSColor colorWithCalibratedWhite: .50 alpha: 1];
    [self drawBorder: NSMakeRect(0, 1, r.size.width, r.size.height - 1) color: c radius: 4];

    NSRect p = r;
    p.size.height = 6;
    p.origin.y += (r.size.height - p.size.height) / 2;
    p.origin.x += 30;
    p.size.width -= 60;
    double ratio = MIN(MAX(0, (double)_pos / (double)_total), 1);

    c0 = [NSColor colorWithCalibratedWhite: .95 alpha: 1];
    [self drawBorder: NSMakeRect(p.origin.x, p.origin.y - 1, p.size.width, p.size.height + 1) color: c0 radius: 1];

    c0 = [NSColor colorWithCalibratedRed: .39 green: .40 blue: .41 alpha: 1];
    c1 = [NSColor colorWithCalibratedRed: .76 green: .77 blue: .78 alpha: 1];
    g = [NSGradient.alloc initWithStartingColor: c1 endingColor: c0];
    [g drawInRect: p angle: 90];

    CGFloat w = p.size.width * ratio;
    if (ratio > 0) {
        c0 = [NSColor colorWithCalibratedRed: .48 green: .57 blue: .69 alpha: 1];
        c1 = [NSColor colorWithCalibratedRed: .83 green: .87 blue: .91 alpha: 1];
        g = [NSGradient.alloc initWithStartingColor: c1 endingColor: c0];
        [g drawInRect: NSMakeRect(p.origin.x, p.origin.y + p.size.height / 2, w, p.size.height / 2) angle: 90];
        c1 = [NSColor colorWithCalibratedRed: .45 green: .52 blue: .62 alpha: 1];
        g = [NSGradient.alloc initWithStartingColor: c0 endingColor: c1];
        [g drawInRect: NSMakeRect(p.origin.x, p.origin.y, w, p.size.height / 2) angle: 90];
    }
    NSPoint pt = {p.origin.x + p.size.width + 5, p.origin.y - (_stop.size.height - p.size.height) / 2};
    _stop_rect.origin = pt;
    _stop_rect.size = _stop.size;
    [_stop drawAtPoint: pt fromRect: NSZeroRect operation: NSCompositeSourceOver fraction: 1];
    if (((ZGAlerts*)self.window)->_alert == null) {
        [_spinner drawIntoView: (NSView*) self point: NSMakePoint(p.origin.x - 25, pt.y)];
    }

    if (_topText != null) {
        NSRect tr = NSMakeRect(p.origin.x, p.origin.y + p.size.height + 2,
                               p.size.width, NSFont.smallSystemFontSize + 4);
        [_topText drawInRect: tr withAttributes: _textAttributes];
    }
    if (_bottomText != null) {
        NSRect tr = NSMakeRect(p.origin.x, p.origin.y - NSFont.smallSystemFontSize - p.size.height / 2 - 2,
                               p.size.width, NSFont.smallSystemFontSize + 4);
        [_bottomText drawInRect: tr withAttributes: _textAttributes];
    }
}

- (void) drawBorder: (NSRect) rect color: (NSColor*) color radius: (CGFloat) r {
    NSGraphicsContext.currentContext.compositingOperation = NSCompositeCopy;
    NSRect newRect = NSMakeRect(rect.origin.x + 1, rect.origin.y + 1, rect.size.width - 2, rect.size.height - 2);
    NSBezierPath *path = [NSBezierPath bezierPathWithRoundedRect: newRect xRadius: r yRadius: r];
    path.lineWidth = 1;
    [color set];
    [path stroke];
}

- (BOOL) isOpaque {
    return true;
}

@end

@implementation ZGAlerts

@synthesize topText;
@synthesize bottomText;

- (id) initWithDocument: (ZGDocument*) d {
    self = super.init;
    if (self != null) {
        alloc_count(self);
        _document = d;
        [self setupProgressBar];
        _boxes = [ZGAnimatedImage.alloc initWith: @"box" frames: 10 size: NSMakeSize(64, 64) fps: FPS];
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    [self killTimer];
    _delayedDismiss = _delayedDismiss.cancel;
    _document = null;
    _progress = null;
    _contentView.subviews = @[];
    _contentView = null;
    _alert = null;
    _block = null;
}

- (void) killTimer {
    if (_timer != null) {
        dispatch_source_cancel(_timer);
        _timer = null;
    }
}

- (void) progress: (int64_t) pos of: (int64_t) total {
    [_progress progress: pos of: total];
    _contentView.needsDisplay = true;
}

- (NSString*) topText {
    return _progress->_topText;
}

- (void) setTopText: (NSString*) s {
    _progress.topText = s;
}

- (NSString*) bottomText {
    return _progress->_bottomText;
}

- (void) setBottomText: (NSString*) s {
    _progress.bottomText = s;
}

- (void) begin {
    assert([NSThread isMainThread]);
    assert(_count == 0);
    _count++;
    [NSApp beginSheet: self modalForWindow: _document.window didEndBlock:^(NSInteger rc) {
        // trace(@"");
    }];
}

- (BOOL) isOpen {
    return _count != 0;
}

- (void) end {
    assert([NSThread isMainThread]);
    assert(_count == 1);
    _count--;
    [NSApp endSheet: self];
    [self orderOut: self];
    [self dismissAlert: NSAlertErrorReturn resize: true];
}

- (void) restoreSize {
    // The order of resizes is very important. The window has to be resized first
    self.size = _initialContentViewSize;
    _contentView.subviews = @[_progress];
    _contentView.size = _initialContentViewSize;
    _contentView.superview.size = _initialContentViewSize;
}

- (void) dismissAlert: (NSInteger) rc resize: (BOOL) b {
    if (_block != null) {
        void (^done) (NSInteger rc) = _block;
        _block = null;
        if (done != null) {
            done(rc);
        }
    }
    if (_alert != null) {
        setTarget(_contentView, self, _alert);
        _alert = null;
        [self killTimer];
        if (_delayedDismiss != null) {
            _delayedDismiss = _delayedDismiss.cancel;
        }
        if (b) {
            _delayedDismiss = [ZGUtils invokeLater: ^{ [self restoreSize]; } delay: 0.75];
        }
    }
}

- (void) requestCancel {
    [_document requestCancel];
}

- (void) setSize: (NSSize) size {
    self.minSize = size;
    self.maxSize = size; // animate: false does not work at all
    [self setFrame: NSMakeRect(0, 0, size.width, size.height) display: true animate: true];
}

- (void) setupProgressBar {
    NSNumber* width = [NSUserDefaults.standardUserDefaults objectForKey: @"zipeg.alerts.width"];
    NSSize size = NSMakeSize(width == null ? 531 : width.floatValue, 90);
    self.size = size;
    if (_contentView == null) {
        _contentView = self.contentView;
        _contentView.autoresizesSubviews = true;
        _progress = ZGProgress.new;
        _progress.frame = NSMakeRect(10, size.height - 80, size.width - 20, 50);
    }
    [self makeFirstResponder: _progress];
    _contentView.subviews = @[_progress];
    _initialContentViewSize = _contentView.frame.size;
}

static void setTarget(NSView* v, id old, id target) {
    if ([v isKindOfClass: NSButton.class] && old == ((NSControl*)v).target) {
        // trace(@"target=0x%016llx", (int64_t)(__bridge void*)(((NSControl*)v).target));
        ((NSControl*)v).target = target;
        ((NSControl*)v).action = @selector(buttonPressed:);
    }
    for (NSView* c in v.subviews) {
        setTarget(c, old, target);
    }
}

- (void) alert: (NSAlert*) a done: (void(^)(NSInteger rc)) d {
    NSSize old = _initialContentViewSize;
    if (_alert) {
        [self dismissAlert: NSAlertErrorReturn resize: false];
    }
    [a layout];
    _delayedDismiss = _delayedDismiss.cancel;
    _alert = a;
    _block = d;
    NSWindow* w = a.window;
    NSView* acv = w.contentView;
    [NSUserDefaults.standardUserDefaults setObject: @(acv.frame.size.width) forKey: @"zipeg.alerts.width"];
    CGFloat width = MAX(acv.frame.size.width, old.width);
    NSSize size = NSMakeSize(width, acv.frame.size.height + old.height);
    _contentView.size = size;
    _contentView.superview.size = size;
    [acv removeFromSuperview];
    _contentView.subviews = @[_progress];
    [_contentView addSubview: acv];
    void (^__block b)() = ^() {
        if (_alert != null) {
            NSImageView* iv = (NSImageView*)[_contentView findViewByClassName: @"NSImageView"];
            assert(iv != null);
            // TODO: http://stackoverflow.com/questions/2795882/how-can-i-animate-a-content-switch-in-an-nsimageview
            iv.image = _boxes.currentSprite;
            iv.needsDisplay = true;
        }
    };
    b();
    setTarget(_contentView, a, self);
    self.size = size;
    _timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0, 0, dispatch_get_main_queue());
    if (_timer != null) {
        dispatch_time_t startTime = dispatch_time(DISPATCH_TIME_NOW, FPS * NSEC_PER_SEC);
        uint64_t interval = (1 / FPS) * NSEC_PER_SEC; // every 2 seconds, converted to nanosecs
        dispatch_source_set_timer(_timer, startTime, interval, 8000);
        dispatch_source_set_event_handler(_timer, b);
        dispatch_resume(_timer);
    }
    [self makeKeyAndOrderFront: self]; // window.moveToFront
    NSArray* tfs = [_contentView findViewsByClassName: @"NSTextField"];
    for (NSTextField* tf in tfs) {
        if (tf.isEditable) {
            [self makeFirstResponder: tf];
            break;
        }
    }
}

- (void) buttonPressed: (id) sender {
    if ([sender isKindOfClass: NSButton.class]) { // see NSAlert notes below
        NSButton* btn = (NSButton*)sender;
        [self dismissAlert: btn.tag resize: true];
    }
}

@end

/*
 
TODO: make sure ALL alerts use addButton and not  NSAlertDefaultReturn /... crap

NSAlert notes:

 for addButtonWithTitle (visually right to left) NSAlert adds button with tags
 
 NSAlertFirstButtonReturn	= 1000,
 NSAlertSecondButtonReturn	= 1001,
 NSAlertThirdButtonReturn	= 1002
...
 that do correspond to return codes.
 
 However for alertWithError and alertWithMessageText:alertWithMessageText:alternateButton:otherButton
 
the tags and return codes are (as defined in NSPanel.h):

 NSAlertDefaultReturn		= 1,
 NSAlertAlternateReturn		= 0,
 NSAlertOtherReturn		= -1,
 NSAlertErrorReturn		= -2

and corresponds to:

 NSOKButton			= 1,
 NSCancelButton			= 0


see:
 http://www.cocoabuilder.com/archive/cocoa/130608-returncode-from-beginsheetmodalforwindow-is-incorrect.html
-----------
NSThemeFrame frame={{0, 0}, {774, 164}} bounds={{0, 0}, {774, 164}}  tag=-1 title=
    _NSAlertContentView frame={{0, 0}, {774, 142}} bounds={{0, 0}, {774, 142}}  tag=-1
         NSImageView frame={{24, 62}, {64, 64}} bounds={{0, 0}, {64, 64}}  tag=0
         NSTextField frame={{104, 109}, {653, 17}} bounds={{0, 0}, {653, 17}}  tag=0 stringValue=Alert button tags and their correspondense to result code
         NSTextField frame={{104, 87}, {653, 14}} bounds={{0, 0}, {653, 14}}  tag=0 stringValue=(You can switch this warning back on in Preferences)
         NSButton frame={{105, 57}, {629, 18}} bounds={{0, 0}, {629, 18}}  tag=0 title=Больше не показывать данное сообщение
         NSButton frame={{669, 12}, {91, 32}} bounds={{0, 0}, {91, 32}}  tag=1 title=OK
         NSButton frame={{478, 12}, {96, 32}} bounds={{0, 0}, {96, 32}}  tag=0 title=Cancel
         NSButton frame={{576, 12}, {91, 32}} bounds={{0, 0}, {91, 32}}  tag=-1 title=Other
         NSButton frame={{385, 12}, {91, 32}} bounds={{0, 0}, {91, 32}}  tag=1003 title=first
         NSButton frame={{287, 12}, {96, 32}} bounds={{0, 0}, {96, 32}}  tag=1004 title=second
         NSButton frame={{194, 12}, {91, 32}} bounds={{0, 0}, {91, 32}}  tag=1005 title=third
         NSButton frame={{101, 12}, {91, 32}} bounds={{0, 0}, {91, 32}}  tag=1006 title=fourth
 
 
 NSAlert* test = [NSAlert alertWithMessageText: @"Alert button tags and their correspondense to result code"
 alertWithMessageText: @"OK"
 alternateButton: @"Cancel"
 otherButton: @"Other"
 informativeTextWithFormat: @"(You can switch this warning back on in Preferences)"];
 [test addButtonWithTitle: @"first"];
 [test addButtonWithTitle: @"second"];
 [test addButtonWithTitle: @"third"];
 [test addButtonWithTitle: @"fourth"];
 test.showsSuppressionButton = true;

*/

