#include "c.h"
#import "ZGAppDelegate.h"
#import "ZGErrors.h"

@interface ZGAppDelegate() {
}
@property BOOL applicationHasStarted;
@end

@implementation ZGAppDelegate

- (void)applicationDidFinishLaunching:(NSNotification *) notification {
    [[NSApplication sharedApplication] setPresentationOptions:NSFullScreenWindowMask];
    
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
    NSDocumentController *controller = [NSDocumentController sharedDocumentController];
    NSArray *documents = [controller recentDocumentURLs];
    if ([documents count] > 0) { // If there is a recent document, try to open it.
        NSError *error = null;
        [controller openDocumentWithContentsOfURL:[documents objectAtIndex:0] display:YES error:&error];
        if (error != null) { // If there was no error, then prevent untitled from appearing.
            // TODO: DASHBOARD HERE instead of New Untitled File
            return true;
        }
    }
    return false;
}

- (BOOL) applicationShouldOpenUntitledFile:(NSApplication *) sender {
    // http://www.cocoawithlove.com/2008/05/open-previous-document-on-application.html
    // On startup, when asked to open an untitled file, open the last opened file instead
    if (!_applicationHasStarted) {
        return [self openLastDocument];
    }
    return true;
}

@end
