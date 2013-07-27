#import "ZGApplication.h"


// http://www.cocoawithlove.com/2009/01/demystifying-nsapplication-by.html
// and
// https://developer.apple.com/library/mac/#documentation/cocoa/conceptual/EventOverview/MonitoringEvents/MonitoringEvents.html
/*
    In -init {
    NSObject* eventMonitor = [NSEvent addLocalMonitorForEventsMatchingMask: NSAnyEventMask handler:^(NSEvent *e) {
        return e;
    }];
    }
    in -dealloc {
       [NSEvent removeMonitor:eventMonitor];
    }
*/

/*
FOUNDATION_EXPORT int ZGApplicationMain(int argc, const char **argv) {
    NSDictionary *infoDictionary = [[NSBundle mainBundle] infoDictionary];
    Class principalClass =
    NSClassFromString([infoDictionary objectForKey:@"NSPrincipalClass"]);
    NSApplication *applicationObject = [principalClass sharedApplication];
    
    NSString *mainNibName = [infoDictionary objectForKey:@"NSMainNibFile"];
    NSNib *mainNib = [[NSNib alloc] initWithNibNamed:mainNibName bundle: [NSBundle mainBundle]];
    [mainNib instantiateNibWithOwner:applicationObject topLevelObjects:nil];
    if ([applicationObject respondsToSelector:@selector(run)]) {
        [applicationObject performSelectorOnMainThread: @selector(run) withObject: nil waitUntilDone: YES];
    }
    mainNib = null;
    return 0;
}
*/

@implementation ZGApplication

- (void)run {
    [super run];
}

- (void) sendEvent:(NSEvent*) e  {
    [super sendEvent: e];
}

- (void) terminate: (id) sender {
    [super terminate: sender];
}

@end
