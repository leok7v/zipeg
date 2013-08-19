#import "ZGDocument.h"
#import "ZGGenericItem.h"
#import "ZG7zip.h"
#import "ZGFileSystem.h"
#import "ZGHeroView.h"
#import "ZGImageAndTextCell.h"
#import "ZGOutlineViewDelegate.h"
#import "ZGTableViewDelegate.h"
#import "ZGOutlineViewDataSource.h"
#import "ZGTableViewDataSource.h"
#import "ZGSplitViewDelegate.h"
#import "ZGWindowController.h"
#import "ZGToolbar.h"
#import "ZGToolbarDelegate.h"
#import "ZGDestination.h"
#import "ZGImages.h"
#import "ZGApp.h"
#import "ZGAlerts.h"
#include <sys/stat.h> // mkdir


/* // TODO: (this is for layout debug and diagnostics) remove me
@interface ZGRedBox : NSView @end

@implementation ZGRedBox
- (void) drawRect: (NSRect) r {
    [NSColor.redColor setFill];
    NSRectFill(r);
    [super drawRect: r];
}
@end
*/

@interface ZGDocument() {
    NSObject<ZGItemFactory>* _archive;
    NSObject<ZGItemProtocol>* _root;
    NSWindow* __weak _window;
    // TODO: all observers should be __weak
    id _windowWillCloseObserver;
    id _clipViewFrameDidChangeObserver;
    id _clipViewBoundsDidChangeObserver;
    NSTableViewSelectionHighlightStyle _highlightStyle;
    BOOL _isNew;
    uint64_t _timeToShowHeroView;
    ZGBlock* _scheduledAlerts;
    int _itemsToExtract;
    int _foldersToExtract;

    NSString* _preNextLastPathComponent;
    NSString* _temporaryUnpackingFolder;
    NSString* _trueDestination;
    NSDictionary* _trashedDestination;
}

@property (weak) NSView* contentView;
@property ZGToolbarDelegate* toolbarDelegate;
@property ZGDestination* destination;
@property NSMenu *tableRowContextMenu;

@property NSTextFieldCell* textCell;
@property ZGOutlineViewDelegate* outlineViewDelegate;
@property ZGOutlineViewDataSource* outlineViewDataSource;
@property ZGTableViewDelegate* tableViewDelegate;
@property ZGTableViewDataSource* tableViewDatatSource;
@property ZGSplitViewDelegate* splitViewDelegate;
@property NSColor* searchTextColor;
@property NSOperationQueue* operationQueue;
@property CFStringEncoding encoding;
@property NSString* typeName;
@property NSError* error;
@property ZGHeroView* heroView;
@property ZGAlerts* alerts;

- (void) openArchiveForOperation: (ZGOperation*) op;
- (void) extractItemsForOperation: (ZGOperation*) op items: (NSArray*) items to: (NSURL*) url DnD: (BOOL) dnd;
- (void) searchArchiveWithString: (NSString*) s forOperation: (ZGOperation*) op done: (void(^)(BOOL)) block;

@end

@interface ZGBackPanel : NSView { ZGDocument* _document; } @end

@implementation ZGBackPanel

- (id) initWithDocument: (ZGDocument*) d andFrame:(NSRect) r {
    self = [super initWithFrame: r];
    if (self) { // it takes space to prevent titlebar with toolbar to over-draw
        self.autoresizingMask = kSizableWH;
        self.autoresizesSubviews = true;
        _document = d;
    }
    return self;
}

@end


@interface OpenArchiveOperation : ZGOperation {
    ZGDocument* __weak _document;
}
- (id)initWithDocument: (ZGDocument*) document;
@end

@implementation OpenArchiveOperation

- (id) initWithDocument: (ZGDocument*) doc {
    self = [super init];
    _document = doc;
    return self;
}

- (void)main {
    [_document openArchiveForOperation: self];
}

@end

@interface SearchArchiveOperation : ZGOperation {
    ZGDocument* __weak _document;
    NSString* _search;
    void (^_block)(BOOL b);
}
- (id)initWithDocument: (ZGDocument*) document searchString: (NSString*) s done: (void(^)(BOOL)) block;
@end

@implementation SearchArchiveOperation

- (id) initWithDocument: (ZGDocument*) doc searchString: (NSString*) s done: (void(^)(BOOL)) block {
    self = [super init];
    _document = doc;
    _search = s.copy;
    _block = block;
    return self;
}

- (void) main {
    [_document searchArchiveWithString: _search forOperation: self done: _block];
}

@end


@interface ExtractItemsOperation : ZGOperation {
    ZGDocument* __weak _document;
    NSArray* _items;
    NSURL* _url;
    BOOL _dnd;
}
- (id) initWithDocument: (ZGDocument*) doc items: (NSArray*) items to: (NSURL*) url DnD: (BOOL) dnd;
@end

@implementation ExtractItemsOperation

- (id) initWithDocument: (ZGDocument*) doc items: (NSArray*) items to: (NSURL*) url DnD: (BOOL) dnd {
    self = [super init];
    if (self != null) {
        _document = doc;
        _items = items;
        _url = url;
        _dnd = dnd;
    }
    return self;
}

- (void)main {
    [_document extractItemsForOperation: self items: _items to: _url DnD: _dnd];
}

@end


@implementation ZGDocument

// See:
// http://stackoverflow.com/questions/16347569/why-arent-the-init-and-windowcontrollerdidloadnib-method-called-when-a-autosav
// and
// http://developer.apple.com/library/mac/#documentation/DataManagement/Conceptual/DocBasedAppProgrammingGuideForOSX/StandardBehaviors/StandardBehaviors.html#//apple_ref/doc/uid/TP40011179-CH5-SW8


+ (BOOL) canConcurrentlyReadDocumentsOfType: (NSString*) typeName {
    return false; // otherwise all -init will be called in concurently on multipe threads on session restore
}

- (id) init {
    self = [super.init ctor];
    return self;
}

- (id)initWithType:(NSString *)typeName error:(NSError **)outError {
    return [super initWithType: typeName error: outError];
}

- (id) initForURL: (NSURL*) absoluteDocumentURL withContentsOfURL: (NSURL*) absoluteDocumentContentsURL
           ofType: (NSString*) typeName error: (NSError**) outError {
    return [super initForURL:absoluteDocumentURL withContentsOfURL:absoluteDocumentContentsURL
                      ofType:typeName error: outError];
}

- (id) initWithContentsOfURL: (NSURL*) absoluteURL ofType: (NSString*) typeName error: (NSError**) outError {
    return [super initWithContentsOfURL: absoluteURL ofType: typeName error: outError];
}

- (void) restoreStateWithCoder: (NSCoder*) state {
    [super restoreStateWithCoder: state];
}

- (id) ctor {
    if (self != null) {
        alloc_count(self);
        self.hasUndoManager = false;
        _operationQueue = NSOperationQueue.new;
        _operationQueue.maxConcurrentOperationCount = 1;
        _encoding = (CFStringEncoding)-1;
        _highlightStyle = NSTableViewSelectionHighlightStyleSourceList;
    }
    return self;
}

- (void)restoreDocumentWindowWithIdentifier: (NSString*) id state: (NSCoder*) state
            completionHandler: (void (^)(NSWindow*, NSError*)) done {
    [super restoreDocumentWindowWithIdentifier: id state: state completionHandler: done];
}

- (void) dealloc {
    trace(@"");
    dealloc_count(self);
    [NSNotificationCenter.defaultCenter removeObserver:self];
    [ZGApp deferedTraceAllocs];
}

- (void) makeWindowControllers {
    assert(_operationQueue != null);
    ZGWindowController* wc = ZGWindowController.new;
    [self addWindowController: wc];
    [self setupDocumentWindow: wc];
}

- (void) reloadData {
    if (_outlineView != null && _archive != null) {
        if (_highlightStyle != NSTableViewSelectionHighlightStyleSourceList) {
            _root = _archive.root;
        } else {
            _root = [ZGGenericItem.alloc initWithChild: _archive.root];
        }
        if (_outlineViewDataSource == null) {
            _outlineViewDataSource = [ZGOutlineViewDataSource.alloc initWithDocument: self andRootItem: _root];
        } else {
            [_outlineViewDelegate cancelDelayed];
            [_tableViewDelegate cancelDelayed];
            _outlineViewDataSource.root = _root;
        }
        if (_archive.numberOfFolders > 0) {
            _outlineView.dataSource = _outlineViewDataSource;
            [_outlineView reloadData];
        } else {
            if (_splitView.subviews.count > 1) {
                _outlineView.hidden = true;
                [_outlineView removeFromSuperview];
                _splitView.subviews = @[_splitView.subviews[1]];
            }
        }
        if (_tableViewDatatSource == null) {
            _tableViewDatatSource = [ZGTableViewDataSource.alloc initWithDocument: self];
            _tableView.dataSource = _tableViewDatatSource;
        }
        [_tableView reloadData];
        dispatch_async(dispatch_get_current_queue(), ^{
            if (![_archive.root isKindOfClass: ZGFileSystemItem.class]) {
                [_outlineViewDelegate expandAll];
            } else { // do not expandAll for the filesystem - will take forever
                [_outlineView expandItem: _root expandChildren: false];
                [_outlineView expandItem: _root.children[0] expandChildren: false];
            }
            [_outlineViewDelegate selectFirsFile];
            if (_archive.numberOfFolders > 0) {
                [self sizeToContent];
            }
            [_tableViewDelegate sizeToContent: _tableView];
        });
        [_toolbar validateVisibleItems];
    }
}

static NSSplitView* createSplitView(NSRect r, NSView* left, NSView* right) {
    NSSplitView* sv = [NSSplitView.alloc initWithFrame: r];
    sv.vertical = true;
    sv.dividerStyle = NSSplitViewDividerStyleThin;
    sv.autoresizingMask = kSizableWH | kSizableLR;
    sv.autoresizesSubviews = true;
    sv.autosaveName = @"Zipeg Split View";
    sv.subviews = @[left, right];
    return sv;
}

static NSScrollView* createScrollView(NSRect r, NSView* v) {
    NSScrollView* sv = [NSScrollView.alloc initWithFrame: r];
    sv.documentView = v;
    sv.hasVerticalScroller = true;
    sv.hasHorizontalScroller = true;
    sv.autoresizingMask = kSizableWH;
    sv.autoresizesSubviews = true;
    return sv;
}

static NSOutlineView* createOutlineView(NSRect r, NSTableViewSelectionHighlightStyle hs) {
    NSOutlineView* ov = [NSOutlineView.alloc initWithFrame: r];
    ov.focusRingType = NSFocusRingTypeNone;
    ov.autoresizingMask = kSizableWH;
    ov.allowsEmptySelection = true; // because of the sections (groups) collapse in Outline View
    ov.indentationMarkerFollowsCell = true;
    ov.indentationPerLevel = 16;
    ov.headerView = null;
    NSTableColumn* tc = NSTableColumn.new;
    [ov addTableColumn: tc];
    ov.outlineTableColumn = tc;
    ZGImageAndTextCell* c = ZGImageAndTextCell.new;
    tc.dataCell = c;
    tc.minWidth = 92;
    tc.maxWidth = 3000;
    tc.editable = true;
    ov.selectionHighlightStyle = hs;
    assert(ov.outlineTableColumn == tc);
    assert(ov.tableColumns[0] == tc);
    [ov setDraggingSourceOperationMask : NSDragOperationCopy forLocal: NO];
    [ov setDraggingSourceOperationMask : NSDragOperationGeneric forLocal: YES];
    [ov registerForDraggedTypes: @[NSFilenamesPboardType, NSFilesPromisePboardType]];
    return ov;
}

static NSTableView* createTableView(NSRect r) {
    NSTableView* tv = [NSTableView.alloc initWithFrame: r];
    tv.focusRingType = NSFocusRingTypeNone;
    NSTableColumn* tableColumn = NSTableColumn.new;
    
    [tv addTableColumn: tableColumn];
    tableColumn.dataCell = ZGImageAndTextCell.new;
    tableColumn.minWidth = 92;
    tableColumn.maxWidth = 3000;
    tableColumn.editable = true;
    assert(tv.tableColumns[0] == tableColumn);
    NSSortDescriptor *sd = [NSSortDescriptor
        sortDescriptorWithKey: @"name" ascending:YES
                     selector: @selector(localizedCaseInsensitiveCompare:)];
    tableColumn.sortDescriptorPrototype = sd;
    tableColumn.resizingMask = NSTableColumnAutoresizingMask | NSTableColumnUserResizingMask;
    for (int i = 1; i < 3; i++) {
        tableColumn = NSTableColumn.new;
        [tv addTableColumn: tableColumn];
        assert(tv.tableColumns[i] == tableColumn);
        tableColumn.dataCell = NSTextFieldCell.new;
        tableColumn.minWidth = 92;
        tableColumn.maxWidth = 3000;
        tableColumn.editable = true;
    }
    
    tv.autoresizingMask = kSizableWH;
    tv.allowsColumnReordering = true;
    tv.allowsColumnResizing = true;
    tv.allowsMultipleSelection = true;
    tv.allowsColumnSelection = false;
    tv.allowsEmptySelection = true; // otherwise deselectAll won't work
    tv.allowsTypeSelect = true;
    tv.usesAlternatingRowBackgroundColors = true;
    //  _tableView.menu = _tableRowContextMenu; // TODO:
    NSFont* font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize - 1];
    for (int i = 0; i < tv.tableColumns.count; i++) {
        NSTableColumn* tc = tv.tableColumns[i];
        NSTableHeaderCell* hc = tc.headerCell;
        hc.font = font;
    }
    
    [tv setDraggingSourceOperationMask : NSDragOperationCopy forLocal: NO];
    [tv setDraggingSourceOperationMask : NSDragOperationGeneric forLocal: YES];
    [tv registerForDraggedTypes: @[NSFilenamesPboardType, NSFilesPromisePboardType]];
    return tv;
}

- (int) viewStyle {
    return _highlightStyle == NSTableViewSelectionHighlightStyleSourceList ? 0 : 1;
}

- (void) setViewStyle: (int) s {
    int hs  = s == 0 ? NSTableViewSelectionHighlightStyleSourceList : NSTableViewSelectionHighlightStyleRegular;
    if (_highlightStyle != hs && !_outlineView.isHidden) {
        _highlightStyle = hs;
        NSRect bounds = _outlineView.bounds;
        _outlineView = createOutlineView(bounds, _highlightStyle);
        assert(_outlineView != null);
        _outlineView.delegate = _outlineViewDelegate;
        _splitView.subviews = @[createScrollView(bounds, _outlineView), _splitView.subviews[1]];
        [_outlineView deselectAll: null];
        [self reloadData];
    }
}

- (NSImage*) itemImage: (NSObject<ZGItemProtocol>*) it open: (BOOL) o {
    if (it.children == null) {
        NSImage* img = [ZGImages iconForFileType16x16: it.name.pathExtension];
        return img == null ? ZGImages.shared.docImage : img;
    } else {
        return o ? ZGImages.shared.dirOpen : ZGImages.shared.dirImage;
    }
}

- (void) setupDocumentWindow: (NSWindowController*) controller { // TODO: rename me
    // setupDocumentWindow is called after readFromURL
    _isNew = _url == null;
    _window = controller.window;
    assert(_window != null);
    _window.collectionBehavior = NSWindowCollectionBehaviorFullScreenPrimary;
    self.window = _window; // weak
    _contentView = _window.contentView;
    _contentView.autoresizingMask = kSizableWH;
    _contentView.autoresizesSubviews = true;
    assert(!_contentView.wantsLayer);
    // NSOutlineView backgrown drawing code is broken if content view wants layer (by my own experiments) and also:
    // http://stackoverflow.com/questions/6638702/nstableview-redraw-not-updating-display-selection-sticking
    NSRect bounds = _contentView.bounds;
    bounds.origin.y += 30;
    bounds.size.height -= 60;
 
    NSRect tbounds = bounds;
    tbounds.size.width /= 2;
    tbounds.origin.x = 0;
    tbounds.origin.y = 0;
    _outlineView = createOutlineView(tbounds, _highlightStyle);
    assert(_outlineView != null);
    _outlineViewDelegate = [ZGOutlineViewDelegate.alloc initWithDocument: self];
    _outlineView.delegate = _outlineViewDelegate;
    
    _tableView = createTableView(tbounds);
    assert(_tableView != null);
    _tableViewDelegate = [ZGTableViewDelegate.alloc initWithDocument: self];
    _tableView.delegate = _tableViewDelegate;
   
    _splitView = createSplitView(bounds,
                                 createScrollView(tbounds, _outlineView),
                                 createScrollView(tbounds, _tableView));
    assert(_splitView != null);
    _splitViewDelegate = [ZGSplitViewDelegate.alloc initWithDocument: self];
    _splitView.delegate = _splitViewDelegate;
    _splitView.hidden = true;
    _heroView = [ZGHeroView.alloc initWithDocument: self andFrame: _contentView.bounds];
    _heroView.autoresizingMask = kSizableWH;
    _heroView.hidden = !_isNew;

    _toolbarDelegate = [ZGToolbarDelegate.alloc initWithDocument: self];
    _toolbar = ZGToolbar.new;
    _toolbar.delegate = _toolbarDelegate; // weak reference
    _window.toolbar = _toolbar;
 
    _alerts = [ZGAlerts.alloc initWithDocument: self];

    NSRect dbounds = _contentView.bounds;
    dbounds.origin.y = dbounds.size.height - 30;
    dbounds.size.height = 30;
    _destination = [ZGDestination.alloc initWithFrame: dbounds for: self];
    
    NSClipView * clipView = _outlineView.enclosingScrollView.contentView;
    clipView.postsFrameChangedNotifications = true;
    void (^sizeToContent)(NSNotification*) = ^(NSNotification* n) {
        [self sizeToContent];
    };
    _clipViewBoundsDidChangeObserver = addObserver(NSViewBoundsDidChangeNotification, clipView, sizeToContent);
    _clipViewFrameDidChangeObserver = addObserver(NSViewFrameDidChangeNotification, clipView, sizeToContent);
    _windowWillCloseObserver = addObserver(NSWindowWillCloseNotification, _window,
        ^(NSNotification* n) {
            _windowWillCloseObserver = removeObserver(_windowWillCloseObserver);
            _clipViewBoundsDidChangeObserver = removeObserver(_clipViewBoundsDidChangeObserver);
            _clipViewFrameDidChangeObserver = removeObserver(_clipViewFrameDidChangeObserver);
        }
    );
    ZGBackPanel* background = [ZGBackPanel.alloc initWithDocument: self andFrame: _contentView.frame];
    _contentView.subviews = @[background];
    background.subviews = @[_splitView, _destination, _heroView];
    if (_isNew) {
        // TODO: how to make document modified without this hack?
        [self performSelector: @selector(_updateDocumentEditedAndAnimate:) withObject: @true];
    } else {
        [self scheduleAlerts];
        _alerts.topText = [NSString stringWithFormat: @"Opening: %@", _url.path.lastPathComponent];
        _timeToShowHeroView = nanotime() + 500 * 1000ULL * 1000ULL; // 0.5 sec
        OpenArchiveOperation *operation = [OpenArchiveOperation.alloc initWithDocument: self];
        [_operationQueue addOperation: operation];
    }
}

- (void) windowDidBecomeKey {
}

- (void) windowDidResignKey {
}

- (void) scheduleAlerts {
    if (_scheduledAlerts == null) {
        _scheduledAlerts = [ZGUtils invokeLater: ^{
            [self beginAlerts];
            _scheduledAlerts = null;
        } delay: 1.0]; // after 1 second
    }
}

- (void) beginAlerts {
    if (!_alerts.isOpen) {
        [_alerts begin];
    }
}

- (void) endAlerts {
    _scheduledAlerts = _scheduledAlerts.cancel;
    if (_alerts.isOpen) {
        [_alerts end];
    }
}

- (void) firstResponderChanged {
    NSResponder* fr = [self.windowControllers[0] window].firstResponder;
    // trace(@"first responder changed to %@", fr);
    if (fr == _tableView) {
        [_tableViewDelegate tableViewBecameFirstResponder: _tableView];
        _lastFirstResponder = _tableView;
    } if (fr == _outlineView) {
        _lastFirstResponder = _outlineView;
    }
}

- (void) sizeToContent {
    [_outlineViewDelegate sizeToContent: _outlineView];
    [_tableViewDelegate sizeToContent: _tableView];
}

+ (BOOL) autosavesInPlace {
    // this is for autosaving documents like text files... see NSDocumentController -setAutosavingDelay:
    return false;
}

- (BOOL) hasUnautosavedChanges {
    return _isNew;
}

- (BOOL) isDocumentEdited {
    return _isNew;
}

- (void) cancelAll {
    [_operationQueue cancelAllOperations];
    [self endAlerts];
    [_operationQueue waitUntilAllOperationsAreFinished];
}

- (void) requestCancel {
    for (ZGOperation* op in _operationQueue.operations) {
        if (op.isExecuting) {
            op.cancelRequested = true;
        }
    }
}

- (BOOL) documentCanClose {
    return _operationQueue.operations.count == 0;
}

- (NSInteger) runModalAlert: (NSString*) message
                    buttons: (NSArray*) buttons
                   tooltips: (NSArray*) tips
                       info: (NSString*) info
                 suppressed: (BOOL*) s {
    NSInteger __block answer = NSAlertErrorReturn;
    NSAlert* a = NSAlert.new;
    int i = 0;
    for (NSString* s in buttons) {
        NSButton* btn = [a addButtonWithTitle: s];
        if (tips != null && i < tips.count && tips[i] != null && ((NSString*)tips[i]).length > 0) {
            btn.toolTip = tips[i];
        }
        i++;
    }
    a.messageText = message;
    a.informativeText = info;
    a.alertStyle = NSInformationalAlertStyle;
    a.icon = [NSImage imageNamed: @"transparent-1x1.png"];
    a.showsSuppressionButton = s != null;
    if (s != null) {
        a.suppressionButton.toolTip = @"You can re-enable suppressed alerts by choosing\n"
                                       "\"ask\" instead of \"always\" under \"Unpack\" button.";
    }
    [self beginAlerts];
    [_alerts alert: a done: ^(NSInteger rc) {
        answer = rc;
        [NSApp stopModal];
    }];
    // https://developer.apple.com/library/mac/documentation/Cocoa/Conceptual/Sheets/Tasks/UsingAppModalDialogs.html
    [NSApp runModalForWindow: _alerts];
    [self endAlerts];
    if (s != null) {
        *s = a.suppressionButton.state == NSOnState;
    }
    return answer;
}

- (void) alertModalSheet: (NSString*) message
                 buttons: (NSArray*) buttons
                tooltips: (NSArray*) tips
                    info: (NSString*) info
              suppressed: (BOOL*) s
                    done: (void(^)(NSInteger rc)) d {
    NSAlert* a = NSAlert.new;
    int i = 0;
    for (NSString* s in buttons) {
        NSButton* btn = [a addButtonWithTitle: s];
        if (tips != null && i < tips.count && tips[i] != null && ((NSString*)tips[i]).length > 0) {
            btn.toolTip = tips[i];
        }
        i++;
    }
    a.messageText = message;
    a.informativeText = info;
    a.alertStyle = NSInformationalAlertStyle;
    a.icon = [NSImage imageNamed: @"transparent-1x1.png"];
    a.showsSuppressionButton = s != null;
    [self beginAlerts];
    [_alerts alert: a done: ^(NSInteger rc) {
        d(rc);
    }];
}

- (void)close {
    // NSWindowController _windowDidClose will call us recursively from super :(
    if (_splitView != null) {
        NSTableColumn* tc = _tableView.tableColumns[0];
        assert(tc != null);
        [self cancelAll];
        _splitView.subviews = @[];
        [_splitView removeFromSuperview];
        _splitView = null;
        [_heroView removeFromSuperview];
        _heroView = null;
        _outlineView = null;
        _tableView = null;
        _destination = null;
        _contentView.subviews = @[];
        if (_archive != null) {
            [_archive close];
            _archive = null;
            _root = null;
        }
        [super close];
    }
}

- (BOOL) writeToURL:(NSURL*) absoluteURL ofType: (NSString*) typeName error: (NSError**) outError {
    [self runModalSavePanelForSaveOperation: NSSaveOperation
                                   delegate: self
                            didSaveSelector: @selector(document:didSave:block:)
                                contextInfo: (__bridge void *)(^(){
        trace(@"save");
    })];
    return true;
}

- (void) document: (NSDocument*) doc didSave: (BOOL) b block: (void (^)())block {
    block();
}


- (BOOL) readFromURL: (NSURL*) absoluteURL ofType: (NSString*) typeName error: (NSError**) outError {
    return [self readFromURL:absoluteURL ofType:typeName encoding:(CFStringEncoding)-1 error:outError];
}

- (BOOL) readFromURL: (NSURL*) absoluteURL ofType: (NSString*) typeName encoding: (CFStringEncoding) encoding
        error:(NSError **)error {
    // this is called before window is created or setup
    _url = absoluteURL;
    _encoding = encoding;
    _typeName = typeName;
    return true;
}

- (BOOL) isEntireFileLoaded {
    return _archive != null;
}

- (void) extract {
    NSURL* u = [NSURL fileURLWithPath: _destination.URL.path isDirectory: true];
    NSArray* items = null;
    if (_destination.isSelected) {
        if (_lastFirstResponder == _tableView) {
            items = [_tableViewDatatSource itemsForRows: _tableView.selectedRowIndexes];
        } else if (!_outlineView.isHidden && _lastFirstResponder == _outlineView) {
            NSObject<ZGItemProtocol>* it = [_outlineViewDelegate selectedItem];
            if (it != null) {
                items = @[it];
            }
        }
    }
    [self extract: items to: u DnD: false];
}

static int numberOfLeafs(NSArray* items, int* numberOfFolders) {
    int n = 0;
    for (NSObject<ZGItemProtocol>* it in items) {
        if (it.children == null) {
            n++; // leaf
        } else {
            n += numberOfLeafs(it.children, numberOfFolders);
            (*numberOfFolders)++;
        }
    }
    return n;
}

static NSString* nonexistentName(NSString* name, NSString* ext, int64_t value) {
    assert(value > 0);
    NSString* res = [name stringByAppendingFormat:@"%lld", value];
    if (ext != null && ext.length > 0) {
        res = [res stringByAppendingPathExtension: ext];
    }
    BOOL e = [NSFileManager.defaultManager fileExistsAtPath: res isDirectory: null];
    return !e ? res : null;
}

static NSString* nextPathname(NSString* path) {
    NSString* ext = path.pathExtension;
    NSString* name = path.stringByDeletingPathExtension;
    int64_t n = -1;
    int spix = [name lastIndexOf: @" "];
    if (spix >= 0) {
        int k = spix + 1;
        if (k < name.length && isdigit([name characterAtIndex: k])) {
            n = [name characterAtIndex: k] - '0';
            k++;
            while (k < name.length && isdigit([name characterAtIndex:k])) {
                n = n * 10 + [name characterAtIndex: k] - '0';
                k++;
            }
            if (k == name.length) {
                // the name is in format: "bla-bla-bla 12345"
            } else {
                n = -1;
            }
        }
    }
    if (n >= 0) {
        name = [name substringFrom: 0 to: spix + 1];
    } else {
        // IMPORTANT: make sure this is the same number as in FilePathAutoRename.cpp
        // TODO: make two implementation into one via delegate - less fragile
        n = 1; // Finder starts with " 2" (which may be a bug or a feature) we will start with " 1"
        name = [name stringByAppendingString: @" "];
    }
    // This is what "Finder.app" does on OS X 10.8
    bool b = true;
    while (b && n < (1LL << 62)) {
        b = nonexistentName(name, ext, n) == null;
        if (b) { // file exists
            n = n + n;
        }
    }
    if (b) {
        return null;
    }
    int64_t left = n / 2 + 1, right = n;
    while (left < right) {
        int64_t mid = (left + right) / 2;
        if (nonexistentName(name, ext, mid) == null) {
            left = mid + 1;
        } else {
            right = mid;
        }
    }
    return nonexistentName(name, ext, right);
}

- (BOOL) extractToNonexistentFolder: (NSString*) path {
    BOOL e = [NSFileManager.defaultManager fileExistsAtPath: path];
    assert(!e);
    if (e) {
        return false;
    }
    _trueDestination = path;
    NSString* parent = path.stringByDeletingLastPathComponent;
    int32_t pid = getpid();
    for (;;) {
        int64_t time = nanotime();
        NSString* temp = [NSString stringWithFormat: @".zipeg.%d.%lld.%@", pid, time, path.lastPathComponent];
        _temporaryUnpackingFolder = [parent stringByAppendingPathComponent: temp];
        if (![NSFileManager.defaultManager fileExistsAtPath: _temporaryUnpackingFolder]) {
            break;
        }
    }
    [ZGApp registerUnpackingFolder: _temporaryUnpackingFolder to: _trueDestination];
    if (!isEqual(ZGApp.allUnpackingFolders[_temporaryUnpackingFolder], _trueDestination)) {
        assert(false);
        return false;
    }
    return mkdir(_temporaryUnpackingFolder.UTF8String, 0700) == 0;
}

- (void) posixError: (NSString*) path {
    NSError* e = [NSError errorWithDomain: NSPOSIXErrorDomain
                                     code: errno
                                 userInfo: @{NSFilePathErrorKey : path}];
    NSAlert* a = [NSAlert alertWithError: e];
    [self beginAlerts];
    [_alerts alert: a done: ^(NSInteger rc) { [self endAlerts]; }];
}

- (void) extract: (NSArray*) items to: (NSURL*) dest DnD: (BOOL) dnd {
    // TODO: __MACOSX/.../._.DS_Store (resource fork!) still exists!
    assert(_trueDestination == null);
    assert(_temporaryUnpackingFolder == null);
    _trueDestination = null;
    _temporaryUnpackingFolder = null;
    if (items == null) {
        _itemsToExtract = _archive.numberOfItems - _archive.numberOfFolders;
        _foldersToExtract = _archive.numberOfFolders;
    } else {
        _foldersToExtract = 0;
        _itemsToExtract = numberOfLeafs(items, &_foldersToExtract);
    }
    if (dnd) {
        [self addExtractOperation: items to: dest DnD: dnd];
        return;
    }
    if (!_destination.isSelected) {
        items = null;
    }
    if (_destination.isNextToArchive) {
        dest = [NSURL fileURLWithPath: [_url.path stringByDeletingLastPathComponent]];
        trace("url=%@", dest);
    }
    NSString* lpc = _url.path.lastPathComponent.stringByDeletingPathExtension;
    dest = [NSURL fileURLWithPath:[dest.path stringByAppendingPathComponent: lpc]];
    trace("url=%@", dest);
    NSString* path = dest.path;
    BOOL d = false;
    BOOL e = [NSFileManager.defaultManager fileExistsAtPath: path isDirectory: &d];
    NSString* details = @"";
    NSString* files = @"";
    if (_itemsToExtract > 1) {
        files = [NSString stringWithFormat:@"%d files", _itemsToExtract];
    } else if (_itemsToExtract > 0) {
        files = @"one file";
    }
    if (_foldersToExtract > 1) {
        details = [NSString stringWithFormat:@"%@ in %d folders from\n", files, _foldersToExtract];
    } else {
        details = [NSString stringWithFormat:@"%@ from\n", files];
    }
    if (!e) {
        NSInteger rc = NSAlertFirstButtonReturn;
        if (_destination.isAsking) {
            BOOL suppress = false;
            rc = [self runModalAlert: [NSString stringWithFormat:
                                       @"About to unpack %@«%@»\ninto folder:\n«%@»\n"
                                       "Do you want to proceed?", details, _url.path.lastPathComponent, dest.path]
                             buttons: @[ @"Proceed", @"Stop" ]
                            tooltips: null
                                info: @"Destination folder does not exist. It will be created.\n"
                          suppressed: &suppress];
            if (suppress) {
                _destination.asking = false;
            }
        }
        if (rc == NSAlertFirstButtonReturn) {
            if ([self extractToNonexistentFolder: path]) {
                dest = [NSURL fileURLWithPath: _temporaryUnpackingFolder];
                [self addExtractOperation: items to: dest DnD: dnd];
                return;
            } else {
                [self posixError: path];
            }
        }
    } else { // there is a folder or file in a way, need new name
        NSInteger rc = NSAlertFirstButtonReturn;
        NSString* next = nextPathname(path);
        _preNextLastPathComponent = path.lastPathComponent; // without any _number suffixes
        if (_destination.isAsking) {
            BOOL suppress = false;
            NSString* keepTooltip = [NSString stringWithFormat:
                                      @"\"Keep Both\" will change the destination to: "
                                      "«%@»", next];
            NSString* replaceTooltip = [NSString stringWithFormat:
                                      @"\"Replace\" will move existing folder\n«%@»\ninto Trash Bin "
                                        "and will unpack items\ninto newly created folder.",
                                        dest.path.lastPathComponent];
            NSString* mergeTooltip = @"\"Merge\" will unpack items into existing folder\n"
                                      "merging them over already existing items.\n"
                                      "Use with caution. \"Keep Both\" is much safer alternative.";
            rc = [self runModalAlert: [NSString stringWithFormat:
                                       @"About to unpack %@«%@» into existing folder:\n«%@»?\n"
                                       "How do you want to proceed?", details, _url.path.lastPathComponent, dest.path]
                             buttons: @[ @"Keep Both", @"Replace", @"Merge", @"Stop" ]
                            tooltips: @[ keepTooltip, replaceTooltip, mergeTooltip, @"Do not unpack" ]
                                info: @"Hover over buttons for more detailed explanaition.\n"
                                       "(isn't it easier just to drag and drop sometimes?)\n"
                          suppressed: &suppress];
            if (suppress) {
                _destination.asking = false;
            }
        }
        if (rc == NSAlertFirstButtonReturn) { // Keep Both
            path = next;
            if ([self extractToNonexistentFolder: path]) {
                dest = [NSURL fileURLWithPath: _temporaryUnpackingFolder];
                [self addExtractOperation: items to: dest DnD: dnd];
                return;
            } else {
                [self posixError: path];
            }
        } else if (rc == NSAlertSecondButtonReturn) { // Replace
            [NSWorkspace.sharedWorkspace recycleURLs: @[[NSURL fileURLWithPath: path]]
                                   completionHandler:
                 ^(NSDictionary* moved, NSError *error) {
                     // TODO: it should be done later when unpack finished. this will eliminate _trashedDestination
                     trace("moved=%@ %@", moved, error);
                     if (error == null) {
                         _trashedDestination = moved;
                         trace("url=%@", [NSURL fileURLWithPath: path]);
                         BOOL e = [NSFileManager.defaultManager fileExistsAtPath: path isDirectory: null];
                         assert(!e);
                         if ([self extractToNonexistentFolder: path]) {
                             NSURL* dest = [NSURL fileURLWithPath: _temporaryUnpackingFolder];
                             [self addExtractOperation: items to: dest DnD: dnd];
                             return;
                         } else {
                             [self posixError: path];
                         }
                     } else {
                         NSAlert* a = [NSAlert alertWithError: error];
                         [self beginAlerts];
                         [_alerts alert: a done: ^(NSInteger rc) { [self endAlerts]; }];
                     }
                     assert(_operationQueue.operationCount == 0);
                     _trueDestination = null;
                     _temporaryUnpackingFolder = null;
                 }
            ];
            return;
        } else if (rc == NSAlertThirdButtonReturn) { // Merge
            dest = [NSURL fileURLWithPath: path];
            trace("url=%@", dest);
            [self addExtractOperation: items to: dest DnD: dnd];
            return;
        } else { // Stop
        }
    }
    assert(_operationQueue.operationCount == 0);
    _trueDestination = null;
    _temporaryUnpackingFolder = null;
}

- (void) addExtractOperation: (NSArray*) items to: (NSURL*) url DnD: (BOOL) dnd {
    trace("_temporaryUnpackingFolder=%@", _temporaryUnpackingFolder);
    trace("_trueDestination=%@", _trueDestination);
    trace("_trashedDestination=%@", _trashedDestination);
    trace("_url=%@", url.debugDescription);
    ExtractItemsOperation* operation = [ExtractItemsOperation.alloc
                                        initWithDocument: self
                                        items: items
                                        to: url
                                        DnD: dnd];
    [_operationQueue addOperation: operation];
}

- (void) extractItemsForOperation: (ZGOperation*) op items: (NSArray*) items to: (NSURL*) url DnD: (BOOL) dnd {
    // This method is called on the background thread
    assert(![NSThread isMainThread]);
    assert(_archive != null);
    if (items == null || items.count == 0) {
        [_outlineView deselectAll: null]; // remove confusing selection
    }
    [self scheduleAlerts];
    _alerts.topText = [NSString stringWithFormat: @"Unpacking: %@", _url.path.lastPathComponent];
    [_archive extract: items to: url operation: op done: ^(NSError* error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            _alerts.topText = _url.path.lastPathComponent;
            [_alerts progress: 0 of: 0];
            if (error != null) {
                [self restoreTrashed];
                [[NSSound soundNamed: @"error"] play];
                NSAlert* a = [NSAlert alertWithError: error];
                [self beginAlerts];
                [_alerts alert: a done: ^(NSInteger rc) { [self endAlerts]; }];
            } else {
                [self moveToTrueDestination];
                // http://cocoathings.blogspot.com/2013/01/playing-system-sounds.html
                // see: /System/Library/Sounds
                // Basso Blow Bottle Frog Funk Glass Hero Morse Ping Pop Purr Sosumi Submarine Tink
                [[NSSound soundNamed:@"done"] play];
                [self endAlerts];
                if (_destination.isReveal && !dnd) { // Reveal in Finder
                    [NSWorkspace.sharedWorkspace openURLs: @[url]
                                  withAppBundleIdentifier: @"com.apple.Finder"
                                                  options: NSWorkspaceLaunchDefault
                           additionalEventParamDescriptor: null
                                        launchIdentifiers: null];
                }
                [_window makeFirstResponder: _lastFirstResponder];
            }
            _trueDestination = null;
            _temporaryUnpackingFolder = null;
            _trashedDestination = null;
        });
    }];
}

- (void) restoreTrashed {
    if (_trashedDestination != null) {
        BOOL b = true;
        assert(_trashedDestination.count == 1); // we are not expecting more than one folder being trashed
        for (NSString* from in _trashedDestination.allKeys) {
            NSString* to = _trashedDestination[from];
            b = rename(to.UTF8String, from.UTF8String) == 0;
        }
        if (!b) {
            // TODO: report errno
        }
        if (_temporaryUnpackingFolder != null) {
            // TODO: this will not work - need recursive rmdir
            rmdir(_temporaryUnpackingFolder.UTF8String);
        }
    }
    if (_temporaryUnpackingFolder != null) {
        [ZGApp unregisterUnpackingFolder: _temporaryUnpackingFolder];
    }
}

- (void) moveToTrueDestination {
    if (_trueDestination == null && _temporaryUnpackingFolder == null) {
        return;
    }
    // TODO: this might be a good place to collapse .../attachment/attachment/... in the file system
    NSArray* c = [NSFileManager.defaultManager contentsOfDirectoryAtPath: _temporaryUnpackingFolder error: null];
    NSString* cp = c != null && c.count == 1 ? [_temporaryUnpackingFolder stringByAppendingPathComponent: c[0]] : null;
    // TODO: this does not work for next()-ed folder names!
    BOOL collapse = cp != null &&
                   ([_trueDestination.lastPathComponent equalsIgnoreCase: c[0]] ||
                    [_preNextLastPathComponent equalsIgnoreCase: c[0]]);
    BOOL b = true;
    if (collapse) {
        b = rename(cp.UTF8String, _trueDestination.UTF8String) == 0;
        rmdir(_temporaryUnpackingFolder.UTF8String);
    } else {
        b = rename(_temporaryUnpackingFolder.UTF8String, _trueDestination.UTF8String) == 0;
    }
    if (!b) {
        [self restoreTrashed];
        // TODO: report errno
    }
    [ZGApp unregisterUnpackingFolder: _temporaryUnpackingFolder];
}

- (int) askOnBackgroundThreadOverwriteFrom: (const char*) fromName time: (int64_t) fromTime size: (int64_t) fromSize
                                        to: (const char*) toName time: (int64_t) toTime size: (int64_t) toSize {
    assert(![NSThread isMainThread]);
    dispatch_semaphore_t sema = dispatch_semaphore_create(0);
    int __block answer = NSAlertErrorReturn;
    dispatch_async(dispatch_get_main_queue(), ^{
        assert([NSThread isMainThread]);
        NSString* name = [NSString stringWithUTF8String: fromName];
        [self askOverwrite: name appltToAll: _itemsToExtract > 1 done:^(int r) {
            answer = r;
            dispatch_semaphore_signal(sema);
        }];
    });
    dispatch_semaphore_wait(sema, DISPATCH_TIME_FOREVER);
    dispatch_release(sema);
    sema = null;
    return answer;
}

- (void) askOverwrite: (NSString*) name appltToAll: (BOOL) ata done: (void (^)(int)) done {
    NSAlert* a = NSAlert.new;
    NSString* next = nextPathname(name);
    NSString* keepTooltip = [NSString stringWithFormat: @"\"Keep Both\" will append new version number "
                             "to the destination item:\n%@", next.lastPathComponent];
    NSString* replaceTooltip = [NSString stringWithFormat:
                                @"\"Replace\" will move existing file\n«%@»\ninto Trash.",
                                name.lastPathComponent];
    NSString* skipTooltip = @"\"Skip\" will skip unpacking this item and will keep existing file as it is.";
    [a addButtonWithTitle: @"Keep Both"].toolTip = keepTooltip;
    [a addButtonWithTitle: @"Replace"].toolTip = replaceTooltip;
    if (ata) {
        [a addButtonWithTitle: @"Skip"].toolTip = skipTooltip;
    }
    [a addButtonWithTitle: @"Stop"].toolTip = @"Will stop unpacking any further items.";
    [a setMessageText: [NSString stringWithFormat:@"Overwrite file\n«%@»?",  name]];
    [a setInformativeText: @"Hover over buttons for more detailed explanaition.\n"
                            "Overwritten files are placed into Trash Bin."];
    a.alertStyle = NSInformationalAlertStyle;
    NSButton* applyToAll = [NSButton.alloc initWithFrame:NSMakeRect(0, 0, 100, 24)];
    applyToAll.title = @"Apply to All";
    applyToAll.buttonType = NSSwitchButton;
    applyToAll.toolTip = @"This choice will be applied to the rest\n"
                          "of the items that are being unpacked.";
    [applyToAll sizeToFit];
    if (ata) {
        a.accessoryView = applyToAll;
    }
    [self beginAlerts];
    [_alerts alert: a done: ^(NSInteger rc){
        int answer = kCancel;
        if (rc == NSAlertFirstButtonReturn) {
            answer = applyToAll.state == NSOffState ? kKeepBoth : kKeepBothToAll;
        } else if (rc == NSAlertSecondButtonReturn) {
            answer = applyToAll.state == NSOffState ? kYes : kYesToAll;
        } else if (rc == NSAlertThirdButtonReturn) {
            if (ata) {
                answer = applyToAll.state == NSOffState ? kNo : kNoToAll;
            } else {
                answer = kCancel;
            }
        } else {
            answer = kCancel;
        }
        a.accessoryView = null;
        done(answer);
    }];
}

- (BOOL) moveToTrash: (const char*) pathname {
    // timestamp("moveToTrash");
    assert(![NSThread isMainThread]);
    dispatch_semaphore_t sema = dispatch_semaphore_create(0);
    BOOL __block b = false;
    dispatch_async(dispatch_get_main_queue(), ^{
        assert([NSThread isMainThread]);
        NSString* name = [NSString stringWithUTF8String: pathname];
        [NSWorkspace.sharedWorkspace recycleURLs: @[[NSURL fileURLWithPath: name]]
                               completionHandler: ^(NSDictionary* moved, NSError *error){
                                   // trace("moved=%@ %@", moved, error);
                                   b = error == null;
                                   dispatch_semaphore_signal(sema);
                               }];
    });
    dispatch_semaphore_wait(sema, DISPATCH_TIME_FOREVER);
    dispatch_release(sema);
    sema = null;
    // timestamp("moveToTrash"); // 1.5 - 6 milliseconds
    return b;
}

- (void) openArchiveForOperation: (ZGOperation*) op {
    // This method is called on the background thread
    assert(![NSThread isMainThread]);
    NSObject<ZGItemFactory>* __block a = ZG7zip.new;
    NSError* __block error;
    BOOL b = [a readFromURL: _url ofType: _typeName encoding: _encoding
                   document: self operation: (ZGOperation*) op error: &error
                       done: ^(NSObject<ZGItemFactory>* a, NSError* error) {
                       }
              ];
    if (!b) {
        a = null;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        assert([NSThread isMainThread]);
        _alerts.topText = _url.path.lastPathComponent;
        [_alerts progress: 0 of: 0];
        if (a != null) {
            [self endAlerts];
            _archive = a;
            // _archive = ZGFileSystem.new;
            [_window setTitle: a.root.name]; // the DnD of title icon will still show filename.part1.rar
            [self reloadData];
            _heroView.hidden = true;
            _timeToShowHeroView = 0;
            _splitView.hidden = false;
            // TODO: or table view if outline view is hidden
            [[self.windowControllers[0] window] makeFirstResponder: _outlineView];
        } else if (error != null && !op.isCancelled) {
            _heroView.hidden = false;
            NSAlert* a = [NSAlert alertWithError: error];
            [self beginAlerts];
            [_alerts alert: a done: ^(NSInteger rc) {
                [self endAlerts];
                [_window performClose: _window];
            }];
        } else {
            // error == null - aborted by user
            [self endAlerts];
            [_window performClose: _window];
        }
    });
}

- (NSString*) askOnBackgroundThreadForPassword {
    assert(![NSThread isMainThread]);
    dispatch_semaphore_t sema = dispatch_semaphore_create(0);
    NSString* __block password;
    dispatch_async(dispatch_get_main_queue(), ^{
        assert([NSThread isMainThread]);
        // there are 2 points in time when we may be asked for password.
        // during archive opening and during extraction. Do not show hero
        // screen at the second scenario.
        _heroView.hidden = _archive != null;
        NSString* prompt = [NSString stringWithFormat: @"Please enter archive password for archive\n«%@»",
                            _url.path.lastPathComponent];
        NSString* info = @"Password has been set by the person who created this archive file.\n"
        "If you don`t know the password please contact that person.";
        NSAlert* a = [NSAlert alertWithMessageText: prompt
                                     defaultButton: @"OK"
                                   alternateButton: @"Cancel"
                                       otherButton: null
                         informativeTextWithFormat: @"%@", info];
        a.alertStyle = NSInformationalAlertStyle;
        NSTextField* password_input = [NSTextField.alloc initWithFrame: NSMakeRect(0, 0, 300, 24)];
        password_input.autoresizingMask = NSViewWidthSizable | NSViewMaxXMargin | NSViewMinXMargin;
        NSCell* cell = password_input.cell;
        cell.usesSingleLineMode = true;
        password_input.stringValue = @"";
        a.accessoryView = password_input;
        [self beginAlerts];
        [_alerts alert: a done: ^(NSInteger rc){
            if (rc == NSAlertDefaultReturn) {
                [password_input validateEditing];
                password = password_input.stringValue;
            } else {
                password = @"";
            }
            a.accessoryView = null;
            dispatch_semaphore_signal(sema);
        }];
    });
    dispatch_semaphore_wait(sema, DISPATCH_TIME_FOREVER);
    dispatch_release(sema);
    sema = null;
    return password;
}

- (BOOL) askOnBackgroundThreadForCancel {
    assert(![NSThread isMainThread]);
    dispatch_semaphore_t sema = dispatch_semaphore_create(0);
    BOOL __block answer = false;
    dispatch_async(dispatch_get_main_queue(), ^{
        assert([NSThread isMainThread]);
        if (_operationQueue.operationCount > 0) {
            NSString* info = _archive == null ? @"" : // opening archive - no data corruption expected
            @"(Some folders and file may be left behind corrupted or incomplete.)";
            NSAlert* a = [NSAlert alertWithMessageText: @"Do you want to cancel current operation?"
                                         defaultButton: @"Stop"
                                       alternateButton: @"Keep Going"
                                           otherButton: null
                             informativeTextWithFormat: @"%@", info];
            a.alertStyle = NSInformationalAlertStyle;
            [self beginAlerts];
            [_alerts alert: a done: ^(NSInteger rc) {
                answer = rc == NSAlertDefaultReturn;
                dispatch_semaphore_signal(sema);
            }];
        }
    });
    dispatch_semaphore_wait(sema, DISPATCH_TIME_FOREVER);
    dispatch_release(sema);
    sema = null;
    return answer;
}

- (void) checkTimeToShowHeroView {
    if (_timeToShowHeroView > 0 && nanotime() > _timeToShowHeroView) {
        _timeToShowHeroView = 0;
        dispatch_async(dispatch_get_main_queue(), ^{
            _heroView.hidden = false;
            _heroView.needsDisplay = true;
        });
    }
}

- (BOOL) progressOnBackgroundThread: (int64_t) pos ofTotal: (int64_t) total {
    assert(![NSThread isMainThread]);
    // trace(@"%llu of %llu", pos, total);
    [self checkTimeToShowHeroView];
    if (total > 0 && 0 <= pos && pos <= total) {
        dispatch_async(dispatch_get_main_queue(), ^{
            [_alerts progress: pos of: total];
        });
    }
    return true; // TODO: may read the state of cancel button even from background thread
}

- (BOOL) progressFileOnBackgroundThread: (int64_t) fileno ofTotal: (int64_t) totalNumberOfFiles {
    assert(![NSThread isMainThread]);
    // TODO: connect to progress bar(s)
    // trace(@"%llu of %llu", fileno, totalNumberOfFiles);
    [self checkTimeToShowHeroView];
    if (totalNumberOfFiles > 0 && 0 <= fileno && fileno <= totalNumberOfFiles) {
        dispatch_async(dispatch_get_main_queue(), ^{
            [_alerts progress: fileno of: totalNumberOfFiles];
        });
    }
    return true; // TODO: may read the state of cancel button even from background thread
}

- (void) search: (NSString*) s {
    assert([NSThread isMainThread]);
    if (_archive == null) {
        return;
    }
    ZGDocument* __block __weak that = self;
    SearchArchiveOperation* op = [SearchArchiveOperation.alloc
      initWithDocument: self searchString: s
        done: (^(BOOL found){
            assert([NSThread isMainThread]);
            [that reloadData];
            if (_searchTextColor == null && _toolbarDelegate.searchFieldOutlet != null) {
                 _searchTextColor = _toolbarDelegate.searchFieldOutlet.textColor;
            }
            if (_searchTextColor != null && _toolbarDelegate.searchFieldOutlet != null) {
                _toolbarDelegate.searchFieldOutlet.textColor = found ? _searchTextColor : NSColor.redColor;
            }
        })];
    [_operationQueue addOperation: op];
}

- (void) searchArchiveWithString: (NSString*) s forOperation: (ZGOperation*) op done: (void(^)(BOOL)) block {
    assert(![NSThread isMainThread]);
    [_archive setFilter: s operation: op done: block];
}

- (BOOL)writeSelectionToPasteboard:(NSPasteboard *)pasteboard {
    // This method will be called for services, or for drags originating from the preview column ZipEntryView, and it calls the previous method
    return false; // TODO: depending on FirstResponder being table or outline view
}

+ (void)registerServices {
    static BOOL registeredServices = false;
    if (!registeredServices) {
        [NSApp setServicesProvider: self];
        registeredServices = true;
    }
}

+ (void)exportData:(NSPasteboard *)pasteboard userData:(NSString *)data error:(NSString **)error {
    // TODO:
    ZGDocument *d = [[[NSApp makeWindowsPerform: @selector(windowController) inOrder:YES] windowController] document];
    if (d) {
        [d writeSelectionToPasteboard:pasteboard];
    }
}

static void addChildren(NSMutableArray* items, NSObject<ZGItemProtocol>* r) {
    for (NSObject<ZGItemProtocol>* c in r.children) {
        if (c.children != null) { // see note about empty children[] in ZG7zipItem -initWith
            [items addObject: c];
        }
    }
    for (NSObject<ZGItemProtocol>* c in r.children) {
        if (r.children != null) {
            addChildren(items, c);
        }
    }
}

- (void) pasteboard: (NSPasteboard*) pasteboard provideDataForType: (NSString*) type {
    // This method will be called to provide data for NSFilenamesPboardType
    trace(@"");
/*
    if ([type isEqualToString: NSFilenamesPboardType]) {
        int draggedRow = 1;
        NSObject<ZGItemProtocol>* it = [_tableViewDatatSource itemAtRow: draggedRow];
        NSURL *fileURL = [self extract: it to: [NSURL fileURLWithPath: NSTemporaryDirectory()]];
        if (fileURL) {
            pasteboard.propertyList = @[[fileURL.path forType:NSFilenamesPboardType]];
        }
    }
*/
}

@end

