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
#import "ZGSheet.h"
#import "ZGToolbar.h"
#import "ZGToolbarDelegate.h"
#import "ZGDestination.h"
#import "ZGImages.h"
#import "ZGApp.h"

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
    id _windowWillCloseObserver;
    id _clipViewFrameDidChangeObserver;
    id _clipViewBoundsDidChangeObserver;
    NSTableViewSelectionHighlightStyle _highlightStyle;
    BOOL _isNew;
    uint64_t _timeToShowHeroView;
}

@property (weak) NSView* contentView;
@property ZGToolbarDelegate* toolbarDelegate;
@property NSLevelIndicator* levelIndicator;
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
@property NSURL* url;
@property CFStringEncoding encoding;
@property NSString* typeName;
@property NSError* error;
@property ZGHeroView* heroView;
@property ZGSheet* sheet;

- (void) openArchiveForOperation: (NSOperation*) op;
- (void) extractItemsForOperation: (NSOperation*) op items: (NSArray*) items to: (NSURL*) url;
- (void) searchArchiveWithString: (NSString*) s forOperation: (NSOperation*) op done: (void(^)(BOOL)) block;

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
/*
- (void) drawRect: (NSRect) r {
    if (_document.url == null) { // new document
        [NSGraphicsContext.currentContext saveGraphicsState];
        CGContextRef gc = (CGContextRef) [NSGraphicsContext.currentContext graphicsPort];
        CGContextSetAlpha(gc, 0.5);
        [NSGraphicsContext.currentContext restoreGraphicsState];
    } else {
        [super drawRect: r];
    }
}
*/
@end


@interface OpenArchiveOperation : NSOperation {
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

@interface SearchArchiveOperation : NSOperation {
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
    _search = [s copy];
    _block = block;
    return self;
}

- (void) main {
    [_document searchArchiveWithString: _search forOperation: self done: _block];
}

@end


@interface ExtractItemsOperation : NSOperation {
    ZGDocument* __weak _document;
    NSArray* _items;
    NSURL* _url;
}
- (id) initWithDocument: (ZGDocument*) doc items: (NSArray*) items to: (NSURL*) url;
@end

@implementation ExtractItemsOperation

- (id) initWithDocument: (ZGDocument*) doc items: (NSArray*) items to: (NSURL*) url {
    self = [super init];
    if (self != null) {
        _document = doc;
        _items = items;
        _url = url;
    }
    return self;
}

- (void)main {
    [_document extractItemsForOperation: self items: _items to: _url];
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
    self = [[super init] ctor];
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
        _operationQueue = [NSOperationQueue new];
        _operationQueue.maxConcurrentOperationCount = 1; // TODO: can it be 2?
        _encoding = (CFStringEncoding)-1;
        _highlightStyle = NSTableViewSelectionHighlightStyleSourceList;
        // _highlightStyle = NSTableViewSelectionHighlightStyleRegular;
    }
    return self;
}

- (void)restoreDocumentWindowWithIdentifier: (NSString*) id state: (NSCoder*) state
            completionHandler: (void (^)(NSWindow*, NSError*)) done {
    [super restoreDocumentWindowWithIdentifier: id state: state completionHandler: done];
}

- (void) dealloc {
    trace(@"");
    trace_allocs();
    dealloc_count(self);
    [NSNotificationCenter.defaultCenter removeObserver:self];
    [ZGApp deferedTraceAllocs];
}

- (void) makeWindowControllers {
    assert(_operationQueue != null);
    ZGWindowController* wc = [ZGWindowController new];
    [self addWindowController: wc];
    [self setupDocumentWindow: wc];
}

- (void) reloadData {
    if (_outlineView != null && _archive != null) {
        if (_highlightStyle != NSTableViewSelectionHighlightStyleSourceList) {
            _root = _archive.root;
        } else {
            _root = [[ZGGenericItem alloc] initWithChild: _archive.root];
        }
        if (_outlineViewDataSource == null) {
            _outlineViewDataSource = [[ZGOutlineViewDataSource alloc] initWithDocument: self andRootItem: _root];
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
            _tableViewDatatSource = [[ZGTableViewDataSource alloc] initWithDocument: self];
            _tableView.dataSource = _tableViewDatatSource;
        }
        [_tableView reloadData];
        dispatch_async(dispatch_get_current_queue(), ^{
            [_outlineViewDelegate expandAll];
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
    NSSplitView* sv = [[NSSplitView alloc] initWithFrame: r];
    sv.vertical = true;
    sv.dividerStyle = NSSplitViewDividerStyleThin;
    sv.autoresizingMask = kSizableWH | kSizableLR;
    sv.autoresizesSubviews = true;
    sv.autosaveName = @"Zipeg Split View";
    sv.subviews = @[left, right];
    return sv;
}

static NSScrollView* createScrollView(NSRect r, NSView* v) {
    NSScrollView* sv = [[NSScrollView alloc] initWithFrame: r];
    sv.documentView = v;
    sv.hasVerticalScroller = true;
    sv.hasHorizontalScroller = true;
    sv.autoresizingMask = kSizableWH;
    sv.autoresizesSubviews = true;
    return sv;
}

static NSOutlineView* createOutlineView(NSRect r, NSTableViewSelectionHighlightStyle hs) {
    NSOutlineView* ov = [[NSOutlineView alloc] initWithFrame: r];
    ov.focusRingType = NSFocusRingTypeNone;
    ov.autoresizingMask = kSizableWH;
    ov.allowsEmptySelection = true; // because of the sections (groups) collapse in Outline View
    ov.indentationMarkerFollowsCell = true;
    ov.indentationPerLevel = 16;
    ov.headerView = null;
    NSTableColumn* tc = [NSTableColumn new];
    [ov addTableColumn: tc];
    ov.outlineTableColumn = tc;
    ZGImageAndTextCell* c = [ZGImageAndTextCell new];
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
    NSTableView* tv = [[NSTableView alloc] initWithFrame: r];
    tv.focusRingType = NSFocusRingTypeNone;
    NSTableColumn* tableColumn = [NSTableColumn new];
    
    [tv addTableColumn: tableColumn];
    tableColumn.dataCell = [ZGImageAndTextCell new];
    tableColumn.minWidth = 92;
    tableColumn.maxWidth = 3000;
    tableColumn.editable = true;
    assert(tv.tableColumns[0] == tableColumn);
    
    NSSortDescriptor *sd = [NSSortDescriptor sortDescriptorWithKey:@"name" ascending:YES
                                                          selector:@selector(localizedCaseInsensitiveCompare:)];
    tableColumn.sortDescriptorPrototype = sd;
    tableColumn.resizingMask = NSTableColumnAutoresizingMask | NSTableColumnUserResizingMask;
    for (int i = 1; i < 3; i++) {
        tableColumn = [NSTableColumn new];
        [tv addTableColumn: tableColumn];
        assert(tv.tableColumns[i] == tableColumn);
        tableColumn.dataCell = [NSTextFieldCell new];
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
    NSImage* dir = o ? ZGImages.shared.dirOpen : ZGImages.shared.dirImage;
    return it.children == null ? ZGImages.shared.docImage : dir;
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
    _outlineViewDelegate = [[ZGOutlineViewDelegate alloc] initWithDocument: self];;
    _outlineView.delegate = _outlineViewDelegate;
    
    _tableView = createTableView(tbounds);
    assert(_tableView != null);
    _tableViewDelegate = [[ZGTableViewDelegate alloc] initWithDocument: self];
    _tableView.delegate = _tableViewDelegate;
   
    _splitView = createSplitView(bounds,
                                 createScrollView(tbounds, _outlineView),
                                 createScrollView(tbounds, _tableView));
    assert(_splitView != null);
    _splitViewDelegate = [[ZGSplitViewDelegate alloc] initWithDocument: self];
    _splitView.delegate = _splitViewDelegate;
    _splitView.hidden = true;
    _heroView = [[ZGHeroView alloc] initWithDocument: self andFrame: _contentView.bounds];
    _heroView.autoresizingMask = kSizableWH;
    _heroView.hidden = !_isNew;

    _toolbarDelegate = [[ZGToolbarDelegate alloc] initWithDocument: self];
    _toolbar = [ZGToolbar new];
    _toolbar.delegate = _toolbarDelegate; // weak reference
    _window.toolbar = _toolbar;
 
    _sheet = [[ZGSheet alloc] initWithWindow: controller.window];

    //  assert(_levelIndicator != null);
    _levelIndicator.maxValue = 10000;
    _levelIndicator.intValue = 5000;
    
    NSRect dbounds = _contentView.bounds;
    dbounds.origin.y = dbounds.size.height - 30;
    dbounds.size.height = 30;
    _destination = [[ZGDestination alloc] initWithFrame: dbounds];
    
    NSClipView * clipView = [[_outlineView enclosingScrollView] contentView];
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
    ZGBackPanel* background = [[ZGBackPanel alloc] initWithDocument: self andFrame: _contentView.frame];
    _contentView.subviews = @[background];
    background.subviews = @[_splitView, _destination, _heroView];
    if (_isNew) {
        // TODO: how to make document modified without this hack?
        [self performSelector: @selector(_updateDocumentEditedAndAnimate:) withObject: @true];
    } else {
        _timeToShowHeroView = nanotime() + 500 * 1000ULL * 1000ULL; // 0.5 sec
        OpenArchiveOperation *operation = [[OpenArchiveOperation alloc] initWithDocument: self];
        [_operationQueue addOperation: operation];
    }
}

- (void) windowDidBecomeKey {
}

- (void) windowDidResignKey {
}

- (void) firstResponderChanged {
    NSResponder* fr = [[self.windowControllers[0] window] firstResponder];
    // trace(@"first responder changed to %@", fr);
    if (fr == _tableView) {
        [_tableViewDelegate tableViewBecameFirstResponder: _tableView];
    }
}

- (void) sizeToContent {
    [_outlineViewDelegate sizeToContent: _outlineView];
    [_tableViewDelegate sizeToContent: _tableView];
}

+ (BOOL)autosavesInPlace {
    // this is for autosaving documents like text files... see NSDocumentController -setAutosavingDelay:
    return false;
}

- (BOOL)hasUnautosavedChanges {
    return _isNew;
}

- (BOOL) isDocumentEdited {
    return _isNew;
}

- (BOOL) documentCanClose {
    if (_operationQueue.operations.count > 0) {
        // TODO: replace with Presenter and dismiss when all ops are done
        NSBeginInformationalAlertSheet(
            @"Operation is in Progress", @"OK",@"Cancel",@"",
            _window,
            self, // modalDelegate
            @selector(closeDidEnd:returnCode:contextInfo:),
            null, // didDismissSelector
            null, @"");  // could be: @"fmt=%@", @"args"
    }
    return _operationQueue.operations.count == 0;
}

- (void) closeDidEnd: (NSWindow*)sheet returnCode: (int) rc contextInfo:(void *) contextInfo {
    if (rc == NSAlertDefaultReturn) {
        [_operationQueue cancelAllOperations];
        [_operationQueue waitUntilAllOperationsAreFinished];
        [_window performClose: self];
    } else {
        // trace(@"Quit - canceled");
    }
}

- (void)close {
    // NSWindowController _windowDidClose will call us recursively from super :(
    if (_splitView != null) {
        NSTableColumn* tc = _tableView.tableColumns[0];
        assert(tc != null);
        [_operationQueue cancelAllOperations];
        [_operationQueue waitUntilAllOperationsAreFinished];
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

- (BOOL) writeToURL:(NSURL *)absoluteURL ofType:(NSString *)typeName error:(NSError **)outError {
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


- (BOOL) readFromURL:(NSURL *)absoluteURL ofType:(NSString *)typeName error:(NSError **)outError {
    return [self readFromURL:absoluteURL ofType:typeName encoding:(CFStringEncoding)-1 error:outError];
}

- (BOOL) readFromURL:(NSURL *)absoluteURL ofType:(NSString *)typeName encoding: (CFStringEncoding) encoding
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

- (void) openArchiveForOperation: (NSOperation*) op {
    // This method is called on the background thread
    assert(![NSThread isMainThread]);
    NSObject<ZGItemFactory>* __block a = [ZG7zip new];
    NSError* __block error;
    BOOL b = [a readFromURL: _url ofType: _typeName encoding: _encoding
                   document: self operation: (NSOperation*) op error: &error
                       done: ^(NSObject<ZGItemFactory>* a, NSError* error) {
                       }
             ];
    if (!b) {
        a = null;
    }
    ZGDocument* __block __weak that = self;
    dispatch_async(dispatch_get_main_queue(), ^{
        assert([NSThread isMainThread]);
        if (a != null) {
            _archive = a;
            // _archive = [ZGFileSystem new];
            [_window setTitle: a.root.name]; // the DnD of title icon will still show filename.part1.rar
            [self reloadData];
            _heroView.hidden = true;
            _timeToShowHeroView = 0;
            _splitView.hidden = false;
            // TODO: or table view if outline view is hidden
            [[self.windowControllers[0] window] makeFirstResponder:_outlineView];
        } else if (error != null) {
            _heroView.hidden = false;
            NSAlert* alert = [NSAlert alertWithError: error];
            [that.sheet begin: alert done: ^(int rc) { [_window performClose: null]; } ];
        } else {
            // error == null - aborted by user
            [_window performClose: _window];
        }
    });
}

- (NSString*) askForPasswordFromBackgroundThread {
    assert(![NSThread isMainThread]);
    dispatch_semaphore_t password_semaphore = dispatch_semaphore_create(0);
    NSString* __block password;
    dispatch_async(dispatch_get_main_queue(), ^{
        assert([NSThread isMainThread]);
        // there are 2 points in time when we may be asked for password.
        // during archive opening and during extraction. Do not show hero
        // screen at the second scenario.
        _heroView.hidden = _archive != null;
        NSString* prompt = [NSString stringWithFormat:@"Please enter archive password for archive\n«%@»",
                            [_url.path lastPathComponent]];
        NSString* info = @"Password has been set by the person who created this archive file.\n"
          "If you don`t know the password please contact that person.";
        NSAlert *alert = [NSAlert alertWithMessageText: prompt
                                         defaultButton: @"OK"
                                       alternateButton: @"Cancel"
                                           otherButton: null
                             informativeTextWithFormat: @"%@", info];
        NSTextField* password_input = [[NSTextField alloc] initWithFrame:NSMakeRect(0, 0, 300, 24)];
        password_input.autoresizingMask = NSViewWidthSizable | NSViewMaxXMargin | NSViewMinXMargin;
        NSCell* cell = password_input.cell;
        cell.usesSingleLineMode = true;
        password_input.stringValue = @"";
        [alert setAccessoryView:password_input];
        dumpAllViews();
        [self.sheet begin: alert
            done: ^(int rc) {
                if (rc == NSAlertDefaultReturn) {
                    [password_input validateEditing];
                    password = password_input.stringValue;
                } else {
                    password = @"";
                }
                [alert setAccessoryView: null];
                dispatch_semaphore_signal(password_semaphore);
            }];
    });
    dispatch_semaphore_wait(password_semaphore, DISPATCH_TIME_FOREVER);
    dispatch_release(password_semaphore);
    password_semaphore = null;
    return password;
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

- (void) extractItemsForOperation: (NSOperation*) op items: (NSArray*) items to: (NSURL*) url {
    // This method is called on the background thread
    assert(![NSThread isMainThread]);
    assert(_archive != null);
    [_archive extract: items to: url operation: op done: ^(NSError* error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (error != null) {
                [[NSSound soundNamed:@"error"] play];
                NSAlert* alert = [NSAlert alertWithError: error];
                [_sheet begin: alert done: ^(int rc) { } ];
            } else {
                // http://cocoathings.blogspot.com/2013/01/playing-system-sounds.html
                // see: /System/Library/Sounds
                // Basso Blow Bottle Frog Funk Glass Hero Morse Ping Pop Purr Sosumi Submarine Tink
                [[NSSound soundNamed:@"done"] play];
            }
        });
    }];
}

- (BOOL) progressOnBackgroundThread: (long long)pos ofTotal:(long long)total {
    assert(![NSThread isMainThread]);
    // TODO: connect to progress bar(s)
    // trace(@"%llu of %llu", pos, total);
    [self checkTimeToShowHeroView];
    return true; // TODO: may read the state of cancel button even from background thread
}

- (BOOL) progressFileOnBackgroundThread:(long long)fileno ofTotal:(long long)totalNumberOfFiles {
    assert(![NSThread isMainThread]);
    // TODO: connect to progress bar(s)
    // trace(@"%llu of %llu", fileno, totalNumberOfFiles);
    [self checkTimeToShowHeroView];
    return true; // TODO: may read the state of cancel button even from background thread
}

- (void) search: (NSString*) s {
    assert([NSThread isMainThread]);
    if (_archive == null) {
        return;
    }
    ZGDocument* __block __weak that = self;
    SearchArchiveOperation* op = [[SearchArchiveOperation alloc]
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

- (void) searchArchiveWithString: (NSString*) s forOperation: (NSOperation*) op done: (void(^)(BOOL)) block {
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

- (void) extract: (NSArray*) items to: (NSURL*) url {
    ExtractItemsOperation *operation = [[ExtractItemsOperation alloc] initWithDocument: self items: items to: url];
    [_operationQueue addOperation: operation];

/*
    // TODO: extact file here and wrtie to fileURL
    double delayInSeconds = 2.0;
    dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delayInSeconds * NSEC_PER_SEC));
    dispatch_after(popTime, dispatch_get_main_queue(), ^(void){
        trace(@"writeFile=%@", fileURL);
        NSMutableData* data = [NSMutableData dataWithLength: 4096];
        NSError* e = [NSError new];
        [data writeToURL:fileURL options: NSAtomicWrite error: &e];
    });
    trace(@"fileURL=%@", fileURL);
    return fileURL;
*/ 
}

- (void) pasteboard:(NSPasteboard *) pasteboard provideDataForType: (NSString*) type {
    // This method will be called to provide data for NSFilenamesPboardType
/*
    if ([type isEqualToString:NSFilenamesPboardType]) {
        int draggedRow = 1;
        NSObject<ZGItemProtocol>* it = [_tableViewDatatSource itemAtRow: draggedRow];
        NSURL *fileURL = [self extract: it to: [NSURL fileURLWithPath: NSTemporaryDirectory()]];
        if (fileURL) {
            [pasteboard setPropertyList:@[[fileURL path]] forType:NSFilenamesPboardType];
        }
    }
*/
}

@end

