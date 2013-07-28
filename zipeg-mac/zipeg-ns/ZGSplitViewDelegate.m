#import "ZGSplitViewDelegate.h"
#import "ZGTableViewDelegate.h"
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

/* TODO: this does not work. the left pane sometimes corrupted and not painted:
 
- (BOOL)splitView:(NSSplitView *)splitView canCollapseSubview:(NSView *)subview {
    return true; // to ensure that forDoubleClickOnDividerAtIndex will be called
}


- (BOOL)splitView:(NSSplitView *)splitView shouldCollapseSubview:(NSView *)subview forDoubleClickOnDividerAtIndex:(NSInteger)dividerIndex NS_AVAILABLE_MAC(10_5) {
    NSSize s = [ZGTableViewDelegate minMaxVisibleColumnContentSize: _document.outlineView columnIndex:0];
    float left = s.width;
    float right = 0;
    for (int i = 0; i < _document.tableView.tableColumns.count; i++) {
        s = [ZGTableViewDelegate minMaxVisibleColumnContentSize: _document.tableView columnIndex:i];
        trace(@"minMaxVisibleColumnContentSize[%d]=%@", i, NSStringFromSize(s));
        right += s.width;
    }
    trace(@"left=%f right=%f", left, right);
    float w = splitView.frame.size.width;
    float t = left + right;
    left = w * left / t;
    right = w - left;
    trace(@"adjusted left=%f right=%f w=%f compare to %f", left, right, w, left + right);
    NSRect r = _document.outlineView.frame;
    r.size.width = left;
    _document.outlineView.frame = r;
    _document.outlineView.needsLayout = true;
    _document.outlineView.needsDisplay = true;
    r = _document.tableView.frame;
    r.size.width = right;
    _document.tableView.frame = r;
    _document.tableView.needsLayout = true;
    _document.tableView.needsDisplay = true;
    [splitView setPosition:left ofDividerAtIndex:0];
    splitView.needsLayout = true;
    splitView.needsDisplay = true;
    [_document sizeOutlineViewToContents];
    return false; // refuse to collapse proportionally sized views
}
*/

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
 
 - (void)splitView:(NSSplitView *)splitView resizeSubviewsWithOldSize:(NSSize)oldSize {
 //  NSLog(@"splitView resizeSubviewsWithOldSize(%@)", NSStringFromSize(oldSize));
   [splitView adjustSubviews];
 }
 
 - (void)splitViewWillResizeSubviews:(NSNotification *)notification {
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
