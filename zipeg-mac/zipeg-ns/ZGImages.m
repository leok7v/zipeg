#import "ZGImages.h"
#import "ZGImage.h"

@interface ZGImages () {
    NSImage* _dirImage;
    NSImage* _dirOpen;
    NSImage* _docImage;
    NSImage* _appImage; // Zipeg.icns
    NSMutableDictionary* _icons16x16;
    NSMutableDictionary* _icons;
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
        _icons = [NSMutableDictionary dictionaryWithCapacity: 1024];
    }
    return self;
}

- (NSImage*) iconForFileType: (NSString*) ext {
    if (ext == null || ext.length == 0) {
        return null;
    }
    NSImage* img = _icons[ext];
    if (img == null) {
        img = [NSWorkspace.sharedWorkspace iconForFileType: ext];
        _icons[ext] = img;
    }
    return img;
}

- (NSImage*) iconForFileType16x16: (NSString*) ext {
    if (ext == null || ext.length == 0) {
        return null;
    }
    NSImage* img = _icons16x16[ext];
    if (img == null) {
        img = [self iconForFileType: ext];
        if (img != null) {
            img = img.copy;
            img.size = NSMakeSize(16, 16);
            _icons16x16[ext] = img;
        }
    }
    return img;
}

+ (NSImage*) iconForFileType: (NSString*) pathExtension {
    return [_shared iconForFileType16x16: pathExtension];
}

+ (NSImage*) iconForFileType16x16: (NSString*) pathExtension {
    return [_shared iconForFileType16x16: pathExtension];
}

+ (NSImage*) thumbnail: (NSString*) path {
    timestamp("thumbnail");
    NSURL* url = [NSURL fileURLWithPath : path];
    NSImage* t = [NSImage exifThumbnail: url];
    if (t == null) {
        // initWithContentsOfFile does respect EXIF orientation tag:
        t = [NSImage.alloc initWithContentsOfFile: path];
    }
    timestamp("thumbnail"); // 2680 microseconds
    if (t != null) {
        trace(@"thumbnail.size=%@", NSStringFromSize(t.size));
    }
    timestamp("quicklook");
    t = [NSImage qlImage: path ofSize: NSMakeSize(512, 512) asIcon: true];
    timestamp("quicklook");
    if (t != null) {
        trace(@"quicklook.size=%@", NSStringFromSize(t.size));
    }
    return t;
}

@end
