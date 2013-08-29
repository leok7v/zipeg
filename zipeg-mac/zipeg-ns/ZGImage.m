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

- (void) makePixelSized {
    NSSize max = NSZeroSize;
    for (NSObject* o in self.representations) {
        if ([o isKindOfClass: NSImageRep.class]) {
            NSImageRep* r = (NSImageRep*)o;
            if (r.pixelsWide != NSImageRepMatchesDevice && r.pixelsHigh != NSImageRepMatchesDevice) {
                max.width = MAX(max.width, r.pixelsWide);
                max.height = MAX(max.height, r.pixelsHigh);
            }
        }
    }
    if (max.width > 0 && max.height > 0) {
        self.size = max;
    }
}

- (NSImage*) rotate: (CGFloat) degrees { // counter-clockwise
    // test();
    const NSSize size = self.size;
    degrees = degrees - 360 * floor(degrees / 360);
    NSAssert(0 <= degrees && degrees <= 360, @"something wrong with my math");
    [NSGraphicsContext.currentContext saveGraphicsState];
    CGFloat radians = degrees * 3.14159265358979323846 / 180;
    CGAffineTransform rot = CGAffineTransformMakeRotation(radians);
    NSRect r = NSMakeRect(-size.width / 2, -size.height / 2, size.width, size.height);
    r = CGRectApplyAffineTransform(r, rot);
    NSSize rs = r.size;
//  trace("%@ %f %@", NSStringFromSize(size), degrees, NSStringFromSize(rs));
    NSImage* ri = [NSImage.alloc initWithSize: rs];
    NSAffineTransform* rccw = NSAffineTransform.transform;
    [rccw rotateByDegrees: degrees];
    NSAffineTransform* tran = NSAffineTransform.transform;
    [tran translateXBy:  rs.width / 2 yBy: rs.height / 2];
    [rccw appendTransform: tran];
    [ri lockFocus]; // NSGraphicsContext.currentContext = rotatedImage
    NSGraphicsContext.currentContext.imageInterpolation = NSImageInterpolationHigh;
    [rccw concat];
    [self drawAtPoint: NSMakePoint(-size.width / 2, -size.height / 2)];
    [ri unlockFocus];
    [NSGraphicsContext.currentContext restoreGraphicsState];
    NSAssert(size.width == self.size.width && size.height == self.size.height, @"corrupted image");
    return ri;
}

- (NSImage*) mirror {
    if (self == null) {
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

- (NSImage*) square: (CGFloat) size {
    if (self == null) {
        return null;
    } else {
        if (size == 0) {
            size = MAX(self.size.width, self.size.height);
        }
        if (self.size.width == size && self.size.height == size) {
            return self;
        }
        NSImage* m = [NSImage.alloc initWithSize: NSMakeSize(size, size)];
        if (m != null) {
            [NSGraphicsContext.currentContext saveGraphicsState];
            [m lockFocus]; // NSGraphicsContext.currentContext = m
            NSGraphicsContext.currentContext.imageInterpolation = NSImageInterpolationHigh;
            CGFloat max = MAX(self.size.width, self.size.height);
            CGFloat x = (max - self.size.width) / 2;
            CGFloat y = (max - self.size.height) / 2;
            x = x * size / max;
            y = y * size / max;
            NSRect r = NSMakeRect(x, y, self.size.width * size / max, self.size.height * size / max);
            [self drawInRect: r];
            [m unlockFocus];
            [NSGraphicsContext.currentContext restoreGraphicsState];
        }
        return m;
    }
}

- (int64_t) imageBytes {
    int64_t bytes = 0;
    for (NSImageRep* o in self.representations) {
        if ([o isKindOfClass: NSBitmapImageRep.class]) {
            /*
             trace("%@ samplesPerPixel=%ld bitsPerPixel=%ld bytesPerRow=%ld bytesPerPlane=%ld numberOfPlanes=%ld",
             NSStringFromSize(r.size),
             r.samplesPerPixel, r.bitsPerPixel, r.bytesPerRow, r.bytesPerPlane, r.numberOfPlanes);
             */
            NSBitmapImageRep* r = (NSBitmapImageRep*)o;
            bytes += MAX(r.size.height * r.bytesPerRow, r.bytesPerPlane * r.numberOfPlanes);
        } else if (isEqual(NSStringFromClass(o.class), @"NSIconRefImageRep")) {
            // most likely cached standard icon -> zero bytes expense
        } else {
            bytes += o.bitsPerSample * o.pixelsWide * o.pixelsHigh;
            // trace("representations=%@", o);
        }
    }
    return bytes;
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

enum { // Baseline TIFF Orientation
    ORIENTATION_UNKNOWN      = 0,
    ORIENTATION_TOP_LEFT     = 1, // 0th row at top, 0th column at left  (below step to normalize to TOP LEFT)
    ORIENTATION_TOP_RIGHT    = 2, // 0th row at top, 0th column at right (mirror)
    ORIENTATION_BOTTOM_RIGHT = 3, // 0th row at bottom, 0th column at right (rotate 180 == flip + mirror)
    ORIENTATION_BOTTOM_LEFT  = 4, // 0th row at bottom, 0th column at left (flip)
    ORIENTATION_LEFT_TOP     = 5, // 0th row at left, 0th column at top 6 (transpose?)
    ORIENTATION_RIGHT_TOP    = 6, // 0th row at right, 0th column at top (rotate 90 clockwise)
    ORIENTATION_RIGHT_BOTTOM = 7, // 0th row at right, 0th column at bottom (transverse?)
    ORIENTATION_LEFT_BOTTOM  = 8  // 0th row at left, 0th column at bottom (rotate 270 clockwise)
};

/* A transversion is a 180° rotation followed by a transposition */

+ (NSImage*) exifThumbnail: (NSURL*) url {
    // timestamp("exifThumbnail");
    NSImage* t = null;
    CGImageSourceRef source = CGImageSourceCreateWithURL((__bridge CFURLRef)url, null);
    if (source != null) {
        CFDictionaryRef meta = CGImageSourceCopyPropertiesAtIndex(source, 0, null);
        if (meta != null) {
            NSDictionary* opt = @{ (NSString*)kCGImageSourceCreateThumbnailFromImageAlways: @false,
                                   (NSString*)kCGImageSourceCreateThumbnailFromImageIfAbsent: @false };
            NSObject* o = (__bridge NSObject*)CFDictionaryGetValue(meta, kCGImagePropertyOrientation);
            if ([o isKindOfClass: NSNumber.class] ||
                (ORIENTATION_TOP_LEFT <= ((NSNumber*)o).intValue && ((NSNumber*)o).intValue <= ORIENTATION_LEFT_BOTTOM)) {
                int orientation = ((NSNumber*)o).intValue;
                // at the time of writing CGImageSourceCreateThumbnailAtIndex does NOT respect EXIF orientation tag
                // gotta do it hard way:
                CGImageRef thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, (__bridge CFDictionaryRef)opt);
                if (thumbnail != null) {
                    t = [NSImage.alloc initWithCGImage: thumbnail];
                    if (t != null) {
                        // trace("%@ orientation=%d", url.path.lastPathComponent, orientation);
                        switch (orientation) {
                            case ORIENTATION_TOP_LEFT: break;
                            case ORIENTATION_TOP_RIGHT: t = t.mirror; break;
                            case ORIENTATION_BOTTOM_RIGHT: t.flipped = true; t = t.mirror; break;
                            case ORIENTATION_BOTTOM_LEFT: t.flipped = true; break;
                            case ORIENTATION_LEFT_TOP: t = null; break; // transpose not supported for now
                            case ORIENTATION_RIGHT_TOP: t = [t rotate: 270]; break; // 90 cw == 270 ccw
                            case ORIENTATION_RIGHT_BOTTOM: t = null; break; // transverse not supported for now
                            case ORIENTATION_LEFT_BOTTOM: t = [t rotate: 90]; break;  // 270 cw == 90 ccw
                            default: NSAssert(false, @"unknown orientation: %d -- ignored", orientation); break;
                        }
                    }
                    CFRelease(thumbnail);
                }
            } else { // no orientation tag:
                CGImageRef thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, (__bridge CFDictionaryRef)opt);
                if (thumbnail != null) {
                    t = [NSImage.alloc initWithCGImage: thumbnail];
                    CFRelease(thumbnail);
                }
            }
            CFRelease(meta);
        }
        CFRelease(source);
    }
    // timestamp("exifThumbnail");
    if (t != null) {
        // trace(@"exifThumbnail=%@", NSStringFromSize(t.size));
    }
    return [t square: 0];
}

/*
 TODO:

 time: extract 30 milliseconds

 time: extract 592 microseconds
 time: exifThumbnail 611 microseconds
 time: NSImage.initWithContentsOfFile 25 milliseconds
 time: exifThumbnail 27 milliseconds
 time: NSImage.initWithContentsOfFile 3061 microseconds
 time: qlImage 5476 microseconds

 time: extract 6084 microseconds
 time: exifThumbnail 4326 microseconds
 time: NSImage.initWithContentsOfFile 3327 microseconds
 time: qlImage 15 milliseconds

 time: extract 293 milliseconds
 time: exifThumbnail 90 milliseconds
 time: qlImage 385 milliseconds

 time: extract 823 microseconds
 time: exifThumbnail 2203 microseconds
 time: NSImage.initWithContentsOfFile 1649 microseconds
 time: qlImage 6088 microseconds

 time: extract 5021 microseconds
 time: exifThumbnail 4147 microseconds
 time: NSImage.initWithContentsOfFile 2883 microseconds
 time: qlImage 13 milliseconds
 time: qlImage 512 milliseconds
 
 time: extract 5308 microseconds
 time: exifThumbnail 4804 microseconds
 time: NSImage.initWithContentsOfFile 4422 microseconds
 time: qlImage 1076 milliseconds

 time: extract 4796 microseconds
 time: exifThumbnail 4384 microseconds
 time: NSImage.initWithContentsOfFile 2908 microseconds
 time: qlImage 1064 milliseconds

 time: extract 6765 microseconds
 time: exifThumbnail 4537 microseconds
 time: NSImage.initWithContentsOfFile 3062 microseconds
 time: qlImage 1074 milliseconds

 time: extract 12 milliseconds
 time: exifThumbnail 4437 microseconds
 time: NSImage.initWithContentsOfFile 3139 microseconds
 time: qlImage 1066 milliseconds

 time: extract 17 milliseconds
 time: exifThumbnail 5907 microseconds
 time: NSImage.initWithContentsOfFile 9162 microseconds
 time: qlImage 1095 milliseconds

 time: extract 5840 microseconds
 time: exifThumbnail 4498 microseconds
 time: NSImage.initWithContentsOfFile 2929 microseconds
 time: qlImage 1086 milliseconds

 time: extract 4619 microseconds
 time: exifThumbnail 4569 microseconds
 time: NSImage.initWithContentsOfFile 2912 microseconds
 time: qlImage 1066 milliseconds

 time: extract 4768 microseconds
 time: exifThumbnail 4644 microseconds (!!!)
 time: NSImage.initWithContentsOfFile 2990 microseconds (!!!)
 time: qlImage 1066 milliseconds (!!!)

 time: extract 4198 microseconds
 time: exifThumbnail 4806 microseconds
 time: NSImage.initWithContentsOfFile 2984 microseconds
 time: qlImage 1074 milliseconds

 time: extract 7970 microseconds
 time: exifThumbnail 6929 microseconds
 time: NSImage.initWithContentsOfFile 4491 microseconds
 time: qlImage 1073 milliseconds

 time: extract 5611 microseconds
 time: exifThumbnail 4509 microseconds
 time: NSImage.initWithContentsOfFile 2932 microseconds
 time: qlImage 1068 milliseconds

 time: extract 4280 microseconds
 time: exifThumbnail 4787 microseconds
 time: NSImage.initWithContentsOfFile 3045 microseconds
 time: qlImage 1079 milliseconds

 time: extract 4814 microseconds
 time: exifThumbnail 4482 microseconds
 time: NSImage.initWithContentsOfFile 2906 microseconds
 time: qlImage 1074 milliseconds

 */

+ (NSImage*) qlImage: (NSString*) path ofSize: (NSSize) size asIcon: (BOOL) icon {
    NSURL* url = [NSURL fileURLWithPath: path];
    NSImage* i = null;
    // timestamp("qlImage");
    NSDictionary *d = @{ (NSString*)kQLThumbnailOptionIconModeKey: @(icon)};
    QLThumbnailRef tr = QLThumbnailCreate(kCFAllocatorDefault,
                                          (__bridge CFURLRef)url,
                                          CGSizeMake(size.width, size.height),
                                          (__bridge CFDictionaryRef)d);
    if (tr != null) {
        /*      NSRect cr = QLThumbnailGetContentRect(tr); */
        NSSize maxSize = QLThumbnailGetMaximumSize(tr);
        size.width = MIN(maxSize.width, size.width);
        size.height = MIN(maxSize.height, size.height);
        // kQLThumbnailOptionScaleFactorKey absent defaults to @(1.0)
        CGImageRef ir = QLThumbnailImageCreate(kCFAllocatorDefault,
                                               (__bridge CFURLRef)url,
                                               CGSizeMake(size.width, size.height),
                                               (__bridge CFDictionaryRef)d);
        if (ir != null) {
            i = [NSImage.alloc initWithCGImage: ir];
            CFRelease(ir);
        }
        if (i == null) {
            i = [NSWorkspace.sharedWorkspace iconForFile: path];
            if (i != null && size.width > 0 && size.height > 0) {
                [i setSize: size];
            }
        }
        CFRelease(tr);
    }
    // timestamp("qlImage");
    if (i != null) {
        // trace("qlImage %@", NSStringFromSize(i.size));
    }
    return i;
}

@end

@implementation ZGImage {
    NSMutableDictionary* _hints;
}

@synthesize transform;

// TODO: this code has not been tested yet!
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

