// based on https://github.com/shpakovski/MASPreferences
// http://blog.shpakovski.com/2011/04/preferences-windows-in-cocoa.html
// MASPreferences is licensed under the BSD license
#import "ZGPreferencesViewController.h"

extern NSString* const kZGPreferencesWindowControllerDidChangeViewNotification;

@interface ZGPreferencesWindowController : NSWindowController <NSToolbarDelegate, NSWindowDelegate>

@property (nonatomic, readonly) NSArray* viewControllers;
@property (nonatomic, readonly) NSUInteger indexOfSelectedController;
@property (nonatomic, readonly) NSViewController <ZGPreferencesViewController>* selectedViewController;
@property (nonatomic, readonly) NSString* title;

- (id) initWithViewControllers: (NSArray*) viewControllers;
- (id) initWithViewControllers: (NSArray*) viewControllers title:(NSString*) title;

- (void) selectControllerAtIndex: (NSUInteger) controllerIndex;

- (IBAction) goNextTab: (id) sender;
- (IBAction) goPreviousTab: (id) sender;

@end
