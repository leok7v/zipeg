#import "ZGPreferencesViewControllerProtocol.h"

@interface ZGBasePreferencesViewController : NSViewController <ZGPreferencesViewControllerProtocol>

+ (NSDictionary*) defaultPreferences;

enum {
    width = 540,
    middle = 160,
    margin = 15
};

+ (NSFont*) font;

NSButton* createCheckBox(NSView* parent, CGFloat* y, NSString* label, NSString* checkbox, NSString* extra, NSString* prefs);

CGFloat labelNoteAndExtra(NSView* parent, CGFloat y, NSString* label, NSString* note, NSString* extra);
CGFloat checkBox(NSView* parent, CGFloat y, NSString* label, NSString* checkbox, NSString* extra, NSString* prefs);
CGFloat radioButtons(NSView* parent, CGFloat y, NSString* label, NSArray* texts, NSString* prefs);
CGFloat comboBox(NSView* parent, CGFloat y, NSString* label, NSArray* texts, CGFloat width, NSString* prefs);
CGFloat popUpButton(NSView* parent, CGFloat y, NSString* label, NSArray* texts, NSArray* tags, NSString* prefs);
CGFloat button(NSView* parent, CGFloat y, NSString* label, NSString* title, NSString* after, NSString* extra, id target, SEL sel);

@end

@interface ZGKeyButton : NSButton @end
@interface ZGKeyPopUpButton : NSPopUpButton @end
@interface ZGKeyComboBox : NSComboBox @end
@interface ZGKeyMatrix : NSMatrix @end

