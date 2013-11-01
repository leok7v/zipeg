@interface NSImage(ZGExtensions)
- (id) initWithCGImage: (CGImageRef) cgImage;

- (NSImage*) rotate: (CGFloat) degrees;
- (NSImage*) mirror; // mirror around Y axis (swap left and right)

- (int64_t) imageBytes;  // counts approximate number of bytes or RAM the image consumes
- (void) makePixelSized; // set size to maximum represnation pixelWide pixelHigh
- (NSImage*) square: (CGFloat) size; // makes image into [size x size] square. If size == 0 uses max(width, hwight) as size

- (void) drawAtPoint: (NSPoint) p;
- (void) drawAtPoint: (NSPoint) p fromRect: (NSRect) r;
- (void) drawAtPoint: (NSPoint) p fromRect: (NSRect) r operation: (NSCompositingOperation) op;
- (void) drawAtPoint: (NSPoint) p fromRect: (NSRect) r operation: (NSCompositingOperation) op opacity: (CGFloat) alpha;

- (void) draw: (NSRect) d;
- (void) drawInRect: (NSRect) d fromRect: (NSRect) r;
- (void) drawInRect: (NSRect) d fromRect: (NSRect) r operation: (NSCompositingOperation) op;
- (void) drawInRect: (NSRect) d fromRect: (NSRect) r operation: (NSCompositingOperation) op opacity: (CGFloat) alpha;

- (NSImage*) upsideDownShadowed: (float) fraction;

+ (NSImage*) qlImage: (NSString*) path ofSize: (NSSize) size asIcon: (BOOL) icon;
+ (NSImage*) exifThumbnail: (NSURL*) url;



@end


@interface ZGImage : NSImage // NSImage with NSAffineTransform hint

@property NSAffineTransform* transform;

- (void)drawInRect: (NSRect)d fromRect: (NSRect) r operation: (NSCompositingOperation) op opacity: (CGFloat) alpha;

// WARNING: drawAtPoint does NOT respect "flipped" and "transform"!
- (void) drawAtPoint: (NSPoint) p fromRect: (NSRect) r operation: (NSCompositingOperation) op opacity: (CGFloat) alpha;

@end