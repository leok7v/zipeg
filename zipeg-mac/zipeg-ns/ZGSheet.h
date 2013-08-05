#import <Cocoa/Cocoa.h>

@interface ZGSheet : NSObject

- (id) initWithWindow: (NSWindow*) w;
- (void) begin: (id) sheet done: (void(^)(int returnCode)) block;

@end
