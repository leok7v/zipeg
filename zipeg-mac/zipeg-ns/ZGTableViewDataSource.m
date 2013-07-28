#import "ZGDocument.h"
#import "ZGtableViewDataSource.h"
#import "ZGImageAndTextCell.h"


@interface ZGTableViewDataSource () {
    ZGDocument* __weak _document;
    NSObject<ZGItemProtocol>* __weak _item;
    NSMutableArray* _sorted;
}
@end

@implementation ZGTableViewDataSource

- (id) initWithDocument: (ZGDocument*) doc {
    self = [super init];
    _document = doc;
    return self;
}

- (void)dealloc {
    trace(@"");
}

- (NSObject<ZGItemProtocol>*) selectedItem {
    id item = [_document.outlineView itemAtRow: _document.outlineView.selectedRow];
    NSObject<ZGItemProtocol>* it = [item conformsToProtocol: @protocol(ZGItemProtocol)] ?
                                   (NSObject<ZGItemProtocol>*)item : null;
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
    NSObject<ZGItemProtocol>* i = [self selectedItem];
    return i == null ? null : (_sorted != null) ? _sorted : i.children;
}

- (NSInteger) numberOfRowsInTableView: (NSTableView*) tableView {
    NSMutableArray* c = [self children];
    assert(c != null);
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

- (void)tableView:(NSTableView *)tableView sortDescriptorsDidChange:(NSArray *)oldDescriptors {
    NSObject<ZGItemProtocol>* it = [self selectedItem];
    if (it != null) {
        if (_sorted == null) { // has not been sorted yet
            _sorted = [NSMutableArray arrayWithArray:it.children];
        }
        // TODO: sort directories to the top
        [_sorted sortUsingDescriptors: [_document.tableView sortDescriptors]];
        [tableView reloadData];
    }
}

- (NSArray*) tableView: (NSTableView*) tv namesOfPromisedFilesDroppedAtDestination: (NSURL*) dest
             forDraggedRowsWithIndexes: (NSIndexSet*) rowIndexes {
    NSMutableArray *draggedFilenames = [[NSMutableArray alloc] initWithCapacity: rowIndexes.count];
    NSUInteger i = [rowIndexes firstIndex];
    while (i != NSNotFound) {
        NSObject<ZGItemProtocol>* it = [self itemAtRow: i];
        NSString *destPath = [[dest path] stringByAppendingPathComponent: it.name];
        [draggedFilenames addObject:destPath];
        i = [rowIndexes indexGreaterThanIndex: i];
    }
    return draggedFilenames;
}

- (NSArray*) namesOfPromisedFilesDroppedAtDestination: (NSURL*) d {
    return [self tableView: _document.tableView namesOfPromisedFilesDroppedAtDestination:d
 forDraggedRowsWithIndexes: _document.tableView.selectedRowIndexes];
}

- (BOOL)tableView:(NSTableView *)tv writeRowsWithIndexes:(NSIndexSet*) rowIndexes
     toPasteboard:(NSPasteboard*)pboard {
    BOOL retval = NO;
    NSUInteger i = [rowIndexes firstIndex];
    while (i != NSNotFound) {
        NSObject<ZGItemProtocol>* it = [self itemAtRow: i];
        [pboard declareTypes:@[NSFilesPromisePboardType, NSFilenamesPboardType, NSStringPboardType] owner:self];
        if ([pboard setPropertyList:@[[it.name pathExtension]] forType:NSFilesPromisePboardType]) {
            retval = YES;
        }
        if ([pboard setString: it.name forType:NSStringPboardType]) {
            retval = YES;
        }
        i = [rowIndexes indexGreaterThanIndex: i];
    }
    return retval;
}

@end
