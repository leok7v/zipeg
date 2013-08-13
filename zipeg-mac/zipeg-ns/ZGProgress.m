#import "ZGProgress.h"


@interface ZGProgressBar : NSView {
    float progress;
}

@end


@implementation ZGProgressBar

- (id) init {
    self = super.init;
    if (self != null) {
        progress = 0.5;
    }
    return self;
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
    NSColor* lc = [NSColor colorWithCalibratedRed: .92 green: .95 blue: .97 alpha: 1];
    [lc set];
    [path stroke];

    NSColor* c = [NSColor colorWithCalibratedWhite: .90 alpha: 1];
    [self drawBorder: NSMakeRect(0, 0, r.size.width, r.size.height - 2) color: c0 radius: 4];
    c = [NSColor colorWithCalibratedWhite: .16 alpha: 1];
    [self drawBorder: NSMakeRect(0, 1, r.size.width, r.size.height) color: c radius: 4];
    c = [NSColor colorWithCalibratedWhite: .50 alpha: 1];
    [self drawBorder: NSMakeRect(0, 1, r.size.width, r.size.height - 1) color: c radius: 4];

    NSRect p = r;
    p.size.height = 6;
    p.origin.y += (r.size.height - p.size.height) / 2;
    p.origin.x += 10;
    p.size.width -= 20;
    c0 = [NSColor colorWithCalibratedWhite: .95 alpha: 1];
    [self drawBorder: NSMakeRect(p.origin.x, p.origin.y - 1, p.size.width, p.size.height + 1) color: c0 radius: 1];

    c0 = [NSColor colorWithCalibratedRed: .39 green: .40 blue: .41 alpha: 1];
    c1 = [NSColor colorWithCalibratedRed: .76 green: .77 blue: .78 alpha: 1];
    g = [NSGradient.alloc initWithStartingColor: c1 endingColor: c0];
    [g drawInRect: p angle: 90];


    CGFloat w = p.size.width * progress;
    c0 = [NSColor colorWithCalibratedRed: .48 green: .57 blue: .69 alpha: 1];
    c1 = [NSColor colorWithCalibratedRed: .83 green: .87 blue: .91 alpha: 1];
    g = [NSGradient.alloc initWithStartingColor: c1 endingColor: c0];
    [g drawInRect: NSMakeRect(p.origin.x, p.origin.y + p.size.height / 2, w, p.size.height / 2) angle: 90];
    c1 = [NSColor colorWithCalibratedRed: .45 green: .52 blue: .62 alpha: 1];
    g = [NSGradient.alloc initWithStartingColor: c0 endingColor: c1];
    [g drawInRect: NSMakeRect(p.origin.x, p.origin.y, w, p.size.height / 2) angle: 90];
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


@interface ZGProgress() {
    NSView* __weak _contentView;
    ZGProgressBar* _progressBar;
}

@end


@implementation ZGProgress

- (id) init {
    self = super.init;
    if (self != null) {
        alloc_count(self);
        NSSize size = NSMakeSize(400, 90);
        self.minSize = size;
        self.maxSize = size;
        [self setFrame: NSMakeRect(0, 0, size.width, size.height) display: true animate: false];
        _contentView = self.contentView;
        _contentView.alphaValue = 1;
        _progressBar = ZGProgressBar.new;
        _progressBar.frame = NSMakeRect(10, size.height - 80, size.width - 20, 50);
        _contentView.subviews = @[_progressBar];
    }
    return self;
}

@end
