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
        self.displayMode = NSToolbarDisplayModeIconOnly;
    }
    return self;
}

- (void) dealloc {
    trace(@"%@", self);
    dealloc_count(self);
}


@end
