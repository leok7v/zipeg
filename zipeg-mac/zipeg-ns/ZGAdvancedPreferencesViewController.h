#import "ZGPreferencesViewControllerProtocol.h"

@interface ZGAdvancedPreferencesViewController : NSViewController <ZGPreferencesViewControllerProtocol>

@property (nonatomic, assign) IBOutlet NSTextField *textField;
@property (assign) IBOutlet NSTableView *tableView;

@end
