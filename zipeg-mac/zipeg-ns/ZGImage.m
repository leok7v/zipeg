#import "ZGImage.h"
#import <QuickLook/QuickLook.h>

static NSDictionary* sHints;

@implementation NSImage (ZGExtensions)

+ (void) initialize {
    sHints = @{ NSImageHintInterpolation: @(NSImageInterpolationHigh) };
}

- (id) initWithCGImage: (CGImageRef) ir {
    if (ir == null) {
        self = null;
    } else {
        NSBitmapImageRep* bir = [NSBitmapImageRep.alloc initWithCGImage: ir];
        if (bir != null) {
            self = [self initWithSize: bir.size];
            if (self != null) {
                [self addRepresentation: bir];
            }
        }
    }
    return self;
}

- (NSImage*) rotate: (CGFloat) degrees {
    // test();
    degrees = degrees - 360 * floor(degrees / 360);
    NSAssert(0 <= degrees && degrees <= 360, @"something wrong with my math");
    NSSize save = self.size;
    [NSGraphicsContext.currentContext saveGraphicsState];
    CGFloat radians = degrees * 3.14159265358979323846 / 180;
    CGAffineTransform rot = CGAffineTransformMakeRotation(radians);
    NSRect r = NSMakeRect(-self.size.width / 2, -self.size.height / 2, self.size.width, self.size.height);
    r = CGRectApplyAffineTransform(r, rot);
    NSSize rs = r.size;
//  trace("%@ %f %@", NSStringFromSize(self.size), degrees, NSStringFromSize(rs));
    NSImage* ri = [NSImage.alloc initWithSize: rs];
    NSAffineTransform* t = NSAffineTransform.transform;
    [t translateXBy:  self.size.width / 2 yBy: self.size.height / 2] ;
    [t rotateByDegrees: degrees];
    // Then translate the origin system back to the bottom left
    [t translateXBy: -rs.width / 2 yBy: -rs.height / 2] ;
    [ri lockFocus]; // NSGraphicsContext.currentContext = rotatedImage
    // NSGraphicsContext.currentContext.imageInterpolation = NSImageInterpolationHigh;
    [t concat];
    [self drawAtPoint: NSMakePoint(0, 0) fromRect: NSZeroRect operation: NSCompositeSourceOver fraction: 1];
    // [self drawInRect: NSMakeRect(0, 0, rs.width, rs.height)];
    [ri unlockFocus];
    [NSGraphicsContext.currentContext restoreGraphicsState];
    NSAssert(save.width == self.size.width && save.height == self.size.height, @"corrupted image");
    return ri;
}

- (NSImage*) mirror {
    if (self == 0) {
        return null;
    } else {
        NSImage* m = [NSImage.alloc initWithSize: self.size];
        if (m != null) {
            [NSGraphicsContext.currentContext saveGraphicsState];
            NSAffineTransform *t = [NSAffineTransform transform];
            // if original image was flipped we will render it upsidedown and the
            // resulting image absorbs "flipped" state and is not flipped anymore
            m.flipped = false;
            [t scaleXBy: -1 yBy: 1];
            [t translateXBy: -self.size.width yBy: 0];
            [m lockFocus]; // NSGraphicsContext.currentContext = m
            NSGraphicsContext.currentContext.imageInterpolation = NSImageInterpolationHigh;
            [t concat];
            [self drawAtPoint: NSMakePoint(0, 0) fromRect: NSZeroRect operation: NSCompositeSourceOver fraction: 1];
            [m unlockFocus];
            [NSGraphicsContext.currentContext restoreGraphicsState];
        }
        return m;
    }
}

+ (NSImage*) qlImage: (NSString*) path ofSize: (NSSize) size asIcon: (BOOL) icon {
    NSURL *fileURL = [NSURL fileURLWithPath:path];
    if (!path || !fileURL) {
        return nil;
    }
    // kQLThumbnailOptionScaleFactorKey absent defaults to @(1.0)
    NSDictionary *d = @{ (NSString*)kQLThumbnailOptionIconModeKey: @(icon)};
    CGImageRef ref = QLThumbnailImageCreate(kCFAllocatorDefault,
                                            (__bridge CFURLRef)fileURL,
                                            CGSizeMake(size.width, size.height),
                                            (__bridge CFDictionaryRef)d);
    NSImage* i = null;
    if (ref != NULL) {
        i = [NSImage.alloc initWithCGImage: ref];
        CFRelease(ref);
    }
    if (i == null) {
        i = [NSWorkspace.sharedWorkspace iconForFile: path];
        if (i != null && size.width > 0 && size.height > 0) {
            [i setSize:size];
        }
    }
    return i;
}

- (void) drawAtPoint: (NSPoint) p {
    [self drawAtPoint: p fromRect: NSZeroRect];
}

- (void) drawAtPoint: (NSPoint) p fromRect: (NSRect) r {
    [self drawAtPoint: p fromRect: r operation: NSCompositeSourceOver fraction: 1];
}

- (void) drawAtPoint: (NSPoint) p fromRect: (NSRect) r operation: (NSCompositingOperation) op {
    [self drawAtPoint: p fromRect: r operation: op opacity: 1];
}

- (void) drawAtPoint: (NSPoint) p fromRect: (NSRect) r operation: (NSCompositingOperation) op opacity: (CGFloat) alpha {
    [self drawAtPoint: p fromRect: r operation: op fraction: alpha];
}

- (void) drawInRect: (NSRect) d {
    [self drawInRect: d fromRect: NSZeroRect];
}

- (void) drawInRect: (NSRect) d fromRect: (NSRect) r {
    [self drawInRect: d fromRect: r operation: NSCompositeSourceOver];
}

- (void) drawInRect: (NSRect) d fromRect: (NSRect) r operation: (NSCompositingOperation) op {
    [self drawInRect: d fromRect: r operation: op opacity: 1];
}

- (void) drawInRect: (NSRect) d fromRect: (NSRect) r operation: (NSCompositingOperation) op opacity: (CGFloat) alpha {
    if ([self isKindOfClass: ZGImage.class]) {
        [((ZGImage*)self) drawInRect: d fromRect: r operation: op opacity: alpha];
    } else {
        [self drawInRect: d fromRect: r operation: op fraction: alpha respectFlipped: true hints: sHints];
    }
}

@end

@implementation ZGImage {
    NSMutableDictionary* _hints;
}

@synthesize transform;


- (void) drawInRect: (NSRect)d fromRect: (NSRect) r operation: (NSCompositingOperation) op opacity: (CGFloat) alpha {
    if (_hints == null) {
        NSAssert(sHints != null, @"NSImage(ZGExtensions) is not initialized?");
        _hints = [NSMutableDictionary dictionaryWithDictionary: sHints];
    }
    if (_hints != null && transform != null) {
        _hints[NSImageHintCTM] = transform;
    }
    [self drawInRect: d fromRect: r operation: op fraction: alpha respectFlipped: true hints: _hints];
}

- (void) drawAtPoint: (NSPoint) p fromRect: (NSRect) r operation: (NSCompositingOperation) op opacity: (CGFloat) alpha {
    [self drawAtPoint: p fromRect: r operation: op fraction: alpha];
}

@end

