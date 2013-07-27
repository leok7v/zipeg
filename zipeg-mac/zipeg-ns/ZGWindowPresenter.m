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
    id delegate;
    SEL selector;
    void* context;
}

@end

@implementation ZGWindowPresenter

+ (ZGWindowPresenter*) windowPresenterFor: (NSWindow*) w {
    ZGWindowPresenter* it = [ZGWindowPresenter new];
    it.window = w;
    return it;
}

- (void) presentSheetWithSheet: (id) s delegate:(id) d didEndSelector:(SEL) sel contextInfo: (void*) ctx {
    if (sheetWindow) {
        [self dismissSheet: sheetWindow];
    }
    assert(s != null);
    assert(d != null);
    assert(sel != null);
    sheetWindow = s;
    delegate = d;
    selector = sel;
    context = ctx;
    CATransition *animation = [CATransition animation];
    animation.type = kCATransitionFade;
    NSView* cv = _window.contentView;
    cv.wantsLayer = true;
    [cv.layer addAnimation:animation forKey:@"layerAnimation"];
    
    //trace(@"%@", NSStringFromRect(cv.bounds));
    if (blankingView != null) {
        blankingView.frame = cv.bounds;
    } else {
        blankingView = [[NSView alloc] initWithFrame:cv.bounds];
        CIFilter *exposureFilter = [CIFilter filterWithName:@"CIExposureAdjust"];
        [exposureFilter setDefaults];
        [exposureFilter setValue:@-1.25 forKey:@"inputEV"];
        CIFilter *saturationFilter = [CIFilter filterWithName:@"CIColorControls"];
        [saturationFilter setDefaults];
        [saturationFilter setValue:@0.35 forKey:@"inputSaturation"];
        CIFilter *gloomFilter = [CIFilter filterWithName:@"CIGloom"];
        [gloomFilter setDefaults];
        [gloomFilter setValue:@0.75 forKey:@"inputIntensity"];
        blankingView.wantsLayer = true;
        blankingView.layer.backgroundFilters = @[exposureFilter, saturationFilter, gloomFilter];
        //trace(@"%@", blankingView.layer);
        //trace(@"%@", blankingView.layer.backgroundFilters);
    }
    [cv addSubview:blankingView positioned:NSWindowAbove relativeTo: null];
    
    if ([sheetWindow isKindOfClass:[NSAlert class]]) {
        NSAlert* a = (NSAlert*)sheetWindow;
        [a beginSheetModalForWindow:_window modalDelegate: self
         didEndSelector: @selector(didEndPresentedAlert:returnCode:contextInfo:)
         contextInfo: context];
    }
    else {
        [NSApplication.sharedApplication
         beginSheet:sheetWindow modalForWindow:_window modalDelegate:self
         didEndSelector: @selector(didEndPresentedAlert:returnCode:contextInfo:)
         contextInfo: context];
    }
    [_window makeKeyAndOrderFront:_window.windowController]; // window.moveToFront
    //trace(@"%@", NSStringFromRect([blankingView bounds]));
}

- (void) dismissSheet:(id) s {
    if ([s isKindOfClass:[NSWindow class]]) {
        [[NSApplication sharedApplication] endSheet:s];
        [s orderOut:null];
    }
    if (![s isEqual:sheetWindow]) {
        return;
    }
    sheetWindow = null;
    CATransition *animation = [CATransition animation];
    animation.type = kCATransitionFade;
    NSView* cv = _window.contentView;
    [cv.layer addAnimation:animation forKey:@"layerAnimation"];
    [blankingView removeFromSuperview];
}

- (void) didEndPresentedAlert: (NSAlert*) a returnCode: (NSInteger)rc contextInfo: (void*) ctx {
    [self dismissSheet: a];
    if (delegate != null && selector != null) {
        NSMethodSignature* signature = [delegate methodSignatureForSelector: selector];
        NSInvocation* invocation = [NSInvocation invocationWithMethodSignature: signature];
        NSUInteger argumentCount = signature.numberOfArguments - 2;
        assert(argumentCount == 3);
        invocation.target = delegate;
        invocation.selector = selector;
        [invocation setArgument: &a atIndex:2];
        [invocation setArgument: &rc atIndex:3];
        [invocation setArgument: &ctx atIndex:4];
        [invocation invoke];
    }
    delegate = null;
    selector = null;
}

@end
