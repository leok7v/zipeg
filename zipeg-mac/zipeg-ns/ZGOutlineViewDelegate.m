#import "ZGTableViewDelegate.h"
#import "ZGItemProtocol.h"
#import "ZGImages.h"
#import "ZGDocument.h"
#import "ZGItemProtocol.h"
#import "ZGOutlineViewDelegate.h"
#import "ZGOutlineViewDataSource.h"
#import "ZGImageAndTextCell.h"


@interface ZGOutlineViewDelegate () {
    bool _queued;
    ZGDocument* __weak _document;
    ZGSectionCell* _sectionCell;
}
@end

@implementation ZGOutlineViewDelegate

- (id) initWithDocument: (ZGDocument*) doc {
    self = [super init];
    if (self) {
        alloc_count(self);
        _sectionCell = [ZGSectionCell new];
        _document = doc;
    }
    return self;
}

- (void) dealloc {
    // trace(@"%@", self);
    dealloc_count(self);
    [NSNotificationCenter.defaultCenter removeObserver:self];
}

- (void) outlineView:(NSOutlineView *) v willDisplayCell:(NSCell*) c forTableColumn:(NSTableColumn *) tc item:(id) i {
    if ([c isKindOfClass:[ZGImageAndTextCell class]] && [i conformsToProtocol:@protocol(ZGItemProtocol)]) {
        NSObject<ZGItemProtocol>* it = (NSObject<ZGItemProtocol>*)i;
        ZGImageAndTextCell* itc = (ZGImageAndTextCell*)c;
        NSImage* image = it.children == null ? ZGImages.shared.docImage : ZGImages.shared.dirImage;
        if ([self outlineView: v isGroupItem: i]) {
            image = ZGImages.shared.appImage;
        } else { // TODO: ask ZGImages to retrieve system icon
            image = it.children == null ? ZGImages.shared.docImage : ZGImages.shared.dirImage;
        }
        [itc setRepresentedObject: i];
        // trace(@"cell=%@ for %@ %@", c, it, it.name);
        itc.image = image;
        itc.stringValue = it.name;
    }
}

- (BOOL)outlineView:(NSOutlineView *) v shouldSelectItem: (id) item {
    // don't allow special group nodes to be selected
    // NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)item;
    // trace(@"%@ = %d", i.name, ![self outlineView: v isGroupItem: item]);
    return ![self outlineView:v isGroupItem:item];
}

- (NSCell*) outlineView: (NSOutlineView*) v dataCellForTableColumn: (NSTableColumn*) tc item: (id) i {
    NSCell* c = [tc dataCell];
    // NSObject<ZGItemProtocol>* it = (NSObject<ZGItemProtocol>*)item;
    if ([self outlineView: v isGroupItem: i]) {
        c = _sectionCell;
    }
    return c;
}

-(void) outlineViewItemDidExpand: (NSNotification*) n {
    // NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)[n.userInfo objectForKey:@"NSObject"];
    NSOutlineView* v = (NSOutlineView*)n.object;
    // trace("outlineViewItemDidExpand rows=%ld", v.numberOfRows);
    [self sizeOutlineViewToContents: v];
}

-(void) outlineViewItemWillExpand: (NSNotification*) n {
}

-(void) outlineViewItemWillCollapse: (NSNotification*) n {
}

-(BOOL)outlineView: (NSOutlineView*) outlineView shouldShowOutlineCellForItem: (id) i {
//  NSObject<ZGItemProtocol>* it = (NSObject<ZGItemProtocol>*)i;
    return true;
}

- (BOOL)outlineView:(NSOutlineView *)outlineView isGroupItem:(id)i {
    NSObject<ZGItemProtocol>* it = (NSObject<ZGItemProtocol>*)i;
    // trace(@"isGroupItem %@=%@ %d", it.name, it.parent == null ? @"true" : @"false", it.isGroup);
    return it.isGroup;
}

-(void) outlineViewItemDidCollapse:(NSNotification *)notification {
    // NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)[notification.userInfo objectForKey:@"NSObject"];
    NSOutlineView* v = (NSOutlineView*)notification.object;
    [self sizeOutlineViewToContents: v];
}

- (BOOL) outlineView:(NSOutlineView *)v shouldEditTableColumn:(NSTableColumn *)tableColumn item:(id)item {
    NSInteger row = [v rowForItem:item];
    if (row >= 0 && [v isRowSelected: row] && ![v isItemExpanded:item]) {
        [v expandItem:item expandChildren:false];
    }
    return false;
}

- (void)expandOne: (NSOutlineView*) outlineView {
    if (!outlineView.dataSource) {
        return;
    }
    // TODO: expand deeper into tree all of single parent root items...
    NSObject<NSOutlineViewDataSource>* ds = outlineView.dataSource;
    for (int i = 0; i < [ds outlineView: outlineView numberOfChildrenOfItem: null]; i++) {
        id item = [ds outlineView: outlineView child:i ofItem: null];
        if ([ds outlineView: outlineView isItemExpandable: item]) {
            [outlineView expandItem:item expandChildren:false];
        }
    }
}

- (void)_sizeOutlineViewToContents:(NSOutlineView*) v {
    assert(v != null);
    NSSize s = [ZGTableViewDelegate minMaxVisibleColumnContentSize: v columnIndex: 0];
    if (s.width > 0 && s.height > 0) {
        NSTableColumn* tc = v.tableColumns[0];
        tc.width = s.width;
        v.rowHeight = s.height;
        [_document.window.contentView setNeedsDisplay: true];
    }
}

- (void) sizeOutlineViewToContents:(NSOutlineView*) outlineView {
    if (!_queued) {
        _queued = true;
        dispatch_async(dispatch_get_current_queue(), ^{
            [self _sizeOutlineViewToContents:outlineView];
            _queued = false;
        });
    } else {
        // trace("sizeOutlineViewToContents skipped");
    }
}

- (void) selectItem: (NSObject<ZGItemProtocol>*) it {
    [_document.outlineView selectItem: it];
}

@end
