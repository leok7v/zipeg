#import "ZGPreferencesViewControllerProtocol.h"

@interface ZGFileTypesPreferencesViewController : NSViewController <ZGPreferencesViewControllerProtocol>

@property (nonatomic, assign) IBOutlet NSTextField *textField;
@property (assign) IBOutlet NSTableView *tableView;

@end
