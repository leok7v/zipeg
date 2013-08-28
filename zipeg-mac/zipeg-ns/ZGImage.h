@interface NSImage(ZGExtensions)
- (id) initWithCGImage: (CGImageRef) cgImage;
- (NSImage*) rotate: (CGFloat) degrees;
- (NSImage*) mirror; // mirror around Y axis (swap left and right)

- (void) drawAtPoint: (NSPoint) p;
- (void) drawAtPoint: (NSPoint) p fromRect: (NSRect) r;
- (void) drawAtPoint: (NSPoint) p fromRect: (NSRect) r operation: (NSCompositingOperation) op;
- (void) drawAtPoint: (NSPoint) p fromRect: (NSRect) r operation: (NSCompositingOperation) op opacity: (CGFloat) alpha;

- (void) drawInRect: (NSRect) d;
- (void) drawInRect: (NSRect) d fromRect: (NSRect) r;
- (void) drawInRect: (NSRect) d fromRect: (NSRect) r operation: (NSCompositingOperation) op;
- (void) drawInRect: (NSRect) d fromRect: (NSRect) r operation: (NSCompositingOperation) op opacity: (CGFloat) alpha;
@end


@interface ZGImage : NSImage // NSImage with NSAffineTransform hint

@property NSAffineTransform* transform;

- (void)drawInRect: (NSRect)d fromRect: (NSRect) r operation: (NSCompositingOperation) op opacity: (CGFloat) alpha;

// WARNING: drawAtPoint does NOT respect "flipped" and "transform"!
- (void) drawAtPoint: (NSPoint) p fromRect: (NSRect) r operation: (NSCompositingOperation) op opacity: (CGFloat) alpha;

@end