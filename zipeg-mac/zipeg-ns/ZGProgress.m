#import "ZGProgress.h"
#import "ZGApp.h"

@class ZGProgressBar;

@interface ZGProgress() {
    NSView* __weak _contentView;
    ZGProgressBar* _progressBar;
    NSTextField* _text;
    NSButton* _cancel;
    NSButton* _proceed;
    int _count;
}
- (void) setupProgressBar;
- (void) setupCancelContinue;
@end

@interface ZGProgressBar : NSView {
    @public float _progress;
    NSImage* _cancel_n;
    NSImage* _cancel_p;
    NSImage* __weak _cancel;
    NSRect _cancel_rect;
}
@end


@implementation ZGProgressBar

- (id) init {
    self = super.init;
    if (self != null) {
        alloc_count(self);
        _cancel_n = [[NSImage imageNamed: @"stop-n"] copy];
        _cancel_n.size = NSMakeSize(18, 18);
        _cancel_p = [[NSImage imageNamed: @"stop-p"] copy];
        _cancel_p.size = NSMakeSize(18, 18);
        _cancel = _cancel_n;
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

- (BOOL) mouseDownCanMoveWindow {
    return false;
}

- (void) mouseDown: (NSEvent*) e {
    NSPoint pt = [self convertPoint: e.locationInWindow fromView: self.window.contentView];
    if (NSPointInRect(pt, _cancel_rect)) {
        _cancel = _cancel_p;
        self.needsDisplay = true;
    }
}

- (void)mouseUp: (NSEvent*) e {
    NSPoint pt = [self convertPoint: e.locationInWindow fromView: self.window.contentView];
    if (NSPointInRect(pt, _cancel_rect)) {
        ZGProgress* pg = (ZGProgress*)self.window;
        [pg setupCancelContinue];
    }
    _cancel = _cancel_n;
    self.needsDisplay = true;
}

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
    c0 = [NSColor colorWithCalibratedWhite: .95 alpha: 1];
    [self drawBorder: NSMakeRect(p.origin.x, p.origin.y - 1, p.size.width, p.size.height + 1) color: c0 radius: 1];

    c0 = [NSColor colorWithCalibratedRed: .39 green: .40 blue: .41 alpha: 1];
    c1 = [NSColor colorWithCalibratedRed: .76 green: .77 blue: .78 alpha: 1];
    g = [NSGradient.alloc initWithStartingColor: c1 endingColor: c0];
    [g drawInRect: p angle: 90];


    CGFloat w = p.size.width * _progress;
    c0 = [NSColor colorWithCalibratedRed: .48 green: .57 blue: .69 alpha: 1];
    c1 = [NSColor colorWithCalibratedRed: .83 green: .87 blue: .91 alpha: 1];
    g = [NSGradient.alloc initWithStartingColor: c1 endingColor: c0];
    [g drawInRect: NSMakeRect(p.origin.x, p.origin.y + p.size.height / 2, w, p.size.height / 2) angle: 90];
    c1 = [NSColor colorWithCalibratedRed: .45 green: .52 blue: .62 alpha: 1];
    g = [NSGradient.alloc initWithStartingColor: c0 endingColor: c1];
    [g drawInRect: NSMakeRect(p.origin.x, p.origin.y, w, p.size.height / 2) angle: 90];

    NSPoint pt = {p.origin.x + p.size.width + 5, p.origin.y - (_cancel.size.height - p.size.height) / 2};
    _cancel_rect.origin = pt;
    _cancel_rect.size = _cancel.size;
    [_cancel drawAtPoint: pt fromRect: NSZeroRect operation: NSCompositeSourceOver fraction: 1];
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


@implementation ZGProgress

@synthesize progress;

- (id) init {
    self = super.init;
    if (self != null) {
        alloc_count(self);
        [self setupProgressBar];
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (float) progress {
    return _progressBar->_progress;
}

- (void) setProgress: (float) v {
    assert(0 <= v && v <= 1);
    v = MIN(MAX(0, v), 1);
    _progressBar.progress = v;
}

- (void) begin: (NSWindow*) w {
    assert([NSThread isMainThread]);
    assert(_count == 0);
    _count++;
    [NSApp beginSheet: self modalForWindow: w didEndBlock:^(NSInteger rc) {
        trace(@"");
    }];
}

- (void) end {
    assert([NSThread isMainThread]);
    assert(_count == 1);
    _count--;
    [NSApp endSheet: self];
    [self orderOut: null];
}

- (void) setupProgressBar {
    NSSize size = NSMakeSize(400, 90);
    self.minSize = size;
    self.maxSize = size;
    [self setFrame: NSMakeRect(0, 0, size.width, size.height) display: true animate: true];
    if (_contentView == null) {
        _contentView = self.contentView;
        _contentView.alphaValue = 1;
        _progressBar = ZGProgressBar.new;
    }
    _progressBar.frame = NSMakeRect(10, size.height - 60, size.width - 20, 50);
    _contentView.subviews = @[_progressBar];
}

- (void) setupCancelContinue {
    NSSize size = NSMakeSize(400, 90 + 30);
    self.minSize = size;
    self.maxSize = size;
    _progressBar.frame = NSMakeRect(10, size.height - 60, size.width - 20, 50);
    [self setFrame: NSMakeRect(0, 0, size.width, size.height) display: true animate: true];

    NSFont* font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize];
    if (_text == null) {
        _text    = createTextField(font, @"An operation is still in progress. Do you want to cancel it?", 10, 40);
        _proceed = createButton(font, @"Continue", 10, 10);
        _cancel  = createButton(font, @" Stop ", _proceed.frame.origin.x + _proceed.frame.size.width + 10, 10);
        _cancel.keyEquivalent = @"\r";
    }
    self.defaultButtonCell = _cancel.cell; // make ENTER in window to press this button
    [self makeFirstResponder: _cancel];
    _contentView.subviews = @[_progressBar, _cancel, _proceed, _text];
    _cancel.target = self;
    _proceed.target = self;
    _proceed.action = @selector(proceed:);
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
    bc.bezelStyle = NSTexturedRoundedBezelStyle;
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
