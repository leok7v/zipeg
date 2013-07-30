#import "ZGApplication.h"
#import "ZGDocument.h"

@implementation ZGApplication

- (void)run {
    [super run];
}

- (void) sendEvent: (NSEvent*) e  {
    // trace(@"%@", e);
    
    // Ctrl+Z will dump useful stats:
    NSUInteger flags = e.modifierFlags & NSDeviceIndependentModifierFlagsMask;
    if( flags == NSControlKeyMask && e.type == NSKeyDown &&
       [e.charactersIgnoringModifiers isEqualToString:@"z"]) {
        NSDocumentController* dc = NSDocumentController.sharedDocumentController;
        NSArray* docs = dc.documents;
        if (docs != null && docs.count > 0) {
            for (int i = 0; i < docs.count; i++) {
                ZGDocument* doc = (ZGDocument*)docs[i];
                if (doc.window != null) {
                    NSLog(@"%@", doc.displayName);
                    dumpViews(doc.window.contentView);
                    NSLog(@"");
                }
            }
        }
        NSLog(@"");
        trace_allocs();
        NSLog(@"");
    }
    [super sendEvent: e];
}

- (void) terminate: (id) sender {
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
