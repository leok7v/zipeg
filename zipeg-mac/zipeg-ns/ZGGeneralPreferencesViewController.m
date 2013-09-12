#import "ZGGeneralPreferencesViewController.h"

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
        v.frameSize = NSMakeSize(width, 360);
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
        y = button(v, y, @"Foo Bar", @" Reset  ",
                     @"Resets all preferences to their original ‘Factory Defaults’ values.");
        self.view = v;
    }
    userDefaultsObserver = addObserver(NSUserDefaultsDidChangeNotification, null,
        ^(NSNotification* n) {
            NSUserDefaults* ud = (NSUserDefaults*)n.object;
            NSDictionary* d = ud.dictionaryRepresentation;
            NSObject* o = d[@"com.zipeg.preferences.encoding"];
            trace("com.zipeg.preferences.encoding=%@", o);
    });
    return self;
}

// TODO: move code below to Prefernces [Reset To Defaults] button:
// if your application supports resetting a subset of the defaults to factory values, you should set those values
// in the shared user defaults controller:
/*
 NSArray* resettableUserDefaultsKeys=@[ @"Value1",@"Value2",@"Value3"];
 NSDictionary* initialValuesDict=[userDefaultsValuesDict dictionaryWithValuesForKeys: resettableUserDefaultsKeys];
 // Set the initial values in the shared user defaults controller
 [NSUserDefaultsController.sharedUserDefaultsController setInitialValues: initialValuesDict];
 // TODO: also see + (void)NSUserDefaults.resetStandardUserDefaults;
 */

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

/*
 
 [theTextField bind:@"value"
 toObject:[NSUserDefaultsController sharedUserDefaultsController]
 withKeyPath:@"values.userName"
 options:[NSDictionary dictionaryWithObject:[NSNumber numberWithBool:YES]
 forKey:@"NSContinuouslyUpdatesValue"]];
 
 https://developer.apple.com/library/mac/documentation/cocoa/conceptual/CocoaBindings/Concepts/NSUserDefaultsController.html

 */


@end
