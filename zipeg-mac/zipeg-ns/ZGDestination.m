#import "ZGDestination.h"


#define kHorizontalGap 10

@interface ZGAskButtonCell : NSPopUpButtonCell

@end

@implementation ZGAskButtonCell

+ (BOOL) prefersTrackingUntilMouseUp {
    return true;
}

- (id) initWithMenu: (NSMenu*) m {
    self = [super init];
    self.menu = m;
    return self;
}

- (void) drawWithFrame: (NSRect) r inView: (NSView*)  v {
    [[NSColor redColor] setFill];
    // NSDictionary* a = @{NSFontAttributeName: self.font};
    // [@"foo" drawInRect: r withAttributes: a];
    [super drawWithFrame: r inView: v];
}

- (BOOL) trackMouse: (NSEvent*) e inRect: (NSRect) r ofView: (NSView*) btn untilMouseUp: (BOOL) up {
    if ([btn isKindOfClass: NSPopUpButton.class]) {
        NSPopUpButton* popup = (NSPopUpButton*)btn;
        NSInteger tag = popup.selectedItem.tag;
        tag = (tag + 1) % popup.menu.itemArray.count;
        [popup selectItemWithTag: tag]; // flip in place w/o menu
        call1(popup.target, popup.action, self);
        return false;
    } else {
        return [super trackMouse: e inRect: r ofView: btn untilMouseUp: up];
    }
}

@end


@implementation ZGDestination {
    NSPathControl* _pathControl;
    NSTextField* _label;
    NSPopUpButton* _ask;
}

static NSTextField* createLabel(NSString* text, NSFont* font, NSRect r) {
    NSRect lr = r;
    lr.origin.x = kHorizontalGap;
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
    NSURL* u = [[NSURL alloc] initFileURLWithPath: @"/Users/leo/Desktop" isDirectory: true];
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
    return _pathControl;
}

static void insertMenuItem(NSMenu* m, NSString* title, int tag) {
    NSMenuItem *it = [[NSMenuItem alloc] initWithTitle: NSLocalizedString(title, @"") action: null keyEquivalent:@""];
    it.tag = tag;
    [m insertItem: it atIndex: m.itemArray.count];
}

static NSPopUpButton* createPopUpButton(NSArray* texts, NSFont* font, NSRect r, NSRect lr) {
    NSRect br = r;
    br.origin.x = lr.origin.x + lr.size.width - 8;
    br.origin.y = lr.origin.y;
    br.size.height = lr.size.height;
    NSMenu* m = [[NSMenu alloc] initWithTitle:@""];
    NSDictionary* a = @{NSFontAttributeName: font};
    int tag = 0;
    int w = 20;
    for (NSString* s in texts) {
        insertMenuItem(m, s, tag++);
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

- (id) initWithFrame: (NSRect) r {
    self = [super initWithFrame: r];
    if (self) {
        alloc_count(self);
        self.autoresizingMask = NSViewWidthSizable | NSViewMinYMargin;
        self.autoresizesSubviews = true;
        NSFont* font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize - 1];

        _label = createLabel(@"Unpack ", font, r);
        _ask = createPopUpButton(@[@"asking ", @"always "], font, r, _label.frame);
        _pathControl = createPathControl(font, r, _ask.frame);

        self.subviews = @[_label, _ask, _pathControl];
        _pathControl.delegate = self;
        _ask.target = self;
        _ask.action = @selector(askPressed:);
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    _pathControl.delegate = null;
    _pathControl = null;
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

@end
