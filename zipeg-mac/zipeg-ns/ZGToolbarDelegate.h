@class ZGDocument;

@interface ZGToolbarDelegate : NSObject<NSToolbarDelegate, NSTextFieldDelegate, NSOpenSavePanelDelegate>

- (id) initWithDocument: (ZGDocument*) doc;

@property (nonatomic, readonly) NSSearchField* searchFieldOutlet;

@end
