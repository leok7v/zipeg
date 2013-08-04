@class ZGDocument;

@interface ZGToolbarDelegate : NSObject<NSToolbarDelegate, NSTextFieldDelegate>

- (id) initWithDocument: (ZGDocument*) doc;

@property (nonatomic, readonly) NSSearchField* searchFieldOutlet;

@end
