#import "ZGGeneralPreferencesViewController.h"

@implementation ZGGeneralPreferencesViewController

- (id) init {
    self = [super initWithNibName: @"ZGGeneralPreferencesView" bundle: null];
    if (self != null) {
        alloc_count(self);
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
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
