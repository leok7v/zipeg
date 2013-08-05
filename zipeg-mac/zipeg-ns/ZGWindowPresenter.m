//  THIS IS ALTERED VERSION OF PresentSheetWithEffectWindowController.m
//  CLEARLY MARKED AS SUCH
//  original version was created by
//  Created by Matt Gallagher on 2011/05/05.
//  http://www.cocoawithlove.com/2011/05/presenting-mac-dialog-sheet-with-visual.html
//  Copyright 2011 Matt Gallagher. All rights reserved.
//
//  This software is provided 'as-is', without any express or implied
//  warranty. In no event will the authors be held liable for any damages
//  arising from the use of this software. Permission is granted to anyone to
//  use this software for any purpose, including commercial applications, and to
//  alter it and redistribute it freely, subject to the following restrictions:
//
//  1. The origin of this software must not be misrepresented; you must not
//     claim that you wrote the original software. If you use this software
//     in a product, an acknowledgment in the product documentation would be
//     appreciated but is not required.
//  2. Altered source versions must be plainly marked as such, and must not be
//     misrepresented as being the original software.
//  3. This notice may not be removed or altered from any source
//     distribution.

#import "ZGWindowPresenter.h"
#import <QuartzCore/QuartzCore.h>

@interface ZGWindowPresenter() {
    NSWindow *sheetWindow;
    NSView *blankingView;
    void(^done)(int returnCode);
}

@end

@implementation ZGWindowPresenter

+ (ZGWindowPresenter*) windowPresenterFor: (NSWindow*) w {
    ZGWindowPresenter* it = [ZGWindowPresenter new];
    it.window = w;
    NSView* cv = w.contentView;
//    assert(cv.wantsLayer);
    return it;
}

- (void) presentSheetWithSheet: (id) s done: (void(^)(int returnCode)) block {
    if (sheetWindow) {
        [self dismissSheet: sheetWindow];
    }
    assert(s != null);
    done = block;
    sheetWindow = s;
    
    NSView* cv = _window.contentView;
   // assert(cv.wantsLayer); // should be set at least one dispatch cycle in advance

    CATransition *animation = [CATransition animation];
    animation.type = kCATransitionFade;
    animation.speed = 0.75; // slightly slower
    animation.removedOnCompletion = true;
    [cv.layer addAnimation: animation forKey: @"PresenterAnimationLayer"];
    
    assert(blankingView == null);
    blankingView = [[NSView alloc] initWithFrame: cv.bounds];
    [cv addSubview:blankingView];
    CIFilter *exposureFilter = [CIFilter filterWithName: @"CIExposureAdjust"];
    [exposureFilter setDefaults];
    [exposureFilter setValue:@-1.25 forKey: @"inputEV"];
    CIFilter *saturationFilter = [CIFilter filterWithName: @"CIColorControls"];
    [saturationFilter setDefaults];
    [saturationFilter setValue:@0.35 forKey: @"inputSaturation"];
    CIFilter *gloomFilter = [CIFilter filterWithName: @"CIGloom"];
    [gloomFilter setDefaults];
    [gloomFilter setValue:@0.75 forKey: @"inputIntensity"];
    blankingView.layer.backgroundFilters = @[exposureFilter, saturationFilter, gloomFilter];
    if ([sheetWindow isKindOfClass:[NSAlert class]]) {
        NSAlert* a = (NSAlert*)sheetWindow;
        [a beginSheetModalForWindow: _window modalDelegate: self
                     didEndSelector: @selector(didEndPresentedAlert:returnCode:contextInfo:)
                        contextInfo: null];
    } else {
        [NSApp beginSheet: sheetWindow modalForWindow: _window modalDelegate: self
           didEndSelector: @selector(didEndPresentedAlert:returnCode:contextInfo:)
              contextInfo: null];
    }
    [_window makeKeyAndOrderFront: _window.windowController]; // window.moveToFront
}

- (void) dismissSheet:(id) s {
    CATransition *animation = [CATransition animation];
    animation.delegate = self;
    animation.type = kCATransitionFade;
    animation.speed = 0.75;
    animation.removedOnCompletion = true;
    NSView* cv = _window.contentView;
    timestamp("animation");
    [cv.layer addAnimation: animation forKey: @"PresenterAnimationLayer"];
    [blankingView removeFromSuperview];
    blankingView = null;
    if ([s isKindOfClass:[NSWindow class]]) {
        [NSApp endSheet: s];
        [s orderOut: null];
    }
    if (![s isEqual:sheetWindow]) {
        return;
    }
    sheetWindow = null;
}

- (void) didEndPresentedAlert: (NSAlert*) a returnCode: (NSInteger) rc contextInfo: (void*) c {
    [self dismissSheet: a];
    if (done != null) {
        double delayInSeconds = 0.2;
        dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delayInSeconds * NSEC_PER_SEC));
        dispatch_after(popTime, dispatch_get_main_queue(), ^(void){
            void(^d)(int rc) = done;
            done = null;
            d((int)rc);
        });
// TODO: this fucks up background in outlineView in Regular mode - why?
//        dispatch_async(dispatch_get_main_queue(), ^() {
//            void(^d)(int rc) = done;
//            done = null;
//            d((int)rc);
//        });
    }
}

@end
