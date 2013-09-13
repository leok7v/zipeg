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
        v.frameSize = NSMakeSize(width, 390);
        NSFont* font = ZGBasePreferencesViewController.font;
        CGFloat y = v.frame.size.height - font.boundingRectForFont.size.height;
        y = checkBox(v, y, @"Show Welcome:", @" when Zipeg starts", @"",
                     @"com.zipeg.preferences.showWelcome");
        y = checkBox(v, y, @"After Unpack:", @" show unpacked items in Finder", null,
                     @"com.zipeg.preferences.showInFinder");
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
        y = checkBox(v, y, @"Show:", @" passwords",
                     @"Password characters will be visible while you type.",
                     @"com.zipeg.preferences.showPasswords");
        y = radioButtons(v, y, @"What to unpack:", @[@" Whole Archive", @" Selection", @" Ask"],
                         @"com.zipeg.preferences.unpackSelection");
        y = button(v, y - 4, @"Reset to: ", @"Defaults ",
                     @"Resets all preferences to their original ‘Factory Defaults’ values.",
                     self, @selector(resetToDefaults));
        self.view = v;
    }
    userDefaultsObserver = addObserver(NSUserDefaultsDidChangeNotification, null,
        ^(NSNotification* n) {
            [self allAlerts];
//          NSUserDefaults* ud = (NSUserDefaults*)n.object;
//          NSDictionary* d = ud.dictionaryRepresentation;
//          NSObject* o = d[@"com.zipeg.preferences.encoding"];
//          trace("com.zipeg.preferences.encoding=%@", o);
    });
    return self;
}

- (void) resetToDefaults {
    [NSUserDefaultsController.sharedUserDefaultsController revertToInitialValues: self];
    [self allAlerts];
}

- (void) allAlerts {
    NSUserDefaults* ud = NSUserDefaults.standardUserDefaults;
    NSDictionary* d = ud.dictionaryRepresentation;
    if (isEqual(d[@"com.zipeg.preferences.allAlerts"], @true)) {
        NSArray* a = d.allKeys.copy;
        for (NSString* k in a) {
            if ([k hasPrefix:@"com.zipeg.preferences.suppress."]) {
                [ud removeObjectForKey: k];
            }
        }
    }
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

@end
