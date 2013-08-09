
@interface ZGApp : NSApplication

+ (void) deferedTraceAllocs;
+ (void) modalWindowToSheet: (NSWindow*) sheet for: (NSWindow*) window;

@end

