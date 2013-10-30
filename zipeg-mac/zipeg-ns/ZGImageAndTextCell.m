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
    self = [super init];
    if (self) { // we want a smaller font
        alloc_count(self);
        self.font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize];
        self.usesSingleLineMode = true;
        self.editable = true; // to make expand on ENTER work
        self.scrollable = true;
    }
    return self;
}


- (void) dealloc {
    dealloc_count(self);
}

- (id) copyWithZone: (NSZone*) zone {
    ZGImageAndTextCell* cell = (ZGImageAndTextCell*) [super copyWithZone: zone];
    cell.image = self.image;
    alloc_count(cell);
    return cell;
}

- (NSRect) titleRectForBounds: (NSRect) bounds {
    // TODO: sanitize ch < 32 out of the attributedStringValue (thank to Apple for Icon<CR>
    // which is still used all around the internet inside iconset zip files
    // see: paper_stacks_by_blupaper-d2zx0sa.zip
    NSSize titleSize = [[self attributedStringValue] size];
    bounds.size.width = MAX(bounds.size.width, titleSize.width + kImageOriginXOffset + kTextOriginXOffset);
    NSRect ifr;
    NSDivideRect(bounds, &ifr, &bounds, kImageOriginXOffset + self.image.size.width, NSMinXEdge);
    NSRect frame = bounds;
    frame.origin.x += kTextOriginXOffset;
    return frame;
}

- (void)editWithFrame: (NSRect) f inView: (NSView*) view editor: (NSText*) ed
             delegate: (id) d event: (NSEvent*) e {
    NSRect textFrame = [self titleRectForBounds: f];
    [super editWithFrame: textFrame inView: view editor: ed delegate: d event: e];
}

- (void) selectWithFrame: (NSRect) f inView: (NSView*) v editor: (NSText*) ed
                delegate: (id) d start: (NSInteger) s length:(NSInteger) len {
    NSRect b = [self titleRectForBounds:f];
    [super selectWithFrame: b inView: v editor: ed delegate: d start: s length: len];
}

- (void) drawWithFrame: (NSRect) f inView: (NSView*)  v {
    bool ov = [v isKindOfClass:NSOutlineView.class];
    int imageOriginYOffset = kImageOriginYOffset + 1 * ov;
    int textOriginYOffset = kTextOriginYOffset + 0 * ov;
    assert(self.image != null);
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
        [self.image drawInRect: ifr fromRect: NSZeroRect operation: NSCompositeSourceOver
                      fraction: 1.0 respectFlipped: true hints: null];
    }
    f.origin.x += kTextOriginXOffset;
    f.origin.y += textOriginYOffset;
    [super drawWithFrame: f inView: v];
}

- (NSSize) cellSize {
    NSString* s = self.attributedStringValue.string;
    if ([s indexOf:@"\n"] >= 0 || [s indexOf:@"\r"] >= 0) {
        // see: 0.hfs inside GoogleNotifier_1.10.7.dmg
        // ".HFS+ Private Directory Data\n" and "".HFS+ Private Directory Data\n"
        NSMutableAttributedString* as = [NSMutableAttributedString.new initWithAttributedString: self.attributedStringValue];
        [as.mutableString replaceOccurrencesOfString: @"\n"
                                          withString: @""
                                             options: NSLiteralSearch
                                               range: NSMakeRange(0, as.string.length)];
        [as.mutableString replaceOccurrencesOfString: @"\r"
                                          withString: @""
                                             options: NSLiteralSearch
                                               range: NSMakeRange(0, as.string.length)];
        self.attributedStringValue = as;
    }
    NSSize cs = [super cellSize];
    NSSize ts = [self.attributedStringValue size];
    cs.width = MAX(cs.width, ts.width + kTextOriginXOffset);
    cs.width += self.image.size.width + kImageOriginXOffset;
/*
    if (cs.height != 16) {
        trace("\"%@\" %@", self.stringValue, NSStringFromSize(ts));
        trace("%@", self.attributedStringValue);
    }
*/
    return cs;
}

@end

@implementation ZGSectionCell

- (id) init {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (id) copyWithZone: (NSZone*) zone {
    ZGSectionCell* cell = (ZGSectionCell*) [super copyWithZone: zone];
    alloc_count(cell);
    return cell;
}

- (NSRect) titleRectForBounds: (NSRect) bounds {
    return NSZeroRect;
}

- (void)drawWithFrame: (NSRect) f inView:(NSView*) v {
}

- (NSSize)cellSize {
    return NSZeroSize;
}

@end
