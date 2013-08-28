#import "ZGImages.h"
#import "ZGImage.h"

@interface ZGImages () {
    NSImage* _dirImage;
    NSImage* _dirOpen;
    NSImage* _docImage;
    NSImage* _appImage; // Zipeg.icns
    NSMutableDictionary* _icons16x16;
}
@end

@implementation ZGImages

static ZGImages* _shared;

+ (void)initialize {
    if (_shared == null) {
        _shared = [ZGImages new];
    }
}

+ (ZGImages*) shared {
    return _shared;
}

- (id) init {
    if (_shared != null) {
        @throw @"ZGImages is singleton. Use ZGImages.sharedInstance";
        return null;
    } else {
        _dirImage = [NSWorkspace.sharedWorkspace iconForFileType:NSFileTypeForHFSTypeCode(kGenericFolderIcon)]; // 
        _dirImage.size = NSMakeSize(kIconImageSize, kIconImageSize);
        _docImage = [NSWorkspace.sharedWorkspace iconForFileType:NSFileTypeForHFSTypeCode(kGenericDocumentIcon)];
        _docImage.size = NSMakeSize(kIconImageSize, kIconImageSize);
        _dirOpen = [NSWorkspace.sharedWorkspace iconForFileType:NSFileTypeForHFSTypeCode(kOpenFolderIcon)];
        _dirOpen.size = NSMakeSize(kIconImageSize, kIconImageSize);

        // http://stackoverflow.com/questions/1359060/how-can-i-load-an-nsimage-representation-of-the-icon-for-my-application
        // Note -[NSApplication applicationIconImage]; that fails to return a pasted custom icon.
        // NSString* appPath = [base::mac::MainBundle() bundlePath];
        // NSImage* appIcon = [[NSWorkspace sharedWorkspace] iconForFile:appPath];
        // but actually I alway want app icon here:
        _appImage = [NSApp applicationIconImage];
        _appImage.size = NSMakeSize(kIconImageSize, kIconImageSize);
        _icons16x16 = [NSMutableDictionary dictionaryWithCapacity: 1024];
    }
    return self;
}

- (NSImage*) iconForFileType16x16: (NSString*) ext {
    if (ext == null || ext.length == 0) {
        return null;
    }
    NSImage* img = _icons16x16[ext];
    if (img == null) {
        img = [NSWorkspace.sharedWorkspace iconForFileType: ext];
    }
    if (img != null) {
        img = img.copy;
        img.size = NSMakeSize(16, 16);
        _icons16x16[ext] = img;
    }
    return img;
}

+ (NSImage*) iconForFileType16x16: (NSString*) pathExtension {
    return [_shared iconForFileType16x16: pathExtension];
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
                        switch (orientation) {
                            case ORIENTATION_TOP_LEFT: break;
                            case ORIENTATION_TOP_RIGHT: t = t.mirror; break;
                            case ORIENTATION_BOTTOM_RIGHT: t.flipped = true; t = t.mirror; break;
                            case ORIENTATION_BOTTOM_LEFT: t.flipped = true; break;
                            case ORIENTATION_LEFT_TOP: t = null; break; // transpose not supported for now
                            case ORIENTATION_RIGHT_TOP: t = [t rotate: 90]; break;
                            case ORIENTATION_RIGHT_BOTTOM: t = null; break; // transverse not supported for now
                            case ORIENTATION_LEFT_BOTTOM: t = [t rotate: 270]; break;
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
    return t;
}

+ (NSImage*) thumbnail: (NSString*) path {
    timestamp("thumbnail");
    NSURL* url = [NSURL fileURLWithPath : path];
    NSImage* t = [ZGImages exifThumbnail: url];
    if (t == null) {
        // initWithContentsOfFile does respect EXIF orientation tag:
        t = [NSImage.alloc initWithContentsOfFile: path];
    }
    timestamp("thumbnail"); // 2680 microseconds
    if (t != null) {
        trace(@"thumbnail.size=%@", NSStringFromSize(t.size));
    }
    return t;
}

@end
