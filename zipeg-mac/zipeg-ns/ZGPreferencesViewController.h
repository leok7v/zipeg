@protocol ZGPreferencesViewController <NSObject>

@optional
- (void) viewWillAppear;
- (void) viewDidDisappear;
- (NSView*) initialKeyView;

@required
@property (nonatomic, readonly) NSString* ident;
@property (nonatomic, readonly) NSImage*  image;
@property (nonatomic, readonly) NSString* label;

@end
