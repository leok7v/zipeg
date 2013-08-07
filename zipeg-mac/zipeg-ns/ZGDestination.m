#import "ZGDestination.h"


#define kHorizontalGap 10

@implementation ZGDestination {
    NSPathControl* _pathControl;
    NSTextField* _label;
}

static NSTextField* createLabel(NSString* text, NSFont* font, NSRect r) {
    NSRect lr = r;
    lr.origin.x = kHorizontalGap;
    lr.size.width = 100;
    NSTextField* label = [[NSTextField alloc] initWithFrame: lr];
    label.focusRingType = NSFocusRingTypeNone;
    label.stringValue = @"Unpack to: ";
    NSTextFieldCell* tc = label.cell;
    tc.usesSingleLineMode = true;
    tc.font = font;
    tc.scrollable = true;
    tc.selectable = false;
    tc.editable = false;
    tc.bordered = false;
    tc.backgroundColor = [NSColor clearColor];
    lr.size = [[label attributedStringValue] size];
    lr.origin.y = (r.size.height - lr.size.height) / 2;
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


- (id) initWithFrame: (NSRect) r {
    self = [super initWithFrame: r];
    if (self) {
        alloc_count(self);
        self.autoresizingMask = NSViewWidthSizable | NSViewMinYMargin;
        self.autoresizesSubviews = true;
        NSFont* font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize - 1];
        _label = createLabel(@"Unpack to:", font, r);
        _pathControl = createPathControl(font, r, _label.frame);
        _pathControl.delegate = self;
        self.subviews = @[_label, _pathControl];
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    _pathControl.delegate = null;
    _pathControl = null;
}

/*
- (void) drawRect: (NSRect) r {
    [[NSColor windowFrameColor] setFill];
    NSRectFill(r);
    [super drawRect:r];
}
*/

- (void) drawRect: (NSRect) r {
//  NSGradient *gradient = [[NSGradient alloc] initWithStartingColor: [NSColor orangeColor] endingColor:[NSColor lightGrayColor]];
    NSColor* t = [NSColor colorWithCalibratedRed: .92 green: .95 blue: .97 alpha: 1];
    NSColor* b = [NSColor colorWithCalibratedRed: .79 green: .82 blue: .87 alpha: 1];
    NSGradient *gradient = [[NSGradient alloc] initWithStartingColor: b endingColor: t];
    [gradient drawInRect: r angle: 90];
}

@end
