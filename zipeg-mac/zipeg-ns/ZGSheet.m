#import "ZGSheet.h"

@interface ZGSheet() {
@public
    NSWindow* __weak _window;
    NSWindow* _sheetWindow;
    NSInteger _rc;
    void (^_done)(int returnCode);
}

@end

@implementation ZGSheet

- (id) initWithWindow: (NSWindow *)w {
    self = [super init];
    if (self) {
        _window = w;
    }
    return self;
}

- (void) begin: (id) s done: (void(^)(int returnCode)) block {
    assert(_sheetWindow == null);
    if (_sheetWindow != null) {
        // user will get confused, the alert returnCode logic will fly out of the window
        @throw @"no more than one sheet at a time";
    }
    assert(s != null);
    _done = block;
    _sheetWindow = s;
    if ([_sheetWindow isKindOfClass: NSAlert.class]) {
        NSAlert* a = (NSAlert*)_sheetWindow;
        [a beginSheetModalForWindow: _window modalDelegate: self
                     didEndSelector: @selector(didEndPresentedAlert:returnCode:contextInfo:)
                        contextInfo: null];
    } else {
        [NSApp beginSheet: _sheetWindow modalForWindow: _window modalDelegate: self
           didEndSelector: @selector(didEndPresentedAlert:returnCode:contextInfo:)
              contextInfo: null];
    }
    [_window makeKeyAndOrderFront: _window.windowController]; // window.moveToFront
}

- (void) end: (id) s {
    assert([s isEqual:_sheetWindow]);
    if (![s isKindOfClass: NSAlert.class]) {
        [NSApp endSheet: s];
        [s orderOut: null];
    } else {
        [NSApp endSheet: ((NSAlert*)s).window];
    }
    _sheetWindow = null;
    if (_done != null) {
        dispatch_async(dispatch_get_main_queue(), ^() {
            void(^d)(int rc) = _done;
            _done = null;
            d((int)_rc);
        });
    }
}

- (void) didEndPresentedAlert: (NSAlert*) a returnCode: (NSInteger) rc contextInfo: (void*) c {
    _rc = rc;
    [self end: a];
}

@end
