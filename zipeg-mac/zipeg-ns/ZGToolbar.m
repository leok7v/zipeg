#import "ZGToolbar.h"
#import "ZGToolbarDelegate.h"

@interface ZGToolbar () {
}

@end


@implementation ZGToolbar


- init {
    self = [super initWithIdentifier: @"ZGToolbarId"];
    if (self != null) {
        alloc_count(self);
        self.showsBaselineSeparator = false;
        self.allowsUserCustomization = true;
        self.autosavesConfiguration = true;
        self.sizeMode = NSToolbarSizeModeSmall;
        self.displayMode = NSToolbarDisplayModeIconAndLabel;
        self.sizeMode = NSToolbarSizeModeRegular;
    }
    return self;
}

- (void) dealloc {
    trace(@"%@", self);
    dealloc_count(self);
}

@end
