#import "ZGApp.h"
#import "ZGDocument.h"

@implementation ZGApp

- (void)run {
    [super run];
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
