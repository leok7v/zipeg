#import "ZGAlerts.h"
#import "ZGDocument.h"
#import "ZGApp.h"

@class ZGProgressBar;

@interface ZGAlerts() {
    NSView* __weak _contentView;
    ZGDocument* __weak _document;
    ZGProgressBar* _progressBar;
    NSButton* _cancel;
    NSButton* _proceed;
    int _count;
    NSString* _savedTop;
    NSString* _savedBottom;
    NSSize    _savedContentViewSize;
    NSArray*  _savedContentViewSubviews;
    NSAlert*  _alert; // strong
    void(^_block)(NSInteger rc); // strong
}
- (void) setupProgressBar;
- (void) setupCancelContinue;
@end

@interface ZGProgressBar : NSView {
    @public float _progress;
    @public NSString* _topText;
    @public NSString* _bottomText;
    NSDictionary* _textAttributes;
    NSRect _stop_rect;
    NSImage* __weak _stop;
    NSImage* _stop_n;
    NSImage* _stop_p;
}
@end

@implementation ZGProgressBar

- (id) init {
    self = super.init;
    if (self != null) {
        alloc_count(self);
        self.autoresizingMask = NSViewMinYMargin;
        _stop_n = [[NSImage imageNamed: @"stop-n"] copy];
        _stop_n.size = NSMakeSize(18, 18);
        _stop_p = [[NSImage imageNamed: @"stop-p"] copy];
        _stop_p.size = NSMakeSize(18, 18);
        _stop = _stop_n;
        NSMutableParagraphStyle* style = NSMutableParagraphStyle.new;
        style.alignment = NSCenterTextAlignment;
        _textAttributes = @{ NSFontAttributeName: [NSFont systemFontOfSize: NSFont.smallSystemFontSize],
                   NSParagraphStyleAttributeName: style };
    }
    return self;
}


- (void) dealloc {
    dealloc_count(self);
}

- (void) setProgress: (float) v {
    assert(0 <= v && v <= 1);
    _progress = MIN(MAX(0, v), 1);
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
        [(ZGAlerts*)self.window setupCancelContinue];
    }
    _stop = _stop_n;
    self.needsDisplay = true;
}

- (BOOL) acceptsFirstResponder {
    return true;
}

- (void)cancelOperation: (id) sender {
    [(ZGAlerts*)self.window setupCancelContinue];
}

/*
- (void) keyDown: (NSEvent*) e {
    switch (e.keyCode) {
        case 53: // esc
            trace(@"ESC");
            break;
        default:
            [super keyDown: e];
    }
}
*/

- (void) drawRect: (NSRect) r {
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
    if (_progress > 0) {
        CGFloat w = p.size.width * _progress;
        c0 = [NSColor colorWithCalibratedWhite: .95 alpha: 1];
        [self drawBorder: NSMakeRect(p.origin.x, p.origin.y - 1, p.size.width, p.size.height + 1) color: c0 radius: 1];

        c0 = [NSColor colorWithCalibratedRed: .39 green: .40 blue: .41 alpha: 1];
        c1 = [NSColor colorWithCalibratedRed: .76 green: .77 blue: .78 alpha: 1];
        g = [NSGradient.alloc initWithStartingColor: c1 endingColor: c0];
        [g drawInRect: p angle: 90];

        c0 = [NSColor colorWithCalibratedRed: .48 green: .57 blue: .69 alpha: 1];
        c1 = [NSColor colorWithCalibratedRed: .83 green: .87 blue: .91 alpha: 1];
        g = [NSGradient.alloc initWithStartingColor: c1 endingColor: c0];
        [g drawInRect: NSMakeRect(p.origin.x, p.origin.y + p.size.height / 2, w, p.size.height / 2) angle: 90];
        c1 = [NSColor colorWithCalibratedRed: .45 green: .52 blue: .62 alpha: 1];
        g = [NSGradient.alloc initWithStartingColor: c0 endingColor: c1];
        [g drawInRect: NSMakeRect(p.origin.x, p.origin.y, w, p.size.height / 2) angle: 90];

        NSPoint pt = {p.origin.x + p.size.width + 5, p.origin.y - (_stop.size.height - p.size.height) / 2};
        _stop_rect.origin = pt;
        _stop_rect.size = _stop.size;
        [_stop drawAtPoint: pt fromRect: NSZeroRect operation: NSCompositeSourceOver fraction: 1];
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

@synthesize progress;
@synthesize topText;
@synthesize bottomText;

- (id) initWithDocument: (ZGDocument*) d {
    self = super.init;
    if (self != null) {
        alloc_count(self);
        _document = d;
        [self setupProgressBar];
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    _document = null;
    _progressBar = null;
    _proceed = null;
    _cancel = null;
    _savedContentViewSubviews = @[];
    _contentView.subviews = @[];
    _contentView = null;
    _alert = null;
    _block = null;
}

- (float) progress {
    return _progressBar->_progress;
}

- (void) setProgress: (float) v {
    _progressBar.progress = v;
}

- (NSString*) topText {
    return _progressBar->_topText;
}

- (void) setTopText: (NSString*) s {
    _progressBar.topText = s;
}

- (NSString*) bottomText {
    return _progressBar->_bottomText;
}

- (void) setBottomText: (NSString*) s {
    _progressBar.bottomText = s;
}

- (void) begin: (NSWindow*) w {
    assert([NSThread isMainThread]);
    assert(_count == 0);
    _count++;
    [NSApp beginSheet: self modalForWindow: w didEndBlock:^(NSInteger rc) {
        trace(@"");
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
    [self orderOut: null];
}

- (void) saveText {
    _savedTop = _progressBar->_topText;
    _savedBottom = _progressBar->_bottomText;
}

- (void) restoreText {
    if (_savedTop != null || _savedBottom != null) {
        _progressBar.topText = _savedTop;
        _progressBar.bottomText = _savedBottom;
        _savedTop = null;
        _savedBottom = null;
    }
}

- (void) setSize: (NSSize) size {
    self.minSize = size;
    self.maxSize = size; // animate: false does not work at all
    [self setFrame: NSMakeRect(0, 0, size.width, size.height) display: true animate: true];
}

- (void) setupProgressBar {
    [self restoreText];
    NSSize size = NSMakeSize(400, 90);
    self.size = size;
    if (_contentView == null) {
        _contentView = self.contentView;
        _contentView.autoresizesSubviews = true;
        _progressBar = ZGProgressBar.new;
        _progressBar.frame = NSMakeRect(10, size.height - 80, size.width - 20, 50);
    }
    [self makeFirstResponder: _progressBar];
    _contentView.subviews = @[_progressBar];
}

- (void) setupCancelContinue {
    [self saveText];
    NSSize size = NSMakeSize(400, 90 + 20);
    self.size = size;
    self.topText = @"The operation is still in progress."; // TODO: save what was there before
    self.bottomText = @"Do you want to stop it?";
    NSFont* font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize];
    if (_proceed == null) {
        _proceed = createButton(font, @"Continue", 10, 10);
        _cancel  = createButton(font, @" Stop ", _proceed.frame.origin.x + _proceed.frame.size.width + 10, 10);
        _cancel.keyEquivalent = @"\r";
        int pw = _proceed.frame.size.width;
        int cw = _cancel.frame.size.width;
        int x = (size.width - pw - cw - 20) / 2;
        _proceed.origin = NSMakePoint(x, _proceed.frame.origin.y);
        _cancel.origin = NSMakePoint(size.width - x - _cancel.frame.size.width, _proceed.frame.origin.y);
    }
    self.defaultButtonCell = _cancel.cell; // make _cancel in window default button
    [self makeFirstResponder: _proceed];
    _contentView.subviews = @[_progressBar, _cancel, _proceed];
    _cancel.target = self;
    _cancel.action = @selector(stop:);
    _proceed.target = self;
    _proceed.action = @selector(proceed:);
}


static void setTarget(NSView* v, id target) {
    if ([v isKindOfClass: NSButton.class]) {
        ((NSControl*)v).target = target;
    }
    for (NSView* c in v.subviews) {
        setTarget(c, target);
    }
}

- (void) alert: (NSAlert*) a done: (void(^)(NSInteger rc)) d {
    // dumpViews(_contentView.superview);
    // NSLog(@"-----------");
    [a layout];
    // trace(@"%@", a.buttons);
    // NSLog(@"-----------");
    NSWindow* w = a.window;
    NSView* acv = w.contentView;
    // dumpViews(acv.superview);
    _savedContentViewSubviews = _contentView.subviews.copy;
    _savedContentViewSize = _contentView.frame.size;
    _contentView.size = acv.frame.size;
    _contentView.superview.size = acv.frame.size;
    NSArray* subviews = acv.subviews.copy;
    acv.subviews = @[]; // remove subviews first
    _contentView.subviews = subviews; // then add them to the content view
    setTarget(_contentView, self);
    // NSLog(@"-----------");
    // dumpViews(_contentView.superview);
    self.size = _contentView.superview.frame.size;
    NSTextField* input = (NSTextField*)[_contentView findViewByClassName: @"NSTextField"];
    if (input != null) {
        [self makeFirstResponder: input];
    }
    _alert = a;
    _block = d;
}

- (id) performSelector: (SEL) sel withObject: (id) o {
    // TODO: this is HACKy - think about cleaner way
    // trace(@"%@(%@)", NSStringFromSelector(sel), o);
    if (sel == @selector(buttonPressed:) && [o isKindOfClass: NSButton.class]) {
        // see NSAlert notes at the end of the file
        NSButton* btn = (NSButton*)o;
        trace(@"buttonPressed: tag=%ld)", btn.tag);
        if (_alert != null) {
            setTarget(_contentView, null);
            void (^done) (NSInteger rc) = _block;
            _block = null;
            _alert = null;
            _contentView.subviews = _savedContentViewSubviews.copy;
            _contentView.size = _savedContentViewSize;
            _contentView.superview.size = _savedContentViewSize;
            self.size = _savedContentViewSize;
            _savedContentViewSubviews = null;
            if (done != null) {
                done(btn.tag);
            }
            return null;
        }
    } else if (sel == @selector(stop:) || sel == @selector(proceed:)) {
        return [super performSelector: sel withObject: o];
    }
    return null;
}

- (void) stop : (id) sender {
    [_document cancel];
}

- (void) proceed : (id) sender {
    [self setupProgressBar];
}

static NSButton* createButton(NSFont* font, NSString* title, int x, int y) {
    NSButton* btn = NSButton.new;
    btn.title = NSLocalizedString(title, @"");
    btn.frame = NSMakeRect(0, 10, 300, 10);
    btn.buttonType = NSMomentaryPushInButton;
    NSButtonCell* bc = btn.cell;
    bc.bezelStyle = NSRoundedBezelStyle; // this is the only style that respects default button
    bc.highlightsBy = NSPushInCellMask;
    bc.controlTint = NSBlueControlTint;
    bc.focusRingType = NSFocusRingTypeDefault; // NSFocusRingTypeNone;
    bc.bordered = true;
    bc.font = font;
    btn.frame = NSMakeRect(x, y, 100, NSFont.smallSystemFontSize);
    [btn sizeToFit];
    return btn;
}

static NSTextField* createTextField(NSFont* font, NSString* s, int x, int y) {
    NSTextField* tf = NSTextField.new;
    tf.editable = false;
    tf.stringValue = NSLocalizedString(s, @"");
    tf.frame = NSMakeRect(x, y, 300, NSFont.smallSystemFontSize);
    tf.drawsBackground = false;
    tf.bordered = false;
    tf.enabled = false;
    [tf sizeToFit];
    return tf;
}

@end

/*
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

