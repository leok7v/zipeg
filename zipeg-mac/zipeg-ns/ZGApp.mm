#import "ZGApp.h"
#import "ZGDocument.h"
#import "ZGLocalize.h"

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

+ (void) initialize {
    [ZGApp registerApp: false];
    [ZGApp cleanup: false];
//  [ZGLocalize collect];
}

+ (void) registerApp: (BOOL) force {
    timestamp("LSRegisterFSRef");
    FSRef fsref = {0};
    OSStatus oss = FSPathMakeRef((const UInt8*)NSBundle.mainBundle.bundlePath.fileSystemRepresentation, &fsref, NULL);
    if (oss != noErr) {
        console(@"FSPathMakeRef = %d error", oss);
    }
    oss = LSRegisterFSRef(&fsref, force); // force registration of this app for file type associations
    if (oss != noErr) {
        console(@"LSRegisterFSRef = %d error", oss);
    }
    timestamp("LSRegisterFSRef");
}

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

- (void) exit  {
    traceObservers();
    trace_allocs();
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

+ (void) cleanup: (BOOL) sync {
    NSDictionary* uf = ZGApp.allUnpackingFolders.copy;
    if (uf.allKeys.count > 0) {
        dispatch_semaphore_t sema = sync ? dispatch_semaphore_create(0) : null;
        for (NSString* temp in uf.allKeys) {
            if (![NSFileManager.defaultManager fileExistsAtPath: temp]) {
                [ZGApp unregisterUnpackingFolder: temp];
            } else {
                [ZGUtils rmdirsOnBackgroundThread: temp done:^(BOOL b) {
                    if (b) {
                        if (sema != null) {
                            dispatch_semaphore_signal(sema);
                        }
                        if (sync) {
                            // this is direct call from background thread with the hope that
                            // NSUserDefaults are thread safe. At least it is guaranteed that
                            // main thread is blocked at this point.
                            [ZGApp unregisterUnpackingFolder: temp];
                        } else {
                            dispatch_async(dispatch_get_main_queue(), ^{
                                [ZGApp unregisterUnpackingFolder: temp];
                            });
                        }
                    }
                }];
                if (sema != null) {
                    dispatch_semaphore_wait(sema, DISPATCH_TIME_FOREVER);
                }
            }
        }
        if (sema != null) {
            dispatch_release(sema);
        }
    }
}

- (void) terminate: (id) sender {
    NSApplicationTerminateReply r = NSTerminateNow;
    if (self.delegate) {
        r = [self.delegate applicationShouldTerminate: self];
    }
    if (r == NSTerminateNow) {
        self.delegate = null;
        [ZGApp cleanup: true];
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

// because of NSUserDefaults.resetStandardUserDefaults rewriting
// /Users/<user>/Library/Preferences/com.zipeg.zipeg.plist back to defaults
// when user presses [Defaults] button in preferences the unpacking.folders
// are kept separately in the
// /Users/<user>/Library/Preferences/com.zipeg.unpacking.folders.plist

#define kUnpackingFolders @"com.zipeg.unpacking.folders"

+ (void) registerUnpackingFolder: (NSString*) path to: (NSString*) dest {
    NSUserDefaults* ud = NSUserDefaults.standardUserDefaults;
    NSDictionary* d = [ud persistentDomainForName: kUnpackingFolders];
    if (d != null) {
        NSMutableDictionary* folders = [NSMutableDictionary dictionaryWithDictionary: d];
        folders[path] = dest;
        d = folders;
    } else {
        NSDictionary* folders = @{path: dest};
        d = folders;
    }
    [ud setPersistentDomain: d forName: kUnpackingFolders];
    [ud synchronize];
}

+ (void) unregisterUnpackingFolder: (NSString*) path {
    NSUserDefaults* ud = NSUserDefaults.standardUserDefaults;
    NSDictionary* d = [ud persistentDomainForName: kUnpackingFolders];
    if (d != null) {
        NSMutableDictionary* folders = [NSMutableDictionary dictionaryWithDictionary: d];
        [folders removeObjectForKey: path];
        [ud setPersistentDomain: folders forName: kUnpackingFolders];
        [ud synchronize];
    }
}

+ (NSMutableDictionary*) allUnpackingFolders {
    NSUserDefaults* ud = NSUserDefaults.standardUserDefaults;
    NSDictionary* d = [ud persistentDomainForName: kUnpackingFolders];
    return d != null ? [NSMutableDictionary dictionaryWithDictionary: d] : null;
}

@end
