#import "ZGImages.h"

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

+ (NSImage*) thumbnail: (NSString*) path {
    NSDictionary* dic = null;
    NSURL* url = [NSURL fileURLWithPath : path];
    CGImageSourceRef source = CGImageSourceCreateWithURL((__bridge CFURLRef)url, null);
    if (source == null) {
        CGImageSourceStatus status = CGImageSourceGetStatus(source);
        NSLog(@"Error: file name : %@ - Status: %d", path, status);
    } else {
        CFDictionaryRef metadataRef =
        CGImageSourceCopyPropertiesAtIndex ( source, 0, NULL );
        if (metadataRef != null) {
            NSDictionary* immutableMetadata = (__bridge NSDictionary*)metadataRef;
            if ( immutableMetadata != null) {
                dic = [ NSDictionary dictionaryWithDictionary : immutableMetadata];
            }
            CFRelease(metadataRef);
        }
        NSDictionary* opt = @{ (NSString*)kCGImageSourceThumbnailMaxPixelSize: @1024,
                               (NSString*)kCGImageSourceCreateThumbnailFromImageIfAbsent: @false,
                               (NSString*)kCGImageSourceCreateThumbnailWithTransform: @true};
        CGImageRef thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, (__bridge CFDictionaryRef)opt);
        trace("%@", thumbnail);
        NSImage* t = [NSImage.alloc initWithCGImage: thumbnail];
        trace("%@ %@", t, NSStringFromSize(t.size));
        CFRelease(source);
        source = null;
        trace(@"%@", dic);
        NSDictionary* tiff = dic[@"{TIFF}"];
        NSDictionary* exif = dic[@"{Exif}"];
        trace(@"TIFF=%@", tiff);
        trace(@"Exif=%@", exif);

    }
    return null;
}

@end
