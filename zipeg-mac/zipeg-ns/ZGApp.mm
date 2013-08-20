#import "ZGApp.h"
#import "ZGDocument.h"

static NSWindow* __weak sheet;
static NSWindow* __weak window;

@implementation NSApplication (SheetAdditions)

- (void) beginSheet: (NSWindow*) s modalForWindow: (NSWindow*) w didEndBlock: (void (^)(NSInteger rc)) block {
    [self beginSheet: s
      modalForWindow: w
       modalDelegate: self
      didEndSelector: @selector(blockSheetDidEnd:returnCode:contextInfo:)
         contextInfo: (Block_copy((__bridge void *)block))];
}

- (void) blockSheetDidEnd:(NSWindow*) sheet returnCode: (NSInteger)rc contextInfo: (void*) ctx {
    void (^block)(NSInteger rc) = (__bridge void (^)(NSInteger rc))ctx;
    assert(block != null);
    block(rc);
    Block_release(block);
}

@end


@implementation ZGApp

- (id) init {
    self = [super init];
    if (self != null) {
        alloc_count(self);
    }
    return self;
}

- (void) dealloc {
     dealloc_count(self);
}

+ (void) modalWindowToSheet: (NSWindow*) s for: (NSWindow*) w {
    sheet = s;
    window = w;
}

- (void)run {
    [super run];
}

- (NSInteger) runModalForWindow: (NSWindow*) s {
    if (s == sheet) { // this is a bit of a hack but it works
        [self beginSheet: s
          modalForWindow:(NSWindow*) window
           modalDelegate:self
          didEndSelector:@selector(didEndSelector:) contextInfo: null];
        NSInteger r = [super runModalForWindow: s];
        [NSApp endSheet: s];
        return r;
    } else {
        return [super runModalForWindow: s];
    }
}

- (void) didEndSelector: (id) sender {
    sheet = null;
    window = null;
}


static NSImage* _appIcon;
static NSImage* _appIcon16x16;
static NSImage* _appIcon32x32;

static void loadIcons() {
    if (_appIcon32x32 == null) {
        // http://stackoverflow.com/questions/1359060/how-can-i-load-an-nsimage-representation-of-the-icon-for-my-application
        _appIcon = [NSWorkspace.sharedWorkspace iconForFile: NSBundle.mainBundle.bundlePath].copy;
        _appIcon32x32 = _appIcon.copy;
        _appIcon32x32.size = NSMakeSize(32, 32);
        _appIcon16x16 = _appIcon.copy;
        _appIcon16x16.size = NSMakeSize(16, 16);
    }
}

+ (NSImage*) appIcon {
    loadIcons();
    return _appIcon;
}

+ (NSImage*) appIcon16x16 {
    loadIcons();
    return _appIcon16x16;
}

+ (NSImage*) appIcon32x32 {
    loadIcons();
    return _appIcon32x32;
}

- (void) sendEvent: (NSEvent*) e  {
    // trace(@"%@", e);
    // Ctrl+Z will dump useful stats:
    NSUInteger flags = e.modifierFlags & NSDeviceIndependentModifierFlagsMask;
    if (flags == NSControlKeyMask && e.type == NSKeyDown &&
       [e.charactersIgnoringModifiers isEqualToString:@"z"]) {
        dumpAllViews();
        NSLog(@"\n");
        traceObservers();
        NSLog(@"\n");
        trace_allocs();
        NSLog(@"\n");
    }
    try {
        [super sendEvent: e];
    } catch (...) {
        NSLog(@"EXCEPTION CAUGHT *******************************************");
        NSLog(@"%@", e);
        throw;
    }
}

- (void) exit  {
    traceObservers();
    trace_allocs();
}

- (void) terminate: (id) sender {
    if (self.delegate) {
        NSApplicationTerminateReply r = [self.delegate applicationShouldTerminate: self];
        if (r == NSTerminateNow) {
            [self exit];
            [super terminate: sender];
        }
    } else {
        [self exit];
        [super terminate: sender];
    }
}

+ (void) deferedTraceAllocs  {
    // somehow, NSToolbar takes a long time to dealloc in ARC; seems like it is sitting on a timer
    dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(100 * NSEC_PER_MSEC));
    dispatch_after(popTime, dispatch_get_main_queue(), ^(void){
        traceObservers();
        trace_allocs();
    });
}

#define kUnpackingFolders @"com.zipeg.unpacking.folders"

+ (void) registerUnpackingFolder: (NSString*) path to: (NSString*) dest {
    NSUserDefaults* ud = NSUserDefaults.standardUserDefaults;
    NSDictionary* d = [ud dictionaryForKey: kUnpackingFolders];
    if (d != null) {
        NSMutableDictionary* folders = [NSMutableDictionary dictionaryWithDictionary: d];
        folders[path] = dest;
        [ud setObject: folders forKey: kUnpackingFolders];
    } else {
        [ud setObject: @{ path: dest } forKey: kUnpackingFolders];
    }
    [ud synchronize];
}

+ (void) unregisterUnpackingFolder: (NSString*) path {
    NSUserDefaults* ud = NSUserDefaults.standardUserDefaults;
    NSDictionary* d = [ud dictionaryForKey: kUnpackingFolders];
    if (d != null) {
        NSMutableDictionary* folders = [NSMutableDictionary dictionaryWithDictionary: d];
        [folders removeObjectForKey: path];
        [ud setObject: folders forKey: kUnpackingFolders];
        [ud synchronize];
    }
}

+ (NSMutableDictionary*) allUnpackingFolders {
    NSUserDefaults* ud = NSUserDefaults.standardUserDefaults;
    NSDictionary* d = [ud dictionaryForKey: kUnpackingFolders];
    return d != null ? [NSMutableDictionary dictionaryWithDictionary: d] : null;
}

@end
