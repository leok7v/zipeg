#import "ZGDocument.h"
#import "ZGtableViewDataSource.h"
#import "ZGOutlineViewDelegate.h"

@interface ZGTableViewDataSource () {
    ZGDocument* __weak _document;
    NSObject<ZGItemProtocol>* __weak _item;
    NSMutableArray* _sorted;
}
@end

@implementation ZGTableViewDataSource

- (id) initWithDocument: (ZGDocument*) doc {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        _document = doc;
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (NSObject<ZGItemProtocol>*) selectedItem {
    NSObject<ZGItemProtocol>* it = null;
    if (_document.outlineView.isHidden) {
        it = _document.root.isGroup ? _document.root.children[0] : _document.root;
    } else {
        ZGOutlineViewDelegate* d = _document.outlineView.delegate;
        it = [d selectedItem];
    }
    if (it == null) {
        _item = null;
        _sorted = null;
    } else {
        if (it != _item) {
            _item = it;
            if (_sorted != null) { // it's been sorted remains sorted:
                _sorted = [NSMutableArray arrayWithArray:it.children];
                [_sorted sortUsingDescriptors: [_document.tableView sortDescriptors]];
            }
        }
    }
    return _item;
}

- (NSMutableArray*) children {
    NSObject<ZGItemProtocol>* i = self.selectedItem;
    return i == null ? null : (_sorted != null) ? _sorted : i.children;
}

- (NSInteger) numberOfRowsInTableView: (NSTableView*) tableView {
    NSMutableArray* c = self.children;
// TODO: uncomment assert below
    //    assert(c != null);
    return c != null ? c.count : 0;
}

- (id) tableView: (NSTableView*) tv objectValueForTableColumn: (NSTableColumn*) column row: (NSInteger) row {
    NSMutableArray* c = [self children];
    assert(c != null);
    NSObject<ZGItemProtocol>* it = c[row];
    if (column == tv.tableColumns[0]) {
        return it.name;
    } else {
        return [NSString stringWithFormat:@"info [%ld]", (long)row];
    }
}

- (id) itemAtRow: (NSInteger) row {
    NSMutableArray* c = [self children];
    assert(c != null);
    return c[row];
}

- (void) tableView: (NSTableView*) tableView sortDescriptorsDidChange: (NSArray*) oldDescriptors {
    NSObject<ZGItemProtocol>* it = self.selectedItem;
    if (it != null) {
        // tableView:didClickTableColumn: in ZGTableViewDelegate
        // which sets sortDescriptorPrototype back after it's been null-ified
        NSArray* sortDescriptors = _document.tableView.sortDescriptors;
        // trace(@"old=%@ new=%@", oldDescriptors, sortDescriptors);
        if (oldDescriptors != null && oldDescriptors.count > 0 && sortDescriptors != null && sortDescriptors.count > 0) {
            NSSortDescriptor* o = oldDescriptors[0];
            NSSortDescriptor* n = sortDescriptors[0];
            if (o.ascending == false && n.ascending == true) {
                // instead of sorting remove sort descriptor prototype
                NSTableColumn* tc = tableView.tableColumns[0];
                tc.sortDescriptorPrototype = null; // this will reset header state to unsorted
                _sorted = null;
                [tableView reloadData];
                return;
            }
        }
        if (sortDescriptors != null && sortDescriptors.count > 0) {
            if (_sorted == null) { // has not been sorted yet
                _sorted = [NSMutableArray arrayWithArray:it.children];
            }
            // TODO: sort directories to the top
            [_sorted sortUsingDescriptors: _document.tableView.sortDescriptors];
            [tableView reloadData];
        }
    }
}

- (NSArray*) itemsForRows:  (NSIndexSet*) rowIndexes {
    NSMutableArray *items = [NSMutableArray.alloc initWithCapacity: rowIndexes.count];
    NSUInteger i = [rowIndexes firstIndex];
    while (i != NSNotFound) {
        NSObject<ZGItemProtocol>* it = [self itemAtRow: i];
        [items addObject: it];
        i = [rowIndexes indexGreaterThanIndex: i];
    }
    return items;
}

- (NSArray*) tableView: (NSTableView*) tv namesOfPromisedFilesDroppedAtDestination: (NSURL*) d
             forDraggedRowsWithIndexes: (NSIndexSet*) rowIndexes {
/* TODO: remove me:
    NSMutableArray *urls  = [[NSMutableArray alloc] initWithCapacity: rowIndexes.count];
    NSMutableArray *items = [[NSMutableArray alloc] initWithCapacity: rowIndexes.count];
    NSUInteger i = [rowIndexes firstIndex];
    while (i != NSNotFound) {
        NSObject<ZGItemProtocol>* it = [self itemAtRow: i];
        [items addObject: it];
        NSURL* u =[NSURL fileURLWithPath:[d.path stringByAppendingPathComponent: it.name] isDirectory: false];
        trace(@"it=%@ fileURL=%@", it.description, u);
        [urls addObject: u.path.lastPathComponent];
        i = [rowIndexes indexGreaterThanIndex: i];
    }
    [_document extract: items to: d DnD: true];
    return urls;
*/
    NSArray* items = [self itemsForRows: rowIndexes];
    NSMutableArray *urls  = [[NSMutableArray alloc] initWithCapacity: rowIndexes.count];
    for (NSObject<ZGItemProtocol>* it in items) {
        NSURL* u =[NSURL fileURLWithPath:[d.path stringByAppendingPathComponent: it.name] isDirectory: false];
        trace(@"it=%@ fileURL=%@", it.description, u);
        [urls addObject: u.path.lastPathComponent];
    }
    [_document extract: items to: d DnD: true];
    return urls;
}

- (NSArray*) namesOfPromisedFilesDroppedAtDestination: (NSURL*) d {
    return [self tableView: _document.tableView
                 namesOfPromisedFilesDroppedAtDestination: d
                 forDraggedRowsWithIndexes: _document.tableView.selectedRowIndexes];
}

- (BOOL) tableView: (NSTableView*) v writeRowsWithIndexes: (NSIndexSet*) indices
     toPasteboard:(NSPasteboard*) pb {
    BOOL b = false;
    NSUInteger i = [indices firstIndex];
    while (i != NSNotFound) {
        NSObject<ZGItemProtocol>* it = [self itemAtRow: i];
        [pb declareTypes: @[NSFilesPromisePboardType, NSFilenamesPboardType, NSStringPboardType] owner: self];
        if ([pb setPropertyList: @[[it.name pathExtension]] forType: NSFilesPromisePboardType]) {
            b = true;
        }
        if ([pb setString: it.name forType: NSStringPboardType]) {
            b = true;
            // trace(@"%@", it.name);
        }
        i = [indices indexGreaterThanIndex: i];
    }
    return b;
}

@end
