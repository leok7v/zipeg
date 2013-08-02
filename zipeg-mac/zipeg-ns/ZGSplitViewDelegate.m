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


- (CGFloat)splitView:(NSSplitView *)splitView constrainMinCoordinate:(CGFloat)proposedMinimumPosition ofSubviewAt:(NSInteger)dividerIndex {
    return proposedMinimumPosition + 93;
}

- (CGFloat)splitView:(NSSplitView *)splitView constrainMaxCoordinate:(CGFloat)proposedMaximumPosition ofSubviewAt:(NSInteger)dividerIndex {
    return proposedMaximumPosition - 93;
}

- (void)splitViewDidResizeSubviews:(NSNotification *)notification {
    [_document sizeToContent];
}

@end
