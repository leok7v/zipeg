#import "ZGHeroView.h"
#import "ZGApp.h"
#import "ZGDocument.h"

@interface NSImage (Transform)
- (NSImage*)imageRotatedByDegrees:(CGFloat)degrees ;
@end

@implementation NSImage (Transform)

- (NSImage*) imageRotatedByDegrees: (CGFloat) degrees {
    while (degrees < 0) {
        degrees += 360;
    }
    while (degrees > 360) {
        degrees -= 360;
    }
    NSSize rotatedSize = NSMakeSize(self.size.height, self.size.width);
    NSImage* rotatedImage = [[NSImage alloc] initWithSize: rotatedSize];
    NSAffineTransform* transform = [NSAffineTransform transform] ;
    [transform translateXBy:  self.size.width / 2 yBy: self.size.height / 2] ;
    [transform rotateByDegrees: degrees];
    // Then translate the origin system back to the bottom left
    [transform translateXBy: -rotatedSize.width / 2 yBy: -rotatedSize.height / 2] ;
    [rotatedImage lockFocus]; // NSGraphicsContext.currentContext = rotatedImage
    NSGraphicsContext.currentContext.imageInterpolation = NSImageInterpolationHigh;
    [transform concat];
    [self drawAtPoint:NSMakePoint(0,0) fromRect:NSZeroRect operation:NSCompositeCopy fraction:1.0];
    [rotatedImage unlockFocus];
    return rotatedImage;
}

@end

// http://stackoverflow.com/questions/2962790/best-way-to-change-the-background-color-for-an-nsview

@interface ZGHeroView() {
    NSImage* _appIcon;
    NSImage* _leafs[4];
    NSImage* _images[200];
    int _index[200];
    ZGDocument* __weak _document;
}

@end

// time: ZGHeroView init 75 milliseconds
// time: ZGHeroView draw 20 milliseconds

@implementation ZGHeroView

- (id) initWithDocument: (ZGDocument*) doc andFrame:(NSRect)frame {
    self = [super initWithFrame: frame];
    // trace(@"initWithFrame %@", NSStringFromRect(self.frame));
    if (self != null) {
        alloc_count(self);
        _document = doc;
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (void) setFrame: (NSRect) frameRect {
    [super setFrame:frameRect];
}

- (BOOL) isOpaque {
    return true;
}

- (void) prepareImages {
    assert(_appIcon == null);
    _appIcon = ZGApp.appIcon32x32;
    _leafs[0] = [[NSImage imageNamed: @"leaf-0-64x64.png"] copy];
    _leafs[1] = [[NSImage imageNamed: @"leaf-1-64x64.png"] copy];
    _leafs[2] = [[NSImage imageNamed: @"leaf-2-64x64.png"] copy];
    _leafs[3] = [[NSImage imageNamed: @"leaf-3-64x64.png"] copy];
    for (int i = 0; i < countof(_images); i++) {
        int degree = arc4random_uniform(360);
        int size = 32 + arc4random_uniform(32);
        NSImage* img = arc4random_uniform(100) < 3 ? _appIcon : _leafs[arc4random_uniform(4)];
        img.size = NSMakeSize(size, size);
        if (img == _appIcon) {
            degree = degree / 4 - 45;
        }
        _images[i] = [img imageRotatedByDegrees: degree];
        _index[i] = arc4random_uniform(countof(_images));
    }
}

static NSString* text = @"Drop Files Here";

- (void) drawImages: (NSRect) rect {
    if (_appIcon == null) {
        [self prepareImages];
    }
    // trace(@"drawRect %@", NSStringFromRect(rect));
    rect = self.bounds; // always paint complete view
    [NSGraphicsContext.currentContext saveGraphicsState];
    NSGraphicsContext.currentContext.imageInterpolation = NSImageInterpolationHigh;
    CGContextRef context = (CGContextRef) [NSGraphicsContext.currentContext graphicsPort];
    CGContextSetRGBFillColor(context, 0.227, 0.337, 0.251, 0.8);
    CGContextFillRect(context, NSRectToCGRect(rect));
    /* Your drawing code [NSImage drawAtPoint....]for the image goes here
     Also, if you need to lock focus when drawing, do it here.       */
    int dy = 0;
    for (float y = rect.origin.y - 64; y < rect.size.height + 64; y += 20) {
        int dx = _index[dy % countof(_index)];
        for (float x = rect.origin.x - 64; x < rect.size.width + 64; x += 20) {
            int ix = _index[dx % countof(_index)];
            NSImage* i = _images[ix];
            [i drawInRect: NSMakeRect(x, rect.size.height - y, i.size.width, i.size.height) fromRect: NSZeroRect
                operation: NSCompositeSourceOver fraction:1];
            dx++;
        }
        dy++;
    }
    [NSGraphicsContext.currentContext restoreGraphicsState];
}

- (void) drawRect: (NSRect) rect {
//  trace("%@", NSStringFromRect(rect));
    rect = self.bounds; // always paint complete view
    CGColorSpaceRef colorspace = CGColorSpaceCreateDeviceGray();
    CGContextRef maskContext = CGBitmapContextCreate(null, self.bounds.size.width, self.bounds.size.height,
                                                        8, self.bounds.size.width, colorspace, 0);
    CGColorSpaceRelease(colorspace);
    NSGraphicsContext *maskGraphicsContext = [NSGraphicsContext graphicsContextWithGraphicsPort: maskContext flipped: false];
    [NSGraphicsContext saveGraphicsState];
    [NSGraphicsContext setCurrentContext: maskGraphicsContext];
    [[NSColor lightGrayColor] setFill];
    CGContextFillRect(maskContext, rect);
    if (_document.isNew) {
        NSColor* dark = [NSColor colorWithCalibratedWhite:0.1 alpha:1];
        NSDictionary* b = @{NSFontAttributeName: [NSFont fontWithName:@"HelveticaNeue-Bold" size: 64],
                            NSForegroundColorAttributeName: dark};
        NSDictionary* w = @{NSFontAttributeName: [NSFont fontWithName:@"HelveticaNeue-Bold" size: 64],
                            NSForegroundColorAttributeName: [NSColor lightGrayColor]};
        NSRect r;
        r.size = [text sizeWithAttributes: b];
        r.origin.x = (rect.size.width - r.size.width) / 2;
        r.origin.y = (rect.size.height - r.size.height) / 2;
        r.origin.x++;
        r.origin.y++;
        [text drawInRect: r withAttributes: w];
        [self drawBorder: r color: NSColor.lightGrayColor];
        r.origin.x--;
        r.origin.y--;
        [text drawInRect: r withAttributes: b];
        [self drawBorder: r color: dark];
    }
    [NSGraphicsContext restoreGraphicsState];
    CGImageRef alphaMask = CGBitmapContextCreateImage(maskContext);
    CGContextRef windowContext = NSGraphicsContext.currentContext.graphicsPort;
    [NSColor.whiteColor setFill];
    CGContextFillRect(windowContext, rect);
    CGContextSaveGState(windowContext);
    CGContextClipToMask(windowContext, NSRectToCGRect(self.bounds), alphaMask);
    [self drawImages: rect];
    CGContextRestoreGState(windowContext);
    CGImageRelease(alphaMask);
}

-(void) drawBorder: (NSRect) rect color: (NSColor*) color {
    NSRect newRect = NSMakeRect(rect.origin.x - 10, rect.origin.y - 10, rect.size.width + 20, rect.size.height + 20);
    NSBezierPath *path = [NSBezierPath bezierPathWithRoundedRect: newRect xRadius: 10 yRadius: 10];
    path.lineWidth = 10;
    [color set];
    CGFloat dash[] = { 42.0, 8.0 };
    [path setLineDash: dash count: countof(dash) phase: 0.0];
    [path stroke];
}

@end
