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
    // trace(@"");
    dealloc_count(self);
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

- (id <NSPasteboardWriting>)outlineView:(NSOutlineView *)outlineView pasteboardWriterForItem:(id)item NS_AVAILABLE_MAC(10_7) {
    return nil;
}

- (void)outlineView:(NSOutlineView *)outlineView draggingSession:(NSDraggingSession *)session willBeginAtPoint:(NSPoint)screenPoint forItems:(NSArray *)draggedItems NS_AVAILABLE_MAC(10_7) {
    
}

- (void)outlineView:(NSOutlineView *)outlineView draggingSession:(NSDraggingSession *)session endedAtPoint:(NSPoint)screenPoint operation:(NSDragOperation)operation NS_AVAILABLE_MAC(10_7) {
    
}

- (BOOL)outlineView:(NSOutlineView *)outlineView writeItems:(NSArray *)items toPasteboard:(NSPasteboard *)pasteboard {
    return false;
}

- (void)outlineView:(NSOutlineView *)outlineView updateDraggingItemsForDrag:(id <NSDraggingInfo>)draggingInfo NS_AVAILABLE_MAC(10_7) {
    
}

- (NSDragOperation)outlineView:(NSOutlineView *)outlineView validateDrop:(id <NSDraggingInfo>)info proposedItem:(id)item proposedChildIndex:(NSInteger)index {
    return NSDragOperationNone;
}

- (BOOL)outlineView:(NSOutlineView *)outlineView acceptDrop:(id <NSDraggingInfo>)info item:(id)item childIndex:(NSInteger)index {
    return false;
}

- (NSArray *)outlineView:(NSOutlineView *)outlineView namesOfPromisedFilesDroppedAtDestination:(NSURL *)dropDestination forDraggedItems:(NSArray *)items {
    return nil;
}

@end
