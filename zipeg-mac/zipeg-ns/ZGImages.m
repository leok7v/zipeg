#import "ZGImages.h"

@interface ZGImages () {
    NSImage* _dirImage;
    NSImage* _dirOpen;
    NSImage* _docImage;
    NSImage* _appImage; // Zipeg.icns
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
    }
    return self;
}

@end
