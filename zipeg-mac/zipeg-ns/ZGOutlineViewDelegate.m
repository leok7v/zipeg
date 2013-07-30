#import "ZGUtils.h"
#import "ZGImages.h"
#import "ZGDocument.h"
#import "ZGItemProtocol.h"
#import "ZGTableViewDelegate.h"
#import "ZGOutlineViewDelegate.h"
#import "ZGOutlineViewDataSource.h"
#import "ZGImageAndTextCell.h"
#import "ZGItemProtocol.h"

@implementation NSOutlineView(SelectItem)

- (void)expandParentsOfItem: (NSObject<ZGItemProtocol>*) item {
    while (item != nil) {
        NSObject<ZGItemProtocol>* parent = item.parent;
        if (parent != null) {
            [self expandItem: parent expandChildren: false];
        }
        item = parent;
    }
}

- (void) selectItem: (id) item {
    NSInteger itemIndex = [self rowForItem: item];
    if (itemIndex < 0) {
        [self expandParentsOfItem: item];
        itemIndex = [self rowForItem: item];
        if (itemIndex < 0) {
            return;
        }
    }
    [self selectRowIndexes: [NSIndexSet indexSetWithIndex: itemIndex] byExtendingSelection: false];
}

@end

@interface ZGOutlineViewDelegate () {
    bool _queued;
    ZGDocument* __weak _document;
}
@end

@implementation ZGOutlineViewDelegate

- (id) initWithDocument: (ZGDocument*) doc {
    self = [super init];
    if (self) {
        alloc_count(self);
        _document = doc;
    }
    return self;
}

- (void) dealloc {
    trace(@"%@", self);
    dealloc_count(self);
    [NSNotificationCenter.defaultCenter removeObserver:self];
}

- (void) outlineView:(NSOutlineView *)outlineView willDisplayCell:(NSCell*)cell forTableColumn:(NSTableColumn *)tableColumn item:(id)item {
    if ([cell isKindOfClass:[ZGImageAndTextCell class]] && [item conformsToProtocol:@protocol(ZGItemProtocol)]) {
        NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)item;
        ZGImageAndTextCell* it = (ZGImageAndTextCell*)cell;
        NSImage* image = i.children == null ? ZGImages.shared.docImage : ZGImages.shared.dirImage;
        [it setRepresentedObject: item];
        trace(@"cell=%@ for %@ %@", cell, i, i.name);
        if (![it isKindOfClass: ZGGroupItem.class]) {
            it.image = image;
        }
        it.stringValue = i.name;
    }
}

- (BOOL)outlineView:(NSOutlineView *)outlineView shouldSelectItem:(id)item {
    // don't allow special group nodes (Devices and Places) to be selected
    return ![item isKindOfClass: ZGGroupItem.class];
}

- (NSCell*) outlineView: (NSOutlineView*) outlineView dataCellForTableColumn: (NSTableColumn*) tableColumn
                   item: (id) item {
    NSCell *returnCell = [tableColumn dataCell];
/*
    if ([[tableColumn identifier] isEqualToString:COLUMNID_NAME])
    {
        // we are being asked for the cell for the single and only column
        BaseNode *node = [item representedObject];
        if ([self isSeparator:node])
            returnCell = separatorCell;
    }
*/
    return returnCell;
}



-(void) outlineViewItemDidExpand:(NSNotification *)notification {
    // NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)[notification.userInfo objectForKey:@"NSObject"];
    NSOutlineView* v = (NSOutlineView*)notification.object;
    // trace("outlineViewItemDidExpand rows=%ld", v.numberOfRows);
    [self sizeOutlineViewToContents: v];
}

-(void) outlineViewItemWillExpand:(NSNotification *)notification {
}

-(void) outlineViewItemWillCollapse:(NSNotification *)notification {
}

-(BOOL)outlineView:(NSOutlineView *)outlineView shouldShowOutlineCellForItem:(id)item {
    NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)item;
    return i.parent != null;
}

- (BOOL)outlineView:(NSOutlineView *)outlineView isGroupItem:(id)item {
    NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)item;
    trace(@"isGroupItem %@=%@", i.name, [item isKindOfClass: ZGGroupItem.class] ? @"true" : @"false");
    return [item isKindOfClass: ZGGroupItem.class];
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
//      trace("sizeOutlineViewToContents skipped");
    }
}

- (void) selectItem: (NSObject<ZGItemProtocol>*) it {
    [_document.outlineView selectItem: it];
}

@end
