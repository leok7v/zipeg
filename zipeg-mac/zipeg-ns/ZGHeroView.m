#import "ZGHeroView.h"
#import "ZGImage.h"
#import "ZGApp.h"
#import "ZGDocument.h"

// http://stackoverflow.com/questions/2962790/best-way-to-change-the-background-color-for-an-nsview

@interface ZGHeroView() {
    NSImage* _appIcon;
    NSImage* _leafs[4];
    NSImage* _images[24];
    NSImage* _all[240];
    NSImage* _cache;
    int _index[4000];
    ZGDocument* __weak _document;
    NSSize _size;
}

@end

// time: ZGHeroView init 75 milliseconds
// time: ZGHeroView draw 20 milliseconds

@implementation ZGHeroView

- (id) initWithDocument: (ZGDocument*) doc andFrame:(NSRect)frame {
    self = [super initWithFrame: frame];
    // trace(@"initWithFrame %@", NSStringFromRect(self.frame));
    if (self != null) {
        alloc_count(self);
        _document = doc;
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (void) setFrame: (NSRect) frameRect {
    [super setFrame:frameRect];
}

- (BOOL) isOpaque {
    return true;
}

- (void) prepareImages {
    timestamp("prepareImages");
    assert(_appIcon == null);
    _appIcon = ZGApp.appIcon32x32;
    _leafs[0] = [[NSImage imageNamed: @"leaf-0-64x64.png"] copy];
    _leafs[1] = [[NSImage imageNamed: @"leaf-1-64x64.png"] copy];
    _leafs[2] = [[NSImage imageNamed: @"leaf-2-64x64.png"] copy];
    _leafs[3] = [[NSImage imageNamed: @"leaf-3-64x64.png"] copy];
    int N = countof(_images) / countof(_leafs);
    NSAssert(N * countof(_leafs) == countof(_images), @"_images must be multiples of _leafs");
    for (int i = 0; i < countof(_leafs); i++) {
        NSImage* img = _leafs[i];
        NSAssert(img.size.width == 64 && img.size.height == 64, @"leafs*.png corrupted");
        // trace("size[%d]: %@", i, NSStringFromSize(img.size));
    }
    for (int i = 0; i < countof(_leafs); i++) {
        NSImage* img = _leafs[i].copy;
        for (int j = 0; j < N; j++) {
            CGFloat degree = 360. * j / N;
            _images[i * N + j] = [img rotate: degree];
            // trace("degree: %f", degree);
        }
    }
    for (int i = 0; i < countof(_all); i++) {
        NSImage* img = _images[i % countof(_images)].copy;
        int size = 32 + arc4random_uniform(48);
        img.size = NSMakeSize(size, size);
        _all[i] = img;
        assert(_all[i] != null);
    }
    for (int i = 0; i < countof(_index); i++) {
        _index[i] = i % countof(_all);
    }
    // shuffle:
    for (int i = 0; i < countof(_index) * 4; i++) {
        int ix0 = arc4random_uniform(countof(_index));
        int ix1 = countof(_index) - 1 - ix0;
        int t = _index[ix0];
        _index[ix0] = _index[ix1];
        _index[ix1] = t;
    }
    timestamp("prepareImages");
}

static NSString* text = @"Drop Files Here";

- (void) drawImages: (NSRect) rect {
    if (_appIcon == null) {
        [self prepareImages];
    }
    [[NSColor colorWithCalibratedRed: 0.227 green: 0.337 blue: 0.251 alpha: 1] set];
    NSRectFill(rect);
    int dy = 0;
    for (float y = rect.origin.y - 64; y < rect.size.height + 64; y += 20) {
        int dx = _index[dy] * 13 % countof(_index);
        for (float x = rect.origin.x - 64; x < rect.size.width + 64; x += 20) {
            int ix = _index[dx];
            NSImage* i = _all[ix];
            if (arc4random_uniform(1000) < 15) {
                [_appIcon drawAtPoint: NSMakePoint(x, rect.size.height - y)];
            } else {
                [i drawAtPoint: NSMakePoint(x, rect.size.height - y)];
            }
            dx++;
        }
        dy++;
    }
}

-(void) drawBorder: (NSRect) rect color: (NSColor*) color {
    NSRect newRect = NSMakeRect(rect.origin.x - 10, rect.origin.y - 10, rect.size.width + 20, rect.size.height + 20);
    NSBezierPath *path = [NSBezierPath bezierPathWithRoundedRect: newRect xRadius: 10 yRadius: 10];
    path.lineWidth = 10;
    [color setStroke];
    [color setFill];
    [color set];
    CGFloat dash[] = { 42.0, 8.0 };
    [path setLineDash: dash count: countof(dash) phase: 0.0];
    [path stroke];
}

- (NSSize) totalScreenSize {
    NSSize size = NSMakeSize(0, 0);
    for (NSScreen* s in NSScreen.screens) {
        size.width  += s.visibleFrame.size.width;
        size.height += s.visibleFrame.size.height;
    }
    return size;
}

- (void) drawRect: (NSRect) rect {
    NSRect bounds = self.bounds;
    NSSize size = bounds.size;
    if (_cache == null || size.width > _size.width || size.height > _size.height) {
        _size = self.totalScreenSize;
        _size.width = MAX(_size.width, size.width);
        _size.height = MAX(_size.height, size.height);
        _cache = [NSImage.alloc initWithSize: _size];
        if (_cache != null) {
            [NSGraphicsContext.currentContext saveGraphicsState];
            [_cache lockFocus];
            [self drawImages: NSMakeRect(0, 0, _size.width, _size.height)];
            [_cache unlockFocus];
            [NSGraphicsContext.currentContext restoreGraphicsState];
        }
    }
    [_cache drawInRect: self.bounds fromRect: NSMakeRect(0, _size.height - size.height, size.width, size.height)];
    if (_document.isNew) {
        [NSColor.whiteColor set];
        NSColor* dark = [NSColor colorWithCalibratedWhite:0.1 alpha:1];
        NSDictionary* b = @{NSFontAttributeName: [NSFont fontWithName:@"HelveticaNeue-Bold" size: 64],
                            NSForegroundColorAttributeName: dark};
        NSDictionary* w = @{NSFontAttributeName: [NSFont fontWithName:@"HelveticaNeue-Bold" size: 64],
                            NSForegroundColorAttributeName: NSColor.lightGrayColor};
        NSRect r;
        r.size = [text sizeWithAttributes: b];
        r.origin.x = (size.width - r.size.width) / 2;
        r.origin.y = (size.height - r.size.height) / 2;
        [text drawInRect: r withAttributes: b];
        [self drawBorder: r color: dark];
        r.origin.x--;
        r.origin.y++;
        [text drawInRect: r withAttributes: w];
        [self drawBorder: r color: NSColor.lightGrayColor];
    }
}

@end
