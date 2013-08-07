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

- (void) validateVisibleItems {
    [super validateVisibleItems];
    for (NSToolbarItem* ti in self.visibleItems) {
        NSResponder* r = ti.view;
        while (r != null) {
            if ([r respondsToSelector: @selector(validateToolbarItem:)]) {
                [r performSelector: @selector(validateToolbarItem:) withObject: ti];
            }
            r = r.nextResponder;
        }
    }
}


@end
