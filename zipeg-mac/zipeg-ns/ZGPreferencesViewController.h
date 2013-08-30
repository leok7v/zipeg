@protocol ZGPreferencesViewController <NSObject>

@optional
- (void) viewWillAppear;
- (void) viewDidDisappear;
- (NSView*) initialKeyView;

@required
@property (nonatomic, readonly) NSString* identifier;
@property (nonatomic, readonly) NSImage*  toolbarItemImage;
@property (nonatomic, readonly) NSString* toolbarItemLabel;

@end
