#import <Cocoa/Cocoa.h>

enum {
    kWindowMinWidth = 720,
    kWindowMinHeight = 480
};

@interface ZGWindowController : NSWindowController<NSWindowDelegate>

@end

/* see:
https://developer.apple.com/library/mac/#documentation/DataManagement/Conceptual/DocBasedAppProgrammingGuideForOSX/KeyObjects/KeyObjects.html
 "You Should Subclass NSWindowController" says:
 "... The NSWindowController subclass instance should be the File’s Owner for the nib file 
  because that creates better separation between the view-related logic and the model-related logic..."
*/