#import "ZGFileTypesPreferencesViewController.h"

@interface ZGFileTypesPreferencesViewController() {
    NSTextField *_textField;
    NSTableView *_tableView;
}
@end

@implementation ZGFileTypesPreferencesViewController

@synthesize textField;
@synthesize tableView;

- (id)init {
    self = [super initWithNibName: @"ZGAdvancedPreferencesView" bundle: null];
    if (self != null) {
        alloc_count(self);
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (NSString*) ident {
    return @"FileTypesPreferences";
}

- (NSImage *) image {
    return [NSImage imageNamed: NSImageNameMultipleDocuments];
}

- (NSString *) label {
    return NSLocalizedString(@"File Types", @"Zipeg File Types Preferences");
}

- (NSView *) initialKeyView {
    NSInteger focusedControlIndex = [[NSApp valueForKeyPath: @"delegate.focusedAdvancedControlIndex"] integerValue];
    return (focusedControlIndex == 0 ? self.textField : self.tableView);
}

@end
