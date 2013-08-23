#import "ZGSplitViewDelegate.h"
#import "ZGTableViewDelegate.h"
#import "ZGImageAndTextCell.h"

@interface ZGSplitViewDelegate () {
    id _windowWillCloseObserver;
    ZGDocument* __weak _document;
    NSMutableDictionary* _minSize; // int view index -> float minimum size
    NSMutableDictionary* _w2ix;    // float weight -> int view index
}
@end

@implementation ZGSplitViewDelegate

- (id) initWithDocument: (ZGDocument*) doc {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        _document = doc;
        _windowWillCloseObserver = addObserver(NSWindowWillCloseNotification, _document.window,
            ^(NSNotification* n) {
                _document.splitView.delegate = null;
                _windowWillCloseObserver = removeObserver(_windowWillCloseObserver);
        });
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

#define kSplitSubviewsSizes @"ZGSplitSubviewsSizes"

- (void) setMinimumSize: (CGFloat) minSize atIndex: (NSInteger) ix {
    if (_minSize == null) {
        _minSize = [NSMutableDictionary dictionaryWithCapacity: 16];
    }
    _minSize[@(ix)] = @(minSize);
}

- (void) setWeight: (CGFloat) weight atIndex: (NSInteger) ix {
    if (_w2ix == null) {
        _w2ix = [NSMutableDictionary dictionaryWithCapacity: 16];
    }
    _w2ix[@(ix)] = @(weight);
}

- (void) splitViewDidResizeSubviews: (NSNotification*) n {
    [self saveSplitSubviewSizes: n.object];
    [_document sizeToContent];
}

- (CGFloat) splitView: (NSSplitView*) sv constrainMinCoordinate: (CGFloat) min ofSubviewAt: (NSInteger) ix {
    NSView* v = sv.subviews[ix];
    NSRect f = v.frame;
    return (sv.isVertical ? f.origin.x : f.origin.y) + [_minSize[@(ix)] floatValue];
}

- (CGFloat) splitView: (NSSplitView*) sv constrainMaxCoordinate: (CGFloat) proposedMax ofSubviewAt: (NSInteger) ix {
    NSView* growing = sv.subviews[ix];
    NSView *shrinking = sv.subviews[ix + 1];
    NSRect gf = growing.frame;
    NSRect sf = shrinking.frame;
    CGFloat coordinate = sv.isVertical ? gf.origin.x + gf.size.width : gf.origin.y + gf.size.height;
    CGFloat size = sv.isVertical ? sf.size.width : sf.size.height;
    return coordinate + (size - [_minSize[@(ix + 1)] floatValue]);
}

- (CGFloat) minSize: (NSNumber*) ix {
    return [_minSize[ix] floatValue];
}

- (CGFloat) splitView: (NSSplitView*) sv subsize: (NSInteger) ix {
    NSView* view = sv.subviews[ix];
    NSSize size = view.frame.size;
    return sv.isVertical ? size.width : size.height;
}

// 3 split subvies sizes won't make sense for 2 split subview. orientation matters too
- (NSString*) key: (NSSplitView* ) sv {
    return [NSString stringWithFormat: @"%@.%ld.%@", kSplitSubviewsSizes, sv.subviews.count,
            sv.isVertical ? @"v" : @"h"];
}

- (void) saveSplitSubviewSizes: (NSSplitView*) sv {
    NSMutableArray* sss = [NSMutableArray arrayWithCapacity: sv.subviews.count];
    int k = 0;
    for (NSView* v in sv.subviews) {
        sss[k++] = @(sv.isVertical ? v.frame.size.width : v.frame.size.height);
    }
    [NSUserDefaults.standardUserDefaults  setObject: sss forKey: [self key: sv]];
}

- (void) layout: (NSSplitView*) sv  {
    NSArray* sss = [NSUserDefaults.standardUserDefaults objectForKey: [self key: sv]];
    sss = null;
    // it direction of a splitter has been changed from initial .nib layout we need to relayout all views.
    CGFloat total = 0;
    int i = 0;
    CGFloat sizes[sv.subviews.count];
    for (NSView* v in sv.subviews) {
        sizes[i] = sss != null && i < sss.count ?
        [sss[i] floatValue] :
        (sv.isVertical ? v.frame.size.width : v.frame.size.height);
        total += sizes[i];
        i++;
    }
    CGFloat divider = [sv dividerThickness];
    CGFloat splitterSize = (sv.isVertical ? sv.bounds.size.width : sv.bounds.size.height) - divider * (sv.subviews.count - 1);
    CGFloat offset = 0;
    i = 0;
    for (NSView* v in sv.subviews) {
        CGFloat s = splitterSize * sizes[i] / total;
        v.frame = sv.isVertical? NSMakeRect(offset, 0, s, sv.bounds.size.height) :
        NSMakeRect(0, offset, sv.bounds.size.width, s);
        offset += s + divider;
        // trace(@"%@", NSStringFromRect(v.frame));
        i++;
    }
    // trace(@"%f %@", offset - divider, NSStringFromRect(sv.bounds));
    [self saveSplitSubviewSizes: sv];
}

// TODO: the code below does not work well
- (void) splitViewXXX: (NSSplitView*) sv resizeSubviewsWithOldSize: (NSSize) oldSize {
    CGFloat delta = sv.isVertical ? sv.bounds.size.width - oldSize.width : sv.bounds.size.height - oldSize.height;
    // trace(@"delta=%f %@ old=%@", delta, NSStringFromSize(sv.bounds.size), NSStringFromSize(oldSize));
    NSArray* sorted = [_w2ix.allKeys sortedArrayUsingComparator: ^ NSComparisonResult(id o0, id o1) {
        NSNumber* v0 = _w2ix[o0];
        NSNumber* v1 = _w2ix[o1];
        // trace(@"compare(%@, %@)=%ld", v0, v1, [v1 compare: v0]);
        return [v1 compare: v0];
    }];
    // trace(@"sorted=%@", sorted);
    BOOL force = false;
    if (sv.subviews == null || sv.subviews.count <= 1) {
        return;
    }
    for (;;) {
        int k = 0;
        CGFloat sum = 0;   // sum of all the view "priorities"
        while (sum == 0 && !force) {
            for (NSNumber* priority in sorted) {
                if (k < sv.subviews.count) {
                    CGFloat min = [self minSize: priority];
                    CGFloat s = [self splitView: sv subsize: priority.integerValue];
                    sum += delta > 0 || s > min || force ? [_w2ix[priority] floatValue] : 0;
                    k++;
                }
            }
            force = force || sum == 0; // sticky
            // trace(@"sum=%f force=%d", sum, force);
        }
        if (force && sum == 0) {
            @throw @"setWeight was not called for SplitViewDelegate subviews";
        }
        NSAssert1(sum > 0, @"sum=%f", sum);
        NSAssert2(sorted.count >= sv.subviews.count, @"sorted.count=%ld sv.subviews.count=%ld", sorted.count, sv.subviews.count);
        CGFloat deltas[sorted.count];
        k = 0;
        for (NSNumber* priority in sorted) {
            if (k < sv.subviews.count) {
                CGFloat min = [self minSize: priority];
                CGFloat s = [self splitView: sv subsize: priority.integerValue];
                deltas[k++] = delta > 0 || s > min || force ? [_w2ix[priority] floatValue] / sum : 0;
                // trace(@"deltas[%d]=%f s=%f min=%f", k - 1, deltas[k - 1], s, min);
            }
        }
        k = 0;
        for (NSNumber* priority in sorted) {
            if (k < sv.subviews.count) {
                NSInteger ix = priority.integerValue;
                NSView* view = sv.subviews[ix];
                NSSize size = sv.bounds.size;
                CGFloat min = [self minSize: priority];
                CGFloat d = delta * deltas[k];
                // trace(@"[%@] %@ d=%f delta=%f", priority, _w2ix[priority], d, delta);
                CGFloat s = [self splitView: sv subsize: priority.integerValue];
                if (d > 0 || s + d >= min || force) {
                    delta -= d;
                    s += d;
                } else if (d < 0) {
                    delta += s - min;
                    s = min;
                }
                if (sv.isVertical) {
                    size.width = s;
                } else {
                    size.height = s;
                }
                // trace(@"[%@] %@=%@ d=%f delta=%f", priority, _w2ix[priority], NSStringFromSize(size), d, delta);
                view.frameSize = size;
            }
            k++;
        }
        if (fabs(delta) < 0.5) {
            break; // even if fabs(delta) > 0.5
        }
    }
    NSAssert3(fabs(delta) < 0.5, @"Split view %p resized smaller than minimum %@ of %f",
              sv, sv.isVertical ? @"width" : @"height", sv.frame.size.width - delta);
    CGFloat offset = 0;
    CGFloat divider = [sv dividerThickness];
    for (NSView* v in sv.subviews) {
        NSSize fs = v.frame.size;
        v.frame = sv.isVertical ? NSMakeRect(offset, 0, fs.width, sv.bounds.size.height) :
        NSMakeRect(0, offset, sv.bounds.size.width, fs.height);
        offset += sv.isVertical ? fs.width + divider : fs.height + divider;
    }
    // last view may have a rounding 0.5 error and tends to accumulate - here is the fix for it:
    NSView* v = sv.subviews[sv.subviews.count - 1];
    NSSize fs = v.frame.size;
    if (sv.isVertical) {
        fs.width = sv.bounds.size.width - v.frame.origin.x;
    } else {
        fs.height = sv.bounds.size.height - v.frame.origin.y;
    }
    v.frameSize = fs;
}

- (BOOL) splitView: (NSSplitView*) splitView canCollapseSubview: (NSView*) subview {
    return false;
}

@end
