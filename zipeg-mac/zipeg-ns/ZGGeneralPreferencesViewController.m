#import "ZGGeneralPreferencesViewController.h"
#import "ZGAppDelegate.h"

@interface ZGGeneralPreferencesViewController() {
    id userDefaultsObserver;
}
@end

@implementation ZGGeneralPreferencesViewController

- (id) init {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        NSView* v = NSView.new;
        v.autoresizesSubviews = true;
        v.frameSize = NSMakeSize(width, 300);
        NSFont* font = ZGBasePreferencesViewController.font;
        CGFloat y = v.frame.size.height - font.boundingRectForFont.size.height;
        y = checkBox(v, y, @"Show Welcome:", @" when Zipeg starts", @"",
                     @"com.zipeg.preferences.showWelcome");
        y = checkBox(v, y, @"After Unpack:", @" show unpacked items in Finder", null,
                     @"com.zipeg.preferences.showInFinder");
        y = checkBox(v, y, @"Archive window:", @" close after unpack", null,
                     @"com.zipeg.preferences.closeAfterUnpack");
        y = checkBox(v, y, @"Automatically open:", @" nested archives",
                     @"If archive contains exactly one item and it happens to be ‘nested’ "
                     @"archive - unpack it to temporary folder and reopen in the same window.",
                     @"com.zipeg.preferences.openNested");
        y = checkBox(v, y, @"Alerts:", @" Show All",
                     @"If this box is unchecked you may have suppressed some alerts. "
                     @"Checkmark this option to restore all hidden alerts.",
                     @"com.zipeg.preferences.allAlerts");
        y = checkBox(v, y, @"Play:", @" sounds",
                     @"Play audible sounds on successfull unpack completion or error. ",
                     @"com.zipeg.preferences.playSounds");
        y = button(v, y, @"Reset to: ", @"Defaults ",
                     @"Resets all preferences to their original ‘Factory Defaults’ values.",
                     self, @selector(resetToDefaults));
        self.view = v;
    }
    userDefaultsObserver = addObserver(NSUserDefaultsDidChangeNotification, null,
        ^(NSNotification* n) {
            NSUserDefaults* ud = (NSUserDefaults*)n.object;
            NSDictionary* d = ud.dictionaryRepresentation;
            NSArray* a = d.allKeys.copy;
            for (NSString* k in a) {
                if ([k hasPrefix:@"com.zipeg.preferences.suppress."]) {
                    [ud removeObjectForKey: k];
                }
            }
//          NSObject* o = d[@"com.zipeg.preferences.encoding"];
//          trace("com.zipeg.preferences.encoding=%@", o);
    });
    return self;
}

- (void) resetToDefaults {
    trace("");
    [NSUserDefaultsController.sharedUserDefaultsController revertToInitialValues: self];
}

- (void) dealloc {
    dealloc_count(self);
    removeObserver(userDefaultsObserver);
}

- (NSString*) ident {
    return @"GeneralPreferences";
}

- (NSImage*) image {
    return [NSImage imageNamed: NSImageNamePreferencesGeneral];
}

- (NSString*) label {
    return NSLocalizedString(@"General", @"Zipeg General Preferences");
}

// TODO: for the status bar:
//     c.backgroundStyle = NSBackgroundStyleRaised;
// http://whomwah.com/2009/03/11/replicating-apples-embossed-text-in-a-cocoa-app/

@end
