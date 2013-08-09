
@interface ZGApp : NSApplication

+ (void) deferedTraceAllocs;
+ (void) modalWindowToSheet: (NSWindow*) sheet for: (NSWindow*) window;
+ (NSImage*) appIcon;
+ (NSImage*) appIcon16x16;
+ (NSImage*) appIcon32x32;

@end

