#import "ZGImages.h"
#import "ZGDocument.h"
#import "ZGOutlineViewDelegate.h"
#import "ZGTableViewDelegate.h"
#import "ZGTableViewDataSource.h"
#import "ZGImageAndTextCell.h"
#import "ZGItemProtocol.h"

@interface ZGTableViewDelegate () {
    ZGDocument* __weak _document;
    ZGBlock* _delayedSizeToContent;
    id _windowWillCloseObserver;
}

@end

@implementation ZGTableViewDelegate

- (id) initWithDocument: (ZGDocument*) doc {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        _document = doc;
        _windowWillCloseObserver = addObserver(NSWindowWillCloseNotification, _document.window,
            ^(NSNotification* n) {
                [self cancelDelayed];
                _document.tableView.delegate = null;
                _windowWillCloseObserver = removeObserver(_windowWillCloseObserver);
                [_document.tableView removeTableColumn: _document.tableView.tableColumns[0]];
            });
    }
    return self;
}

- (void) dealloc {
    [self cancelDelayed];
    dealloc_count(self);
}

- (void) cancelDelayed {
    _delayedSizeToContent = [_delayedSizeToContent cancel];
}

- (void) tableViewColumnDidResize: (NSNotification*) n {
    // NSTableViewColumnDidResizeNotification  @"NSTableColumn", @"NSOldWidth"
    // NSTableColumn* tc = (NSTableColumn*)n.userInfo[@"NSTableColumn"];
    // NSNumber* oldWidth = (NSNumber*)n.userInfo[@"NSOldWidth"];
    // trace(@"column %@ %d", tc, oldWidth.intValue);
    // TODO: should it affect autosizing? for how long?
    // I should keep user preferred size till user double click on column divider
    // and preferred size is reset back to -1. If preferred size == -1 autosize.
}

- (void) outlineViewSelectionWillChange {
    [_delayedSizeToContent cancel];
}

- (void) outlineViewSelectionDidChange {
    [_document.tableView reloadData];
    [_document.tableView deselectAll: null];
    [self sizeToContent: _document.tableView];
}

- (void) tableView: (NSTableView *) v willDisplayCell: (id) cell
   forTableColumn: (NSTableColumn*) column row: (NSInteger) row {
    ZGTableViewDataSource* ds = (ZGTableViewDataSource*) v.dataSource;
    NSObject<ZGItemProtocol>* it = [ds itemAtRow: row];
    if ([cell isKindOfClass:[ZGImageAndTextCell class]]) {
        ZGImageAndTextCell* c = (ZGImageAndTextCell*)cell;
        NSImage* image = [_document itemImage: it open: false];
        c.representedObject = it;
        c.image = image;
        c.stringValue = it.name;
    } else if ([cell isKindOfClass:[NSTextFieldCell class]]) {
        NSObject* o = [ds tableView: v objectValueForTableColumn:column row: row];
        NSTextFieldCell* t = (NSTextFieldCell*)cell;
        t.stringValue = o.description;
    } else {
        assert(false);
    }
}

+ (NSSize) minMaxVisibleColumnContentSize: (NSTableView*) view columnIndex: (int) cx {
    assert(view != null);
    const int EXTRA = 250; // about 10 milliseconds for 500 rows
    NSInteger rowCount = view.numberOfRows;
    CGFloat maxHeight = view.rowHeight;
    if (view.dataSource == null) {
        return NSMakeSize(-1, -1);
    }
//  timestamp("minMaxVisibleColumnContentSize");
    NSOutlineView* ov = [view isKindOfClass: NSOutlineView.class] ? (NSOutlineView*)view : null;
    ZGTableViewDataSource* tds = [view.dataSource isKindOfClass: ZGTableViewDataSource.class] ?
                                 (ZGTableViewDataSource*)view.dataSource : null;
    assert((ov != null) != (tds != null));
    CGRect visibleRect = view.enclosingScrollView.contentView.visibleRect;
    // trace(@"%@", NSStringFromRect(visibleRect));
    NSRange range = [view rowsInRect:visibleRect];
    int from = (int)MAX(0, range.location);
    int to = (int)MIN(range.location + range.length, rowCount);
    CGFloat indentationPerLevel = ov != null ? ov.indentationPerLevel : 0;
    NSTableColumn *tableColumn = view.tableColumns[cx];
    CGFloat minWidth = tableColumn.minWidth;
    CGFloat width = [tableColumn.headerCell cellSize].width;
    CGFloat maxWidth = MAX(minWidth, width);
    from = (int)MAX(0, from - EXTRA);
    to += EXTRA;
    if (from - EXTRA < 0) {
        to -= (from - EXTRA);
    }
    to = (int)MIN(to, rowCount);
    for (NSInteger i = from; i < to; i++) {
        NSCell *cell = [tableColumn dataCellForRow:i];
        id item = ov != null ? [ov itemAtRow: i] : [tds itemAtRow: i];
        if ([cell isKindOfClass: [ZGImageAndTextCell class]] &&
            [item conformsToProtocol: @protocol(ZGItemProtocol)]) {
            [cell setRepresentedObject:item];
            ZGImageAndTextCell* itc = (ZGImageAndTextCell*)cell;
            NSObject<ZGItemProtocol>* it = (NSObject<ZGItemProtocol>*)item;
            NSImage* nodeIcon = it.children == null ? ZGImages.shared.docImage : ZGImages.shared.dirImage;
            itc.image = nodeIcon;
            itc.stringValue = it.name;
            itc.representedObject = it;
        } else if ([cell isKindOfClass: [NSTextFieldCell class]]) {
            // assume this is table
            NSObject* o = [tds tableView:view objectValueForTableColumn:tableColumn row:i];
            NSTextFieldCell* tc = (NSTextFieldCell*)cell;
            tc.stringValue = o.description;
        } else {
            continue; // TODO: skip for now
        }
        CGSize size = [cell cellSize];
        CGFloat x = 0;
        if (ov != null) {
            NSInteger levelForRow = [ov levelForRow:i];
            x = indentationPerLevel * (levelForRow + 1);
        }
        maxWidth = MAX(maxWidth, size.width + x); // emiprecally found extra pixels
        maxHeight = MAX(maxHeight, size.height);
    }
//  timestamp("minMaxVisibleColumnContentSize");
    return NSMakeSize(maxWidth, maxHeight);
}

- (void) _sizeToContent: (NSTableView*) v {
    assert(v != null);
    int n = (int)v.tableColumns.count;
    for (int i = 0; i < n; i++) {
        NSSize size = [ZGTableViewDelegate minMaxVisibleColumnContentSize:v columnIndex: i];
        if (size.width > 0 && size.height > 0) {
            NSTableColumn* tableColumn = v.tableColumns[i];
            tableColumn.width = MAX(tableColumn.width, size.width + 16);
            v.rowHeight = size.height;
            [_document.window.contentView setNeedsDisplay: true];
        }
    }
}

- (void) sizeToContent: (NSTableView*) v {
    if (_delayedSizeToContent == null) {
        _delayedSizeToContent = [ZGUtils invokeLater:^(){
            [self _sizeToContent: v];     // order of this two lines is irrelevant because they executed
            _delayedSizeToContent = null; // inside single iteration of one dispatch loop
        }];
    } else {
        // trace("sizeToContent skipped");
    }
}

- (void)tableView:(NSTableView *)tableView didClickTableColumn:(NSTableColumn *)tableColumn {
    trace(@"TODO: extend to more than one column");
    // see: -tableView:sortDescriptorsDidChange: in TableViewDataSource
    //      which sets sortDescriptorPrototype to null on 3rd click on column header
    NSTableColumn* tc = tableView.tableColumns[0];
    if (tc.sortDescriptorPrototype == null) {
        NSSortDescriptor *sd = [NSSortDescriptor sortDescriptorWithKey:@"name" ascending:YES
                                                              selector:@selector(localizedCaseInsensitiveCompare:)];
        tc.sortDescriptorPrototype = sd; // do not reload data, sort will happen on next click
    }
}

- (CGFloat) tableView: (NSTableView *) v sizeToFitWidthOfColumn: (NSInteger) cx {
    NSSize s = [ZGTableViewDelegate minMaxVisibleColumnContentSize:v columnIndex: (int)cx];
    return s.width;
}

- (BOOL) tableView: (NSTableView*) v shouldEditTableColumn: (NSTableColumn*) c row: (NSInteger) row {
    // TODO: this is bugger - if the selection is already in table view but table view is not focused
    // click on selection should only move the focus not enter the folder. Unfortunately this needs yet
    // another delay on accept first responder... :( 
    if ([v isRowSelected: row] && isEqual(v, [_document.window firstResponder])) {
        [self tableView: v enterFolder: row];
    }
    return false;
}

- (void) tableView: (NSTableView*) v enterFolder: (NSInteger) row {
    ZGTableViewDataSource* tds = (ZGTableViewDataSource*)v.dataSource;
    NSObject<ZGItemProtocol>* it = (NSObject<ZGItemProtocol>*)[tds itemAtRow: row];
    if (it.children != null) {
        ZGOutlineViewDelegate* d = (ZGOutlineViewDelegate*)_document.outlineView.delegate;
        [d selectItem: it];
    }
}

- (void) tableViewBecameFirstResponder: (NSTableView*) v  {
    if (v.numberOfRows > 0 && v.selectedRowIndexes.count == 0) {
        [v selectRowIndexes: [NSIndexSet indexSetWithIndex: 0] byExtendingSelection: false];
    }
}

@end
