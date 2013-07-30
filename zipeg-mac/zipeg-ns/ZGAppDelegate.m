#include "c.h"
#import "ZGAppDelegate.h"
#import "ZGDocument.h"
#import "ZGErrors.h"

@interface ZGAppDelegate() {
}
@property BOOL applicationHasStarted;
@end

@implementation ZGAppDelegate

- (void)applicationDidFinishLaunching:(NSNotification *) notification {
    NSApplication.sharedApplication.presentationOptions = NSFullScreenWindowMask;
    NSUserDefaults* ud = NSUserDefaults.standardUserDefaults;
    [ud setObject:@[@"ru", @"en", @"es"] forKey:@"AppleLanguages"];
    [ud synchronize];
    ZGErrorsInit();
    _applicationHasStarted = true;
}

- (BOOL) applicationShouldTerminateAfterLastWindowClosed:(NSApplication *) theApplication {
    return YES;
}

- (BOOL) openLastDocument {
    NSDocumentController* dc = NSDocumentController.sharedDocumentController;
    NSArray* rds = dc.recentDocumentURLs;
    if (rds != null && rds.count > 0) { // If there is a recent document, try to open it.
        NSError *error = null;
        [dc openDocumentWithContentsOfURL: rds[0] display: true error: &error];
        if (error != null) {
            // If there was no error, then prevent untitled from appearing.
            // TODO: DASHBOARD HERE instead of New Untitled File
            return true;
        }
    }
    return false;
}

// http://stackoverflow.com/questions/7564290/why-isnt-applicationshouldopenuntitledfile-being-called

- (BOOL) applicationShouldOpenUntitledFile:(NSApplication *) sender {
    // http://www.cocoawithlove.com/2008/05/open-previous-document-on-application.html
    // On startup, when asked to open an untitled file, open the last opened file instead
    if (!_applicationHasStarted) {
        return [self openLastDocument];
    }
    return true;
}

- (NSApplicationTerminateReply)applicationShouldTerminate:(NSApplication *)sender {
/*
 NSTerminateCancel = 0,
 NSTerminateNow = 1,
 NSTerminateLater = 2
 */
    NSDocumentController* dc = NSDocumentController.sharedDocumentController;
    NSArray* docs = dc.documents;
    if (docs != null && docs.count > 0) {
        for (int i = 0; i < docs.count; i++) {
            ZGDocument* doc = (ZGDocument*)docs[i];
            if (![doc documentCanClose]) {
                return NSTerminateCancel;
            }
        }
    }
    return NSTerminateNow;
    // NSTerminateLater: the app itself will be responsible for later termination
    // OSX will just gray out (disable) App Quit and will stay this way forever...
}

@end
