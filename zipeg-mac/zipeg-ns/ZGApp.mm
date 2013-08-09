#import "ZGApp.h"
#import "ZGDocument.h"

static NSWindow* __weak sheet;
static NSWindow* __weak window;

@implementation ZGApp

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

- (void) sendEvent: (NSEvent*) e  {
    // trace(@"%@", e);
    // Ctrl+Z will dump useful stats:
    NSUInteger flags = e.modifierFlags & NSDeviceIndependentModifierFlagsMask;
    if (flags == NSControlKeyMask && e.type == NSKeyDown &&
       [e.charactersIgnoringModifiers isEqualToString:@"z"]) {
        dumpAllViews();
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

- (void) terminate: (id) sender {
    NSLog(@"\nZGApp -terminate\n");
    trace_allocs();
    [super terminate: sender];
}

+ (void) deferedTraceAllocs  {
    // somehow, NSToolbar takes a long time to dealloc in ARC; seems like it is sitting on a timer
    dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(100 * NSEC_PER_MSEC));
    dispatch_after(popTime, dispatch_get_main_queue(), ^(void){
        trace_allocs();
    });
}

@end
