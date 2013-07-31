#import "ZGImageAndTextCell.h"
#import "ZGDocument.h"
#import "ZGItemProtocol.h"
#import "ZGImages.h"

#define kImageOriginXOffset  7
#define kImageOriginYOffset  0

#define kTextOriginXOffset   8
#define kTextOriginYOffset   0

@interface ZGImageAndTextCell () {
}
@property (readwrite, strong) NSImage* image;
@end

@implementation ZGImageAndTextCell

- (id) init {
    self = [self initSuper];
    if (self) { // we want a smaller font
        self.font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize];
        self.usesSingleLineMode = true;
        self.editable = true; // to make expand on ENTER work
        self.scrollable = true;
    }
    return self;
}

- (id) initSuper {
    self = [super init];
    return self;
}


- (void) dealloc {
    // trace(@"");
}

- (id) copyWithZone: (NSZone *)zone {
    ZGImageAndTextCell *cell = (ZGImageAndTextCell *)[super copyWithZone:zone];
    cell.image = self.image;
    return cell;
}

- (NSRect) titleRectForBounds: (NSRect) bounds {
    int imageOriginYOffset = kImageOriginYOffset + 2 * [self.controlView isKindOfClass:NSOutlineView.class];
    NSSize titleSize = [[self attributedStringValue] size];
    bounds.size.width = MAX(bounds.size.width, titleSize.width + kImageOriginXOffset + kTextOriginXOffset);
    NSSize isz = [self.image size];
    NSRect ifr;
    NSDivideRect(bounds, &ifr, &bounds, kImageOriginXOffset + isz.width, NSMinXEdge);
    ifr.origin.x += kImageOriginXOffset;
    ifr.origin.y -= imageOriginYOffset;
    ifr.size = isz;
    ifr.origin.y += ceil((bounds.size.height - ifr.size.height) / 2);
    NSRect frame = bounds;
    frame.origin.x += kTextOriginXOffset;
    return frame;
}

- (void)editWithFrame: (NSRect) f inView: (NSView*) view editor: (NSText*) ed delegate: (id) d event: (NSEvent*) e {
    NSRect textFrame = [self titleRectForBounds: f];
    [super editWithFrame: textFrame inView: view editor: ed delegate: d event: e];
}

- (void)selectWithFrame: (NSRect) f inView: (NSView*) v editor: (NSText*) ed delegate: (id) d start: (NSInteger) s length:(NSInteger) len {
    NSRect b = [self titleRectForBounds:f];
    [super selectWithFrame: b inView: v editor: ed delegate: d start: s length: len];
}

- (void)drawWithFrame: (NSRect) f inView: (NSView *) v {
    [NSGraphicsContext saveGraphicsState];
    bool ov = [v isKindOfClass:NSOutlineView.class];
    int imageOriginYOffset = kImageOriginYOffset + 2 * ov;
    int textOriginYOffset = kTextOriginYOffset + 1 * ov;
    // trace(@"drawWithFrame %@", self.stringValue);
    assert(self.image != nil);
    if (self.image == null) {
        // trace(@"drawWithFrame %@ no image", self.stringValue);
    }
    if (self.image) {
        NSSize isz = [self.image size];
        NSRect ifr;
        NSDivideRect(f, &ifr, &f, isz.width, NSMinXEdge);
        if ([self drawsBackground]) {
            [[self backgroundColor] set];
            NSRectFill(ifr);
        }
        ifr.origin.x += kImageOriginXOffset;
        ifr.origin.y += imageOriginYOffset;
        ifr.size = isz;
        [self.image drawInRect:ifr fromRect:NSZeroRect operation:NSCompositeSourceOver
                      fraction:1.0 respectFlipped:YES hints:nil];
    }
    f.origin.x += kTextOriginXOffset;
    f.origin.y += textOriginYOffset;
    // trace(@"%@ cellFrame=%@ newCellFrame=%@", [self stringValue], NSStringFromRect(cellFrame), NSStringFromRect(newCellFrame));
    [super drawWithFrame:f inView:v];
    [NSGraphicsContext restoreGraphicsState];
}

- (NSSize)cellSize {
    NSSize cs = [super cellSize];
    NSSize ts = [[self attributedStringValue] size];
    cs.width = MAX(cs.width, ts.width + kTextOriginXOffset);
    cs.width += [self.image size].width + kImageOriginXOffset;
    return cs;
}

@end

@implementation ZGSectionCell

- (id) init {
    self = [super init];
    if (self != null) {
        self.textColor = [NSColor disabledControlTextColor];
        self.font = [NSFont systemFontOfSize: NSFont.systemFontSize];
    }
    return self;
}

- (void) dealloc {
    // trace(@"");
}

- (id) copyWithZone: (NSZone *)zone {
    ZGSectionCell* cell = (ZGSectionCell*) [super copyWithZone: zone];
    return cell;
}

- (void)drawWithFrame: (NSRect) f inView:(NSView *) v {
    f.origin.x -= kTextOriginXOffset + 1;
    f.origin.y -= 2;
    [super drawWithFrame: f inView: v];
}

@end

