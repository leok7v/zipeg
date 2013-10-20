#include "c.h"
#import "ZGAppDelegate.h"
#import "ZGApp.h"
#import "ZGDocument.h"
#import "ZGErrors.h"
#import "ZGBasePreferencesViewController.h"
#import "ZGPreferencesWindowController.h"
#import "ZGGeneralPreferencesViewController.h"
#import "ZGFileTypesPreferencesViewController.h"
#import "ZGAdvancedPreferencesViewController.h"

@interface ZGAppDelegate() {
    ZGPreferencesWindowController* _preferencesWindowController;
    BOOL _applicationHasStarted;
    id __weak _windowWillCloseObserver;
}
// TODO: remove me
@property (nonatomic) NSInteger focusedAdvancedControlIndex;
@end

@interface ZGTestPref  : NSViewController <ZGPreferencesViewControllerProtocol>
@end

@implementation ZGTestPref

- (id) init {
    self = super.init;
    self.view = NSView.new;
    self.view.frame = NSMakeRect(100, 100, 200, 200);
    return self;
}

- (NSString*) ident {
    return @"AdvancedPreferences";
}

- (NSImage *) image {
    return [NSImage imageNamed: NSImageNameAdvanced];
}

- (NSString *) label {
    return NSLocalizedString(@"Test", @"Zipeg Test Preferences");
}

@end

@implementation ZGAppDelegate

+ (void) initialize {
    NSDictionary* defaults = ZGBasePreferencesViewController.defaultPreferences;
    //  [NSUserDefaults.standardUserDefaults registerDefaults: defaults];
    [NSUserDefaultsController.sharedUserDefaultsController setInitialValues: defaults];
}

- (id) init {
    self = [super init];
    if (self != null) {
        alloc_count(self);
    }
    return self;
}

- (void) dealloc {
    _preferencesWindowController = null;
    dealloc_count(self);
}

// TODO: remove me
NSString* const kFocusedAdvancedControlIndex = @"FocusedAdvancedControlIndex";

- (NSInteger)focusedAdvancedControlIndex {
    return [[NSUserDefaults standardUserDefaults] integerForKey: kFocusedAdvancedControlIndex];
}

- (void)setFocusedAdvancedControlIndex: (NSInteger) focusedAdvancedControlIndex {
    [[NSUserDefaults standardUserDefaults] setInteger:focusedAdvancedControlIndex forKey: kFocusedAdvancedControlIndex];
}

- (IBAction) preferences: (id) sender {
    if (_preferencesWindowController == null) {
/*
        NSArray* controllers = @[ZGGeneralPreferencesViewController.new,
                                 ZGFileTypesPreferencesViewController.new,
                                 ZGAdvancedPreferencesViewController.new];
*/
        NSArray* controllers = @[ZGTestPref.new];
        _preferencesWindowController = [ZGPreferencesWindowController.alloc initWithViewControllers: controllers title: @""];
    }
    if (_preferencesWindowController != null) {
        [_preferencesWindowController showWindow: self];
        trace("%@", _preferencesWindowController.window);
        _windowWillCloseObserver = addObserver(NSWindowWillCloseNotification, _preferencesWindowController.window,
            ^(NSNotification* n) {
                _windowWillCloseObserver = removeObserver(_windowWillCloseObserver);
                trace("%@", _preferencesWindowController.window);
                _preferencesWindowController.window = null;
                _preferencesWindowController = null;
            });
    }
}

- (IBAction) welcome: (id) sender {
    trace(@"welcome");
}

- (void) applicationDidFinishLaunching:(NSNotification *) notification {
    NSApplication.sharedApplication.presentationOptions = NSFullScreenWindowMask;
#ifdef DEBUG
    NSUserDefaults* ud = NSUserDefaults.standardUserDefaults;
    [ud setObject:@[@"ru", @"en", @"es"] forKey: @"AppleLanguages"];
    [ud synchronize];
#else
    NSUserDefaults* ud = NSUserDefaults.standardUserDefaults;
    [ud setObject:@[@"en", @"ru"] forKey: @"AppleLanguages"];
    [ud synchronize];
#endif
    ZGErrorsInit();
    _applicationHasStarted = true;
}

- (BOOL) applicationShouldTerminateAfterLastWindowClosed: (NSApplication*) app {
    return true;
}

- (BOOL) openLastDocument {
#ifdef DEBUG
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
#endif
    return false;
}

// TODO: for multi-document model below has no effect. Looks like we need Document controller override :(
// to distingish between Pro and non-Pro

// http://stackoverflow.com/questions/7564290/why-isnt-applicationshouldopenuntitledfile-being-called

- (BOOL) applicationShouldOpenUntitledFile: (NSApplication*) app {
#ifdef DEBUG
    // http://www.cocoawithlove.com/2008/05/open-previous-document-on-application.html
    // On startup, when asked to open an untitled file, open the last opened file instead
    if (!_applicationHasStarted) {
        return [self openLastDocument];
    }
    return true;
#else
    return false; // TODO: might be true in Pro
#endif
}

- (BOOL) applicationOpenUntitledFile: (NSApplication*) app {
#ifndef DEBUG
    return false;
#else
    return true;
#endif
}

- (void) cancelAll {
    NSArray* docs = ((NSDocumentController*)NSDocumentController.sharedDocumentController).documents;
    for (int i = 0; i < docs.count; i++) {
        ZGDocument* doc = (ZGDocument*)docs[i];
        [doc cancelAll];
    }
}


- (NSApplicationTerminateReply) applicationShouldTerminate: (NSApplication*) app {
    ZGDocument* last = null;
    NSArray* docs = ((NSDocumentController*)NSDocumentController.sharedDocumentController).documents;
    int cannotClose = 0;
    if (docs != null && docs.count > 0) {
        for (int i = 0; i < docs.count; i++) {
            ZGDocument* doc = (ZGDocument*)docs[i];
            if (![doc documentCanClose]) {
                cannotClose++;
                last = doc;
            }
        }
    }
    if (cannotClose == 0) {
        return NSTerminateNow;
    }
    NSString* message = @"Some operations are still in progress.\n"
                         "Do you want to stop all the operations and quit Zipeg?";
    NSString* info = @"(terminating unfinished operations may leave\n"
                      "behind incomplete/corrupted folders and files)\n";
    NSString* stop = @"Stop and Quit";
    NSString* keep = @"Keep Going";
    NSInteger rc = NSAlertErrorReturn;
    void __block (^done)(NSInteger rc) = ^(NSInteger rc) {
        NSApplicationTerminateReply r = rc == NSAlertFirstButtonReturn ? NSTerminateNow : NSTerminateCancel;
        if (r == NSTerminateNow) {
            [self cancelAll];
        }
        [NSApp replyToApplicationShouldTerminate: r];
    };
    if (cannotClose == 1) {
        // the only document can present alert inside the _alerts sheet:
        [last alertModalSheet: message
                buttons: @[stop, keep]
                     tooltips: null
                         info: info
                   suppressed: null
                         done: done];
    } else {
        dispatch_async(dispatch_get_main_queue(), ^{
            // this alert panel cannot be presented in a particular window's sheet
            // because we have multiple documents running operations at the same time:
            NSAlert* a = NSAlert.new;
            a.messageText = message;
            [a addButtonWithTitle: stop];
            [a addButtonWithTitle: keep];
            a.informativeText = info;
            NSInteger rc = [a runModal];
            done(rc);
        });
        return NSTerminateCancel;
    }
    if (rc == NSAlertDefaultReturn) {
        for (int i = 0; i < docs.count; i++) {
            ZGDocument* doc = (ZGDocument*)docs[i];
            [doc cancelAll];
        }
        return NSTerminateNow;
    } else {
        return NSTerminateCancel;
    }
    // NSTerminateLater/NSTerminateCancel: the app itself will be responsible for later termination
    // OSX will just gray out (disable) App Quit and will stay this way forever... unless you use:
    // [NSApp replyToApplicationShouldTerminate: r];
    // see: http://stackoverflow.com/questions/10224141/how-to-handle-cocoa-application-termination-properly
}

@end
