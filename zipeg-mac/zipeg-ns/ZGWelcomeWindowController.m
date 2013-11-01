#import "ZGWelcomeWindowController.h"
#import "ZGImage.h"
#import "ZGApp.h"

@interface ZGWelcomeContentView : NSView {
    NSColor* _background;
    NSImage* _icon;
    NSImage* _reflection;
}
@end

@implementation ZGWelcomeContentView

- (id) init {
    self = super.init;
    if (self != null) {
        _background = [NSColor colorWithPatternImage: [NSImage imageNamed:@"white_wall"]];
        _icon = ZGApp.appIcon.copy;
        _icon.size = NSMakeSize(128, 128);
        _reflection = [_icon upsideDownShadowed: 0.35];
    }
    return self;
}

static NSString* text = @"Welcome to Zipeg";

- (void)drawRect: (NSRect) r {
    [_background setFill];
    NSRectFill(r);
    NSSize s = self.frame.size;
    CGFloat x = (s.width / 2 - _icon.size.width) / 2;
    CGFloat y = s.height / 2 + (s.height - _icon.size.height) / 4;
    [_icon drawAtPoint: NSMakePoint(x, y)];
    y -= _icon.size.height;
    [_reflection drawAtPoint: NSMakePoint(x, y)];

    [NSColor.whiteColor set];
    NSColor* dark = [NSColor colorWithCalibratedWhite:0.1 alpha:1];
    NSDictionary* b = @{NSFontAttributeName: [NSFont fontWithName:@"HelveticaNeue" size: 40],
                        NSForegroundColorAttributeName: dark};
    NSDictionary* w = @{NSFontAttributeName: [NSFont fontWithName:@"HelveticaNeue" size: 40],
                        NSForegroundColorAttributeName: NSColor.lightGrayColor};
    NSSize ts = [text sizeWithAttributes: b];
    CGFloat xt = (s.width / 2 - ts.width) / 2;
    CGFloat yt = y + ts.height * 1.5;

    [text drawAtPoint: NSMakePoint(xt, yt) withAttributes: w];
    xt--;
    yt++;
    [text drawAtPoint: NSMakePoint(xt, yt) withAttributes: b];
    [super drawRect: r];
}

@end


@interface ZGWelcomeWindowController ()

@end

@implementation ZGWelcomeWindowController

- (id) init {
    NSWindow* window = NSWindow.new;
    self = [super initWithWindow: window];
    if (self != null && window != null) {
        alloc_count(self);
        self.window.delegate = self;
        self.window.showsResizeIndicator = false;
        self.window.styleMask = NSTitledWindowMask | NSClosableWindowMask;
        self.window.hasShadow = true;
        self.window.showsToolbarButton = false;

        NSView* cv = ZGWelcomeContentView.new;
        cv.autoresizesSubviews = true;
        cv.frameSize = NSMakeSize(800, 500);
        self.window.contentSize = cv.frame.size;
        self.window.contentMinSize = cv.frame.size;
        self.window.contentMaxSize = cv.frame.size;
        self.window.contentView = cv;


        NSScreen* scr = self.window.screen;
        CGFloat x = (scr.visibleFrame.size.width - cv.frame.size.width) / 2;
        CGFloat y = scr.visibleFrame.size.height - (scr.visibleFrame.size.height - cv.frame.size.height) / 8;
        self.window.frameTopLeftPoint = NSMakePoint(x, y);
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

@end
