#import "ZGImageAndTextCell.h"
#import "ZGDocument.h"
#import "ZGItemProtocol.h"

#define kImageOriginXOffset  7
#define kImageOriginYOffset  2

#define kTextOriginXOffset   8

@interface ZGImageAndTextCell () {
}
@property (readwrite) BOOL isGroup;
@property (readwrite, strong) NSImage* image;
@end

@implementation ZGImageAndTextCell

- (id) init {
    self = [super init];
    if (self) { // we want a smaller font
        self.font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize];
    }
    self.usesSingleLineMode = true;
    self.editable = true; // to make expand on ENTER work 
    self.scrollable = true;
    return self;
}

- (void) dealloc {
//    trace(@"");
}

- (id) copyWithZone: (NSZone *)zone {
    ZGImageAndTextCell *cell = (ZGImageAndTextCell *)[super copyWithZone:zone];
    cell.image = self.image;
    cell.isGroup = self.isGroup;
    return cell;
}

- (NSRect) titleRectForBounds: (NSRect) cellRect {
    assert(self.image != null);
    assert(self.representedObject != null);
    NSObject<ZGItemProtocol>* it = self.representedObject;
    if ([it isKindOfClass: ZGGroupItem.class]) {
        return [super titleRectForBounds: cellRect];
    } else {
        NSSize imageSize;
        NSRect imageFrame;
        NSSize titleSize = [[self attributedStringValue] size];
        cellRect.size.width = MAX(cellRect.size.width, titleSize.width + kImageOriginXOffset + kTextOriginXOffset);
        imageSize = [self.image size];
        NSDivideRect(cellRect, &imageFrame, &cellRect, kImageOriginXOffset + imageSize.width, NSMinXEdge);
        imageFrame.origin.x += kImageOriginXOffset;
        imageFrame.origin.y -= kImageOriginYOffset;
        imageFrame.size = imageSize;
        imageFrame.origin.y += ceil((cellRect.size.height - imageFrame.size.height) / 2);
        NSRect newFrame = cellRect;
        newFrame.origin.x += kTextOriginXOffset;
        return newFrame;
    }
}

- (void)editWithFrame:(NSRect)aRect inView:(NSView*)controlView editor:(NSText*)textObj delegate:(id)anObject event:(NSEvent*)theEvent {
    NSRect textFrame = [self titleRectForBounds:aRect];
    [super editWithFrame:textFrame inView:controlView editor:textObj delegate:anObject event:theEvent];
}

- (void)selectWithFrame:(NSRect)aRect inView:(NSView *)controlView editor:(NSText *)textObj delegate:(id)anObject start:(NSInteger)selStart length:(NSInteger)selLength {
    NSRect textFrame = [self titleRectForBounds:aRect];
    [super selectWithFrame:textFrame inView:controlView editor:textObj delegate:anObject start:selStart length:selLength];
}

- (void)drawWithFrame:(NSRect) cellFrame inView:(NSView *)controlView {
    assert(self.image != nil);
    assert(self.representedObject != null);
    NSObject<ZGItemProtocol>* it = self.representedObject;
    if ([it isKindOfClass: ZGGroupItem.class]) {
        [super drawWithFrame: cellFrame inView:controlView];
    } else {
        NSRect newCellFrame = cellFrame;
        NSSize imageSize;
        NSRect imageFrame;
        imageSize = [self.image size];
        NSDivideRect(newCellFrame, &imageFrame, &newCellFrame, imageSize.width, NSMinXEdge);
        if ([self drawsBackground]) {
            [[self backgroundColor] set];
            NSRectFill(imageFrame);
        }
        imageFrame.origin.x += kImageOriginXOffset;
        imageFrame.origin.y += kImageOriginYOffset;
        imageFrame.size = imageSize;
        [self.image drawInRect:imageFrame fromRect:NSZeroRect operation:NSCompositeSourceOver
                      fraction:1.0 respectFlipped:YES hints:nil];
        newCellFrame.origin.x += kTextOriginXOffset;
        //trace(@"%@ cellFrame=%@ newCellFrame=%@", [self stringValue], NSStringFromRect(cellFrame), NSStringFromRect(newCellFrame));
        [super drawWithFrame:newCellFrame inView:controlView];
    }
}

- (NSSize)cellSize {
    NSSize cellSize = [super cellSize];
    NSSize titleSize = [[self attributedStringValue] size];
    cellSize.width = MAX(cellSize.width, titleSize.width + kTextOriginXOffset);
    cellSize.width += [self.image size].width + kImageOriginXOffset;
    return cellSize;
}

@end

