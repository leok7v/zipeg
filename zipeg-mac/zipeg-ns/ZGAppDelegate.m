#include "c.h"
#import "ZGAppDelegate.h"
#import "ZGApp.h"
#import "ZGDocument.h"
#import "ZGErrors.h"
#import "ZGWelcomeWindowController.h"
#import "ZGBasePreferencesViewController.h"
#import "ZGPreferencesWindowController.h"
#import "ZGGeneralPreferencesViewController.h"
#import "ZGFileTypesPreferencesViewController.h"
#import "ZGAdvancedPreferencesViewController.h"

@interface ZGAppDelegate() {
    ZGPreferencesWindowController* _preferencesWindowController;
    ZGWelcomeWindowController* _welcomeWindowController;
    BOOL _applicationHasStarted;
    id __weak _preferencesWindowWillCloseObserver;
    id __weak _welcomeWindowWillCloseObserver;
}
// TODO: remove me
@property (nonatomic) NSInteger focusedAdvancedControlIndex;
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
    _preferencesWindowWillCloseObserver = removeObserver(_preferencesWindowWillCloseObserver);
    _welcomeWindowWillCloseObserver = removeObserver(_welcomeWindowWillCloseObserver);
    _preferencesWindowController = null;
    _welcomeWindowController = null;
    dealloc_count(self);
}

// TODO: remove me
NSString* const kFocusedAdvancedControlIndex = @"FocusedAdvancedControlIndex";

- (NSInteger)focusedAdvancedControlIndex {
    return [NSUserDefaults.standardUserDefaults integerForKey: kFocusedAdvancedControlIndex];
}

- (void)setFocusedAdvancedControlIndex: (NSInteger) focusedAdvancedControlIndex {
    [NSUserDefaults.standardUserDefaults setInteger:focusedAdvancedControlIndex forKey: kFocusedAdvancedControlIndex];
}

- (IBAction) preferences: (id) sender {
    if (_preferencesWindowController == null) {
        NSArray* controllers = @[ZGGeneralPreferencesViewController.new,
                                 ZGFileTypesPreferencesViewController.new,
                                 ZGAdvancedPreferencesViewController.new];
        _preferencesWindowController = [ZGPreferencesWindowController.alloc
                                        initWithViewControllers: controllers
                                        title: @""];
    }
    if (_preferencesWindowController != null) {
        [_preferencesWindowController showWindow: self];
        _preferencesWindowWillCloseObserver = addObserver(NSWindowWillCloseNotification,
            _preferencesWindowController.window,
            ^(NSNotification* n) {
                _preferencesWindowWillCloseObserver = removeObserver(_preferencesWindowWillCloseObserver);
                _preferencesWindowController.window = null;
                _preferencesWindowController = null;
            });
    }
}

- (IBAction) welcome: (id) sender {
    if (_welcomeWindowController == null) {
        if (_welcomeWindowController == null) {
            _welcomeWindowController = ZGWelcomeWindowController.new;
        }
        if (_welcomeWindowController != null) {
            [_welcomeWindowController showWindow: self];
            _welcomeWindowWillCloseObserver = addObserver(NSWindowWillCloseNotification,
                _welcomeWindowController.window,
                ^(NSNotification* n) {
                    _welcomeWindowWillCloseObserver = removeObserver(_welcomeWindowWillCloseObserver);
                    _welcomeWindowController.window = null;
                    _welcomeWindowController = null;
                });
        }
    }
}

- (void) dismissWelcome {
    if (_welcomeWindowController != null) {
        [_welcomeWindowController.window close];
    }
}


- (void) applicationDidFinishLaunching: (NSNotification*) n {
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
    if ([NSApp windows].count == 0) {
        [self welcome: null];
    }
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

- (BOOL) applicationShouldOpenUntitledFile: (NSApplication*) app {
    return false;
}

- (BOOL) applicationOpenUntitledFile: (NSApplication*) app {
    return false;
}

- (void) cancelAll {
    NSArray* docs = ((NSDocumentController*)NSDocumentController.sharedDocumentController).documents;
    for (int i = 0; i < docs.count; i++) {
        ZGDocument* doc = (ZGDocument*)docs[i];
        [doc cancelAll];
    }
}

- (void) terminateLater {
    if ([NSApp windows].count == 0) {
        [NSApp terminate: self];
    } else {
        dispatch_async(dispatch_get_main_queue(), ^{ [self terminateLater]; });
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
            } else {
                [doc close];
            }
        }
    }
    if (cannotClose == 0) {
        if ([NSApp windows].count == 0) {
            return NSTerminateNow;
        } else {
            [[NSApp windows] makeObjectsPerformSelector: @selector(close)];
            dispatch_async(dispatch_get_main_queue(), ^{ [self terminateLater]; });
            return NSTerminateLater;
        }
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
}

@end
