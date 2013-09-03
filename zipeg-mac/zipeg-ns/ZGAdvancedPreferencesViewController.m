#import "ZGAdvancedPreferencesViewController.h"

@interface ZGAdvancedPreferencesViewController() {
    NSTextField *_textField;
    NSTableView *_tableView;
}
@end

@implementation ZGAdvancedPreferencesViewController

@synthesize textField;
@synthesize tableView;

- (id)init {
    self = [super initWithNibName: @"ZGAdvancedPreferencesView" bundle:nil];
    if (self != null) {
        alloc_count(self);
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (NSString*) ident {
    return @"AdvancedPreferences";
}

- (NSImage *) image {
    return [NSImage imageNamed: NSImageNameAdvanced];
}

- (NSString *) label {
    return NSLocalizedString(@"Advanced", @"Zipeg Advanced Preferences");
}

- (NSView *) initialKeyView {
    NSInteger focusedControlIndex = [[NSApp valueForKeyPath: @"delegate.focusedAdvancedControlIndex"] integerValue];
    return (focusedControlIndex == 0 ? self.textField : self.tableView);
}

@end
