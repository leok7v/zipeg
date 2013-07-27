#import "ZGHeroView.h"

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
    NSImage* rotatedImage = [[NSImage alloc] initWithSize:rotatedSize];
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
}

@end

// time: ZGHeroView init 75 milliseconds
// time: ZGHeroView draw 20 milliseconds

@implementation ZGHeroView

- (id)initWithFrame:(NSRect)frame {
    self = [super initWithFrame:frame];
    // trace(@"initWithFrame %@", NSStringFromRect(self.frame));
    if (self != null) {
        _leafs[0] = [NSImage imageNamed:@"leaf-0-64x64.png"];
        _leafs[1] = [NSImage imageNamed:@"leaf-1-64x64.png"];
        _leafs[2] = [NSImage imageNamed:@"leaf-2-64x64.png"];
        _leafs[3] = [NSImage imageNamed:@"leaf-3-64x64.png"];
        // http://stackoverflow.com/questions/1359060/how-can-i-load-an-nsimage-representation-of-the-icon-for-my-application
        NSString* appPath = [[NSBundle mainBundle] bundlePath];
        _appIcon = [[NSWorkspace sharedWorkspace] iconForFile:appPath];
        for (int i = 0; i < countof(_images); i++) {
            int degree = arc4random_uniform(360);
            int size = 32 + arc4random_uniform(32);
            NSImage* img = arc4random_uniform(100) < 3 ? _appIcon : _leafs[arc4random_uniform(4)];
            img.size = NSMakeSize(size, size);
            if (img == _appIcon) {
                degree = degree / 4 - 45;
            }
            _images[i] = [img imageRotatedByDegrees:degree];
            _index[i] = arc4random_uniform(countof(_images));
        }
    }
    return self;
}

- (void) setFrame:(NSRect)frameRect {
    [super setFrame:frameRect];
}

- (void)drawRect:(NSRect)dirtyRect {
    // trace(@"drawRect %@", NSStringFromRect(dirtyRect));
//  [[NSImage imageNamed:@"background.png"] drawInRect:dirtyRect fromRect:NSZeroRect operation:NSCompositeSourceOver fraction:1];
// Passing NSZeroRect causes the entire image to draw.
//  [_appIcon drawInRect:dirtyRect fromRect:NSZeroRect operation:NSCompositeSourceOver fraction:1]; // Passing NSZeroRect causes the entire image to draw.
    [NSGraphicsContext.currentContext saveGraphicsState];
    NSGraphicsContext.currentContext.imageInterpolation = NSImageInterpolationHigh;
    CGContextRef context = (CGContextRef) [NSGraphicsContext.currentContext graphicsPort];
    CGContextSetRGBFillColor(context, 0.227, 0.337, 0.251, 0.8);
    CGContextFillRect(context, NSRectToCGRect(dirtyRect));
    /* Your drawing code [NSImage drawAtPoint....]for the image goes here
     Also, if you need to lock focus when drawing, do it here.       */
    int dy = 0;
    for (float y = dirtyRect.origin.y - 64; y < dirtyRect.size.height + 64; y += 20) {
        int dx = _index[dy % countof(_index)];
        for (float x = dirtyRect.origin.x - 64; x < dirtyRect.size.width + 64; x += 20) {
            int ix = _index[dx % countof(_index)];
            NSImage* r = _images[ix];
            [r drawInRect:NSMakeRect(x, dirtyRect.size.height - y, r.size.width, r.size.height) fromRect:NSZeroRect
                operation:NSCompositeSourceOver fraction:1];
            dx++;
        }
        dy++;
    }
    [NSGraphicsContext.currentContext restoreGraphicsState];
//  [super drawRect:dirtyRect];
//  [_appIcon drawInRect:(NSRect)dstRect fromRect:(NSRect)srcRect operation:(NSCompositingOperation)op fraction:(CGFloat)delta];
}

@end
