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
        self.allowsUserCustomization = true;
        self.autosavesConfiguration = true;
        self.sizeMode = NSToolbarSizeModeSmall;
        self.showsBaselineSeparator = true;
        self.displayMode = NSToolbarDisplayModeIconAndLabel; // NSToolbarDisplayModeIconOnly;
    }
    return self;
}

- (void) dealloc {
    trace(@"%@", self);
    dealloc_count(self);
}


@end
