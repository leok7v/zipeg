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

- (NSString*) identifier {
    return @"GeneralPreferences";
}

- (NSImage*) toolbarItemImage {
    return [NSImage imageNamed:NSImageNamePreferencesGeneral];
}

- (NSString*) toolbarItemLabel {
    return NSLocalizedString(@"General", @"Toolbar item name for the General preference pane");
}

@end
