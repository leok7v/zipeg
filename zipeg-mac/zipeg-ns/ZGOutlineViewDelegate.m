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
    bool _queued;
    int _nestedCollapse; // only collapse is nested, expand is sequential
    int _expandCounter;  // onlu collapse is nested, expand is sequential
    ZGDocument* __weak _document;
    ZGSectionCell* _sectionCell;
    void (^_delayedExpand)();
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

-(void) selectItemByNotification: (NSNotification*) n {
    NSOutlineView* v = (NSOutlineView*)n.object;
    NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)n.userInfo[@"NSObject"];
    ZGOutlineViewDelegate* d = (ZGOutlineViewDelegate*)v.delegate;
    [d selectItem: i];
//  trace(@"select %@ %@ %@", v, i, i.name);
}

-(void) sizeToContentByNotification: (NSNotification*) n {
    NSOutlineView* v = (NSOutlineView*)n.object;
    [self sizeOutlineViewToContents: v];
//  NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)n.userInfo[@"NSObject"];
//  trace(@"sizeToContentByNotification %@ %@ %@", v, i, i.name);
}


-(void) postDelayed: (NSNotification*) n {
    int __block counter = _expandCounter;
    ZGOutlineViewDelegate* __weak __block this = self;
    _delayedExpand= ^{
        [this areWeThereYet: counter notification: n];
    };
    dispatch_async(dispatch_get_current_queue(), _delayedExpand);
}

-(void) areWeThereYet: (int) counter notification: (NSNotification*) n {
    if (_expandCounter == counter) {
        _expandCounter = 0;
        _delayedExpand = null;
        [self selectItemByNotification: n];
        [self sizeToContentByNotification: n];
    } else {
        trace("re-postDelayed: %d", _expandCounter);
        [self postDelayed: n];
    }
}

-(void) outlineViewItemWillExpand: (NSNotification*) n {
//  NSOutlineView* v = (NSOutlineView*)n.object;
//  NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)n.userInfo[@"NSObject"];
//  trace(@"willExpand %@ %@ %@", v, i, i.name);
    _expandCounter++;
}

-(void) outlineViewItemDidExpand: (NSNotification*) n {
//  NSOutlineView* v = (NSOutlineView*)n.object;
//  NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)n.userInfo[@"NSObject"];
//  trace(@"didExpand %@ %@ %@ %d", v, i, i.name, _expandCounter);
    _expandCounter++;
    if (_delayedExpand == null) {
        trace("postDelayed: %d\n", _expandCounter);
        [self postDelayed: n];
    }
}

-(void) outlineViewItemWillCollapse: (NSNotification*) n {
//  NSOutlineView* v = (NSOutlineView*)n.object;
//  NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)n.userInfo[@"NSObject"];
//  trace(@"willCollapse %@ %@ %@ %d", v, i, i.name, _nestedCollapse);
    _nestedCollapse++;
}

-(void) outlineViewItemDidCollapse:(NSNotification *) n {
    _nestedCollapse--;
//  NSOutlineView* v = (NSOutlineView*)n.object;
//  NSObject<ZGItemProtocol>* i = (NSObject<ZGItemProtocol>*)n.userInfo[@"NSObject"];
//  trace(@"didCollapse %@ %@ %@ %d", v, i, i.name, _nestedCollapse);
    if (_nestedCollapse == 0) {
        [self selectItemByNotification: n];
        [self sizeToContentByNotification: n];
    }
}

-(BOOL)outlineView: (NSOutlineView*) outlineView shouldShowOutlineCellForItem: (id) i {
    return true;
}

- (BOOL)outlineView:(NSOutlineView *)outlineView isGroupItem:(id)i {
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

- (void)expandAll: (NSOutlineView*) outlineView {
    if (!outlineView.dataSource) {
        return;
    }
    ZGOutlineViewDataSource* ds = outlineView.dataSource;
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
            outlineView.delegate = null;
            [outlineView expandItem: r expandChildren: true];
            outlineView.delegate = self;
        }
    } else {
        trace(@"root=%@ with %ld childs will be expanded", r.name, r.children.count);
        outlineView.delegate = null;
        NSArray* kids = r.children;
        for (int i = 0; i < kids.count; i++) {
            NSObject<ZGItemProtocol>* c = kids[i];
            trace(@"  %@ with %ld childs", c.name, c.children.count);
            if (c.children != null) {
                [outlineView expandItem: c expandChildren: true];
            }
        }
        outlineView.delegate = self;
    }
}

/*
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
*/

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

- (NSObject<ZGItemProtocol>*) selectedItem {
    return _document.outlineView.selectedRow < 0 ? null : [_document.outlineView itemAtRow: _document.outlineView.selectedRow];
}

@end
