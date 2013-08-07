#import "ZGDestination.h"

@implementation ZGDestination {
    NSPathControl* _path;
}

- (id) initWithFrame: (NSRect) r {
    self = [super initWithFrame: r];
    if (self) {
        alloc_count(self);
        self.autoresizingMask = NSViewWidthSizable | NSViewMinYMargin;
        self.autoresizesSubviews = true;
        r.origin.x = 20;
        r.origin.y = 2;
        r.size.width -= 40;
        r.size.height -= 4;
        _path = [[NSPathControl alloc] initWithFrame: r];
        NSURL* u = [[NSURL alloc] initFileURLWithPath: @"/Users/leo" isDirectory: true];
        _path.URL = u;
        _path.pathStyle = NSPathStyleStandard;
        _path.backgroundColor = [NSColor clearColor];
        NSPathCell* c = _path.cell;
        // c.placeholderString = @"You can drag folders here";
        c.controlSize = NSSmallControlSize; // NSSmallControlSize
        c.font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize - 1];
        _path.autoresizingMask = NSViewWidthSizable | NSViewMinYMargin;
        _path.doubleAction = @selector(pathControlDoubleClick:);
        _path.delegate = self;
        _path.focusRingType = NSFocusRingTypeNone; // because it looks ugly
        [self addSubview: _path];
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
    _path.delegate = null;
    _path = null;
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
