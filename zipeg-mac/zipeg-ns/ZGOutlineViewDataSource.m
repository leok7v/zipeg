#import "ZGDocument.h"
#import "ZGOutlineViewDataSource.h"
#import "ZGImageAndTextCell.h"


@interface ZGOutlineViewDataSource () {
    ZGDocument* __weak _document;
    NSObject<ZGItemProtocol>* __weak _root;
}
@end

@implementation ZGOutlineViewDataSource

- (id)initWithDocument: (ZGDocument*) doc andRootItem:(NSObject<ZGItemProtocol>*)root {
    self = [super init];
    if (self) {
        alloc_count(self);
        _root = root;
        _document = doc;
    }
    return self;
}

- (void)dealloc {
    dealloc_count(self);
}

- (id) parentForItem: (id) item {
    NSObject<ZGItemProtocol>* i = item == null ? _root : item;
    return i.parent;
}

- (NSInteger)outlineView:(NSOutlineView *)outlineView numberOfChildrenOfItem:(id)item {
    NSObject<ZGItemProtocol>* i = item == null ? _root : item;
    // trace("%@ %@.folderChildren=%ld parent=%@", item, i.name, i.folderChildren.count, i.parent == null ? @"null" : i.parent.name);
    return i.folderChildren.count;
}

- (BOOL)outlineView:(NSOutlineView *)outlineView isItemExpandable:(id)item {
    NSObject<ZGItemProtocol>* i = item == null ? _root : item;
    // trace("%@.isItemExpandable=%@", i.name, i.folderChildren != null ? @"true" : @"false");
    return i.folderChildren != null;
}

- (id)outlineView:(NSOutlineView *)outlineView child:(NSInteger)index ofItem:(id)item {
    NSObject<ZGItemProtocol>* i = item == null ? _root : item;
    // trace("%@[%ld]=%@", i.name, index, ((NSObject<ZGItemProtocol>*)i.folderChildren[index]).name);
    return i.folderChildren[index];
}

- (id)outlineView:(NSOutlineView *)outlineView objectValueForTableColumn:(NSTableColumn *)tableColumn byItem:(id)item {
    NSObject<ZGItemProtocol>* i = item == null ? _root : item;
    // trace(@"objectValueForTableColumn %@=%@", item, i.name);
    return i.name;
}

#pragma mark -
#pragma mark ***** Optional Methods *****

- (void)outlineView:(NSOutlineView *)outlineView setObjectValue:(id)object forTableColumn:(NSTableColumn *)tableColumn byItem:(id)item {
}

- (id)outlineView:(NSOutlineView *)outlineView itemForPersistentObject:(id)object {
    return nil;
}

- (id)outlineView:(NSOutlineView *)outlineView persistentObjectForItem:(id)item {
    return nil;
}

- (void)outlineView:(NSOutlineView *)outlineView sortDescriptorsDidChange:(NSArray *)oldDescriptors {
}

#pragma mark -
#pragma mark ***** Optional - Drag and Drop support  *****

/* ????????
- (id <NSPasteboardWriting>) outlineView: (NSOutlineView*) v pasteboardWriterForItem: (id) item NS_AVAILABLE_MAC(10_7) {
    return (id <NSPasteboardWriting>)[item representedObject];
}
*/

- (void) outlineView: (NSOutlineView*) v draggingSession: (NSDraggingSession*) s
    willBeginAtPoint: (NSPoint) pt forItems: (NSArray*) items NS_AVAILABLE_MAC(10_7) {
    
}

- (void) outlineView:(NSOutlineView*) v draggingSession:(NSDraggingSession*) s
        endedAtPoint: (NSPoint)  screenPoint operation:(NSDragOperation)operation NS_AVAILABLE_MAC(10_7) {
    
}

- (void) outlineView: (NSOutlineView*) v updateDraggingItemsForDrag: (id<NSDraggingInfo>) di NS_AVAILABLE_MAC(10_7) {
    
}

- (NSDragOperation)outlineView:(NSOutlineView *) v validateDrop: (id<NSDraggingInfo>) di
                  proposedItem: (id)item proposedChildIndex: (NSInteger) index {
    return NSDragOperationNone;
}

- (BOOL)outlineView:(NSOutlineView *)outlineView acceptDrop:(id <NSDraggingInfo>)info item:(id)item childIndex:(NSInteger)index {
    return false;
}

- (BOOL) outlineView: (NSOutlineView*) outlineView writeItems:(NSArray*) items toPasteboard: (NSPasteboard*) pb {
    BOOL b = false;
    for (NSObject<ZGItemProtocol>* it in items) {
        [pb declareTypes: @[NSFilesPromisePboardType, NSFilenamesPboardType, NSStringPboardType] owner: self];
        if ([pb setPropertyList: @[[it.name pathExtension]] forType: NSFilesPromisePboardType]) {
            b = true;
        }
        if ([pb setString: it.name forType: NSStringPboardType]) {
            b = true;
            // trace(@"%@", it.name);
        }
    }
    return b;
}

- (NSArray*) outlineView: (NSOutlineView*) v namesOfPromisedFilesDroppedAtDestination: (NSURL*) d
         forDraggedItems: (NSArray*) items {
    NSMutableArray *urls  = [[NSMutableArray alloc] initWithCapacity: items.count];
    for (NSObject<ZGItemProtocol>* it in items) {
        NSURL* u =[NSURL fileURLWithPath:[[d path] stringByAppendingPathComponent: it.name] isDirectory: false];
        [urls addObject: u.path.lastPathComponent];
    }
    [_document extract: items to: d DnD: true];
    return urls;
}

@end
