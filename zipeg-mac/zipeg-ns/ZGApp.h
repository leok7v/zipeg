

@interface NSApplication (SheetAdditions)

- (void) beginSheet: (NSWindow*) s modalForWindow: (NSWindow*) w didEndBlock: (void (^)(NSInteger rc)) block;

@end

@interface ZGApp : NSApplication

+ (void) deferedTraceAllocs;
+ (void) modalWindowToSheet: (NSWindow*) sheet for: (NSWindow*) window;
+ (NSImage*) appIcon;
+ (NSImage*) appIcon16x16;
+ (NSImage*) appIcon32x32;

/* When Zipeg unpacks the folder that does not exist it generates temporary
   folder "../.zipeg.<pid>.<nanotime>destination/" in destination location.
   This folder will be moved to the true "destination" path when unpacking is
   complete. However if Zipeg dies during unpacking the temp folders will polute
   user's disk space. To avoid it Zipeg checks "unfinished" folders on startup
   and removes them.
 */

+ (void) registerUnpackingFolder: (NSString*) path to: (NSString*) destination;
+ (void) unregisterUnpackingFolder: (NSString*) path;
+ (NSMutableDictionary*) allUnpackingFolders;

@end
