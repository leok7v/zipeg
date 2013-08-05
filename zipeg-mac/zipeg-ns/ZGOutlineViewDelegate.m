#import "ZGTableViewDelegate.h"
#import "ZGItemProtocol.h"
#import "ZGGenericItem.h"
#import "ZGImages.h"
#import "ZGDocument.h"
#import "ZGItemProtocol.h"
#import "ZGOutlineViewDelegate.h"
#import "ZGOutlineViewDataSource.h"
#import "ZGImageAndTextCell.h"


@interface ZGOutlineViewDelegate () {
    int _nestedCollapse; // only collapse callbacks are nested,
    int _expandCounter;  // expand callbacks are sequential
    ZGDocument* __weak _document;
    ZGSectionCell* _sectionCell;
    ZGBlock* _delayedExpand;
    ZGBlock* _delayedSizeToContent;
    id _windowWillCloseObserver;
}
@end

@implementation ZGOutlineViewDelegate

- (id) initWithDocument: (ZGDocument*) doc {
    self = [super init];
    if (self) {
        alloc_count(self);
        _sectionCell = [ZGSectionCell new];
        _document = doc;
        _windowWillCloseObserver = addObserver(NSWindowWillCloseNotification, _document.window,
            ^(NSNotification* n) {
                _windowWillCloseObserver = removeObserver(_windowWillCloseObserver);
                [self cancelDelayed];
                _document.outlineView.delegate = null;
                _sectionCell = null;
                [_document.tableView removeTableColumn: _document.tableView.tableColumns[0]];

            });
    }
    return self;
}

- (void) dealloc {
    trace(@"%@", self);
    [self cancelDelayed];
    dealloc_count(self);
}

- (void) cancelDelayed {
    _delayedExpand = [_delayedExpand cancel];
    _delayedSizeToContent = [_delayedSizeToContent cancel];
}


- (CGFloat) outlineView: (NSOutlineView*) v heightOfRowByItem: (id) i {
    return [self outlineView: v isGroupItem: i] ? 0 : 18;
}

- (void) outlineViewColumnDidResize:(NSNotification*) n {
    // NSOutlineViewColumnDidResizeNotification @"NSTableColumn", @"NSOldWidth"
//  trace(@"%@", n.userInfo);
}

- (void) outlineViewSelectionIsChanging: (NSNotification *) n {
    // useless because it is only sent on mouse clicks not even on keyboard up/down
}

- (void)outlineViewSelectionDidChange:(NSNotification *)notification {
    ZGTableViewDelegate* d = _document.tableView.delegate;
    [d outlineViewSelectionDidChange];
}

- (void) outlineView: (NSOutlineView *) v willDisplayCell: (NSCell*) c forTableColumn: (NSTableColumn *) tc item: (id) i {
    if ([c isKindOfClass:[ZGImageAndTextCell class]] && [i conformsToProtocol:@protocol(ZGItemProtocol)]) {
        NSObject<ZGItemProtocol>* it = (NSObject<ZGItemProtocol>*)i;
        ZGImageAndTextCell* itc = (ZGImageAndTextCell*)c;
        NSImage* image = null;
        if ([self outlineView: v isGroupItem: i]) {
            image = ZGImages.shared.appImage;
        } else { // TODO: ask ZGImages to retrieve system icon
            image = [_document itemImage: it open: [v isItemExpanded: it]];
        }
        [itc setRepresentedObject: i];
        // trace(@"cell=%@ for %@ %@", c, it, it.name);
        itc.image = image;
        itc.stringValue = it.name;
    }
}

- (BOOL) outlineView:(NSOutlineView *) v shouldSelectItem: (id) item {
    // don't allow special group nodes to be selected
    // NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)item;
    // trace(@"%@ = %d", i.name, ![self outlineView: v isGroupItem: item]);
    BOOL b = ![self outlineView:v isGroupItem:item];
    if (b) {
        [_delayedSizeToContent cancel];
        ZGTableViewDelegate* d = _document.tableView.delegate;
        [d outlineViewSelectionWillChange];
    }
    return b;
}

- (NSCell*) outlineView: (NSOutlineView*) v dataCellForTableColumn: (NSTableColumn*) tc item: (id) i {
    NSCell* c = [tc dataCell];
    // NSObject<ZGItemProtocol>* it = (NSObject<ZGItemProtocol>*)item;
    if ([self outlineView: v isGroupItem: i]) {
        c = _sectionCell;
    }
    return c;
}

-(void) selectItemByNotification: (NSNotification*) n {
    NSOutlineView* v = (NSOutlineView*)n.object;
    NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)n.userInfo[@"NSObject"];
    ZGOutlineViewDelegate* d = (ZGOutlineViewDelegate*)v.delegate;
    [d selectItem: i];
//  trace(@"select %@ %@ %@", v, i, i.name);
}

-(void) sizeToContentByNotification: (NSNotification*) n {
    NSOutlineView* v = (NSOutlineView*)n.object;
    [self sizeToContent: v];
//  NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)n.userInfo[@"NSObject"];
//  trace(@"sizeToContentByNotification %@ %@ %@", v, i, i.name);
}


-(void) postDelayed: (NSNotification*) n {
    int __block counter = _expandCounter;
    ZGOutlineViewDelegate* __weak __block this = self; // TODO: not portable to 10.6
    _delayedExpand= [ZGUtils invokeLater: ^{
        _delayedExpand = null;
        [this areWeThereYet: counter notification: n];
    }];
}

-(void) areWeThereYet: (int) counter notification: (NSNotification*) n {
    if (_expandCounter == counter) {
        _expandCounter = 0;
        [self selectItemByNotification: n];
        [self sizeToContentByNotification: n];
    } else {
//      trace("re-postDelayed: %d", _expandCounter);
        [self postDelayed: n];
    }
}

-(void) outlineViewItemWillExpand: (NSNotification*) n {
    NSOutlineView* v = (NSOutlineView*)n.object;
    NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)n.userInfo[@"NSObject"];
    trace(@"willExpand %@ %@ %@", v, i, i.name);
    _expandCounter++;
}

-(void) outlineViewItemDidExpand: (NSNotification*) n {
//  NSOutlineView* v = (NSOutlineView*)n.object;
//  NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)n.userInfo[@"NSObject"];
//  trace(@"didExpand %@ %@ %@ %d", v, i, i.name, _expandCounter);
    _expandCounter++;
    if (_delayedExpand == null) {
//      trace("postDelayed: %d\n", _expandCounter);
        [self postDelayed: n];
    }
}

-(void) outlineViewItemWillCollapse: (NSNotification*) n {
//  NSOutlineView* v = (NSOutlineView*)n.object;
//  NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)n.userInfo[@"NSObject"];
//  trace(@"willCollapse %@ %@ %@ %d", v, i, i.name, _nestedCollapse);
    _nestedCollapse++;
}

-(void) outlineViewItemDidCollapse: (NSNotification *) n {
    _nestedCollapse--;
//  NSOutlineView* v = (NSOutlineView*)n.object;
//  NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)n.userInfo[@"NSObject"];
//  trace(@"didCollapse %@ %@ %@ %d", v, i, i.name, _nestedCollapse);
    if (_nestedCollapse == 0) {
        [self selectItemByNotification: n];
        [self sizeToContentByNotification: n];
    }
}

-(BOOL)outlineView: (NSOutlineView*) v shouldShowOutlineCellForItem: (id) i {
    return [self outlineView: v isGroupItem: i] ? false : true;
}

- (BOOL)outlineView: (NSOutlineView *) v isGroupItem: (id) i {
    NSObject<ZGItemProtocol>* it = (NSObject<ZGItemProtocol>*)i;
    // trace(@"isGroupItem %@=%@ %d", it.name, it.parent == null ? @"true" : @"false", it.isGroup);
    return it.isGroup;
}

- (BOOL) outlineView:(NSOutlineView *)v shouldEditTableColumn:(NSTableColumn *)tableColumn item:(id)item {
    NSEvent *e = [NSApp currentEvent];
    if (e.type == NSKeyDown && e.keyCode == 48) { // TAB
        // this saves from extending NSOutlineView (for now)
        // http://stackoverflow.com/questions/5600793/nstableview-nsoutlineview-editing-on-tab-key
        // far from perfect rapid Shitf-TABing will start expand items in a tree view... May be later
        return false;
    }
    NSInteger row = [v rowForItem:item];
    if (row >= 0 && [v isRowSelected: row] && ![v isItemExpanded:item]) {
        [v expandItem:item expandChildren:false];
    }
    return false;
}

- (void) expandAll {
    NSOutlineView* v = _document.outlineView;
    if (!v.dataSource) {
        return;
    }
    ZGOutlineViewDataSource* ds = v.dataSource;
    NSObject<ZGItemProtocol>* r = ds.root;
    if ([r isKindOfClass: ZGGenericItem.class]) {
        r = null;
        for (int i = 0; i < ds.root.children.count; i++) {
            if (![ds.root.children[i] isKindOfClass: ZGGenericItem.class]) {
                r = ds.root.children[i];
                break;
            }
        }
        if (r != null) {
            // delegate = null to prevent gazillion of outlineViewItemWillExpand/outlineViewItemDidExpand
            v.delegate = null;
            [v expandItem: r expandChildren: true];
            v.delegate = self;
        }
    } else {
//      trace(@"root=%@ with %ld childs will be expanded", r.name, r.children.count);
        v.delegate = null;
        NSArray* kids = r.children;
        for (int i = 0; i < kids.count; i++) {
            NSObject<ZGItemProtocol>* c = kids[i];
//          trace(@"  %@ with %ld childs", c.name, c.children.count);
            if (c.children != null) {
                [v expandItem: c expandChildren: true];
            }
        }
        v.delegate = self;
    }
}

static BOOL hasFileChild(NSObject<ZGItemProtocol>* it) {
    if (it != null && it.children != null && it.children.count > 0) {
        for (NSObject<ZGItemProtocol>* c in it.children) {
            if (c.children == null) { // selected item already has a file child
                return true;
            }
        }
    }
    return false;
}

- (void) selectFirsFile {
    NSOutlineView* v = _document.outlineView;
    if (hasFileChild(self.selectedItem)) {
        return; // selected item already has a file child
    }
    int rows = (int)v.numberOfRows;
    for (int i = 0; i < rows; i++) {
        NSObject<ZGItemProtocol>* it = [v itemAtRow: i];
        if (hasFileChild(it)) {
            [v selectItem: it];
            return;
        }
    }
}

- (void) _sizeToContent: (NSOutlineView*) v {
    assert(v != null);
    NSSize s = [ZGTableViewDelegate minMaxVisibleColumnContentSize: v columnIndex: 0];
    if (s.width > 0 && s.height > 0) {
        NSTableColumn* tc = v.tableColumns[0];
        tc.width = s.width;
        v.rowHeight = s.height;
        [_document.window.contentView setNeedsDisplay: true];
    }
}

- (void) sizeToContent: (NSOutlineView*) v {
    if (_delayedSizeToContent == null) {
        _delayedSizeToContent = [ZGUtils invokeLater:^(){
            dispatch_async(dispatch_get_current_queue(), ^{
                [self _sizeToContent: v];     // order of this two lines is irrelevant because they executed
                _delayedSizeToContent = null; // inside single iteration of one dispatch loop
            });
        }];
    } else {
        // trace("sizeToContent skipped");
    }
}

- (void) selectItem: (NSObject<ZGItemProtocol>*) it {
    [_document.outlineView selectItem: it];
}

- (NSObject<ZGItemProtocol>*) selectedItem {
    return _document.outlineView.selectedRow < 0 ? null : [_document.outlineView itemAtRow: _document.outlineView.selectedRow];
}

@end
