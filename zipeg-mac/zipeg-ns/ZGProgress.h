@interface ZGProgress : NSWindow

@property float progress;

- (void) begin: (NSWindow*) w;
- (void) end;

@end
