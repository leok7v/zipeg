#import "ZGSplitViewDelegate.h"
#import "ZGImageAndTextCell.h"

@interface ZGSplitViewDelegate () {
}
@property (weak) ZGDocument* document;
@end

@implementation ZGSplitViewDelegate

- (id)initWithDocument:(ZGDocument*) document {
    self = [super init];
    if (self) {
        _document = document;
    }
    return self;
}

- (void)dealloc {
//    trace(@"");
}

- (BOOL)splitView:(NSSplitView *)splitView canCollapseSubview:(NSView *)subview {
    return false;
}


- (BOOL)splitView:(NSSplitView *)splitView shouldCollapseSubview:(NSView *)subview forDoubleClickOnDividerAtIndex:(NSInteger)dividerIndex NS_AVAILABLE_MAC(10_5) {
    return false;
}


- (CGFloat)splitView:(NSSplitView *)splitView constrainMinCoordinate:(CGFloat)proposedMinimumPosition ofSubviewAt:(NSInteger)dividerIndex {
    return proposedMinimumPosition + 120;
}

- (CGFloat)splitView:(NSSplitView *)splitView constrainMaxCoordinate:(CGFloat)proposedMaximumPosition ofSubviewAt:(NSInteger)dividerIndex {
    return proposedMaximumPosition - 120;
}

- (void)splitViewDidResizeSubviews:(NSNotification *)notification {
    
    [_document sizeOutlineViewToContents];
}

/*
 
 - (void)splitViewWillResizeSubviews:(NSNotification *)notification {
 }
 
 
 - (void)splitView:(NSSplitView *)splitView resizeSubviewsWithOldSize:(NSSize)oldSize {
 NSLog(@"splitView resizeSubviewsWithOldSize(%@)", NSStringFromSize(oldSize));
 }
 
 - (CGFloat)splitView:(NSSplitView *)splitView constrainSplitPosition:(CGFloat)proposedPosition ofSubviewAt:(NSInteger)dividerIndex {
 }
 
 
 
 - (BOOL)splitView:(NSSplitView *)splitView shouldAdjustSizeOfSubview:(NSView *)view NS_AVAILABLE_MAC(10_6) {
 }
 
 - (BOOL)splitView:(NSSplitView *)splitView shouldHideDividerAtIndex:(NSInteger)dividerIndex NS_AVAILABLE_MAC(10_5) {
 
 }
 
 - (NSRect)splitView:(NSSplitView *)splitView effectiveRect:(NSRect)proposedEffectiveRect forDrawnRect:(NSRect)drawnRect ofDividerAtIndex:(NSInteger)dividerIndex NS_AVAILABLE_MAC(10_5) {
 }
 
 - (NSRect)splitView:(NSSplitView *)splitView additionalEffectiveRectOfDividerAtIndex:(NSInteger)dividerIndex NS_AVAILABLE_MAC(10_5) {
 }
 
 
 - (void)splitViewWillResizeSubviews:(NSNotification *)notification {
 }
 
 - (void)splitViewDidResizeSubviews:(NSNotification *)notification {
 }
 
 */

@end
