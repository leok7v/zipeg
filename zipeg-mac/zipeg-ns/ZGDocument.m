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
#import "ZGWindowPresenter.h"
#import "ZGToolbar.h"
#import "ZGToolbarDelegate.h"
#import "ZGApp.h"


@interface ZGDocument() {
    NSObject<ZGItemFactory>* _archive;
    NSObject<ZGItemProtocol>* _root;
    NSWindow* __weak _window;
    NSColor* _sourceListBackgroundColor; // strong
    NSTableViewSelectionHighlightStyle _highlightStyle;
}

@property NSSearchField* searchField;
@property (weak) NSView* contentView;
@property ZGToolbar* toolbar;
@property ZGToolbarDelegate* toolbarDelegate;
@property NSSplitView* splitView;
@property NSLevelIndicator* levelIndicator;
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
@property ZGWindowPresenter* windowPresenter;

- (void) openArchiveForOperation: (NSOperation*) op;
- (void) searchArchiveWithString: (NSString*) s forOperation: (NSOperation*) op done: (void(^)(BOOL)) block;

@end

@interface ZGOutlineHeaderView : NSTableHeaderView
@end

@implementation ZGOutlineHeaderView

- (id) initWithFrame: (NSRect) frame {
    frame.size.height = 17;
    self = [super initWithFrame:frame];
    self.autoresizesSubviews = true;
    self.autoresizingMask = NSViewWidthSizable | NSViewMaxXMargin;
    return self;
}

- (void)drawRect:(NSRect)dirtyRect {
//  trace(@"%@", NSStringFromRect(self.frame));
    NSGradient *g = [[NSGradient alloc] initWithColorsAndLocations:
                     [NSColor colorWithDeviceWhite:1.00 alpha:1], 0.3,
                     [NSColor colorWithDeviceWhite:0.96 alpha:1], 0.42,
                     [NSColor colorWithDeviceWhite:0.93 alpha:1], 0.99, nil];
    [g drawInRect: self.bounds angle:90];
    [[NSColor colorWithDeviceWhite:0.66 alpha:1] set];
    NSFrameRect(NSMakeRect(0, 0, self.frame.size.width, self.frame.size.height));
}

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

- (void)main {
    [_document searchArchiveWithString: _search forOperation: self done: _block];
}

@end

@implementation ZGDocument

// See:
// http://stackoverflow.com/questions/16347569/why-arent-the-init-and-windowcontrollerdidloadnib-method-called-when-a-autosav
// and
// http://developer.apple.com/library/mac/#documentation/DataManagement/Conceptual/DocBasedAppProgrammingGuideForOSX/StandardBehaviors/StandardBehaviors.html#//apple_ref/doc/uid/TP40011179-CH5-SW8

+ (BOOL) canConcurrentlyReadDocumentsOfType: (NSString*) typeName {
    return false; // otherwise all -init will be called in concurently on session restore
}

- (id) init {
    trace(@"_operationQueue=%@ %@", _operationQueue, [NSThread currentThread]);
    self = [[super init] ctor];
    trace(@"_operationQueue=%@ %@", _operationQueue, [NSThread currentThread]);
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

- (id)initWithContentsOfURL: (NSURL*) absoluteURL ofType: (NSString*) typeName error: (NSError**) outError {
    return [super initWithContentsOfURL: absoluteURL ofType: typeName error: outError];
}

- (void) restoreStateWithCoder: (NSCoder*) state {
    [super restoreStateWithCoder: state];
    trace(@"_operationQueue=%@ %@", _operationQueue, [NSThread currentThread]);
}

- (id) ctor {
    if (self != null) {
        alloc_count(self);
        trace_allocs();
        self.hasUndoManager = false;
        _operationQueue = [NSOperationQueue new];
        _operationQueue.maxConcurrentOperationCount = 1; // TODO: can it be 2?
        _encoding = (CFStringEncoding)-1;
        //      _highlightStyle = NSTableViewSelectionHighlightStyleSourceList;
        _highlightStyle = NSTableViewSelectionHighlightStyleRegular;
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

- (void) setViewStyle: (int) s {
    int hs  = s == 0? NSTableViewSelectionHighlightStyleSourceList : NSTableViewSelectionHighlightStyleRegular;
    if (_highlightStyle != hs) {
        _highlightStyle = hs;
        _outlineView.selectionHighlightStyle =  _highlightStyle;
        [_outlineView deselectAll: null];
        [self reloadData];
        // void* v = (__bridge void *)(_outlineView.backgroundColor);
        // trace(@"_outlineView.background = %@ 0x%016llX", _outlineView.backgroundColor, (UInt64)v);
        _outlineView.backgroundColor = _sourceListBackgroundColor;
        _outlineView.backgroundColor = [NSColor sourceListBackgroundColor];
    }
}

- (void)makeWindowControllers {
    assert(_operationQueue != null);
    ZGWindowController* wc = [ZGWindowController new];
    [self addWindowController: wc];
    // trace("wc.document=%@ %s (self %@)", wc.document, wc.document == self ? "==" : "!=", self);
    // [wc window]; // this actually loads Nib (see docs)
    [self setupDocumentWindow: wc];
}

- (void) reloadData {
    if (_outlineView != null && _archive != null) {
        if (_highlightStyle != NSTableViewSelectionHighlightStyleSourceList) {
            _root = _archive.root;
        } else {
            _root = [[ZGGenericItem alloc] initWithChild: _archive.root];
        }
        _outlineViewDataSource = [[ZGOutlineViewDataSource alloc] initWithDocument: self andRootItem: _root];
        // trace("_root=%@", _root);
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
        _tableViewDatatSource = [[ZGTableViewDataSource alloc] initWithDocument: self];
        _tableView.dataSource = _tableViewDatatSource;
        [_tableView reloadData];
        dispatch_async(dispatch_get_current_queue(), ^{
            [_outlineViewDelegate expandAll: _outlineView];
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
    sv.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
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
    sv.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
    sv.autoresizesSubviews = true;
    return sv;
}

static NSOutlineView* createOutlineView(NSRect r, NSTableViewSelectionHighlightStyle hs) {
    NSOutlineView* ov = [[NSOutlineView alloc] initWithFrame: r];
    ov.focusRingType = NSFocusRingTypeNone;
    ov.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
    ov.allowsEmptySelection = true; // because of the sections (groups) collapse in Outline View
    ov.selectionHighlightStyle = hs;
    ov.indentationMarkerFollowsCell = true;
    ov.indentationPerLevel = 16;
//  ov.headerView = [[ZGOutlineHeaderView alloc] initWithFrame: r];
    ov.headerView = null;
//  ov.columnAutoresizingStyle = NSTableViewLastColumnOnlyAutoresizingStyle;
    NSTableColumn* tc = [NSTableColumn new];
    [ov addTableColumn: tc];
    ov.outlineTableColumn = tc;
    ZGImageAndTextCell* c = [ZGImageAndTextCell new];
    tc.dataCell = c;
    c.backgroundColor = [NSColor sourceListBackgroundColor];
    tc.minWidth = 92;
    tc.maxWidth = 3000;
    tc.editable = true;
//  tc.resizingMask = NSTableColumnAutoresizingMask;
    assert(ov.outlineTableColumn == tc);
    assert(ov.tableColumns[0] == tc);
    return ov;
}

- (void) setupDocumentWindow: (NSWindowController*) controller { // TODO: rename me
    // this is called after readFromURL
    _window = controller.window;
    assert(_window != null);
    _window.collectionBehavior = NSWindowCollectionBehaviorFullScreenPrimary;
    [self setWindow: _window]; // weak
    NSRect bounds = [_window.contentView bounds];
    _contentView = _window.contentView;
    bounds = _contentView.frame;
    bounds.origin.y += 30;
    bounds.size.height -= 60;
 
    NSRect tbounds = bounds;
    tbounds.size.width /= 2;
    tbounds.origin.x = 0;
    tbounds.origin.y = 0;
    _outlineView = createOutlineView(tbounds, _highlightStyle);
    _outlineView.selectionHighlightStyle = NSTableViewSelectionHighlightStyleSourceList;
    _sourceListBackgroundColor = _outlineView.backgroundColor;
//  trace(@"_outlineView.background = %@", _outlineView.backgroundColor);
    _outlineView.selectionHighlightStyle = _highlightStyle;
//  trace(@"_outlineView.background = %@", _outlineView.backgroundColor);
    
    assert(_outlineView != null);

    _tableView = [[NSTableView alloc] initWithFrame: tbounds];
    assert(_tableView != null);
    _tableView.focusRingType = NSFocusRingTypeNone;
    _splitView = createSplitView(bounds,
                                 createScrollView(tbounds, _outlineView),
                                 createScrollView(tbounds, _tableView));
    _contentView.autoresizesSubviews = true;
    _contentView.subviews = @[_splitView];
    controller.window.contentView = _contentView;
    
    assert(_contentView != null);
    assert(controller.window.contentView == _contentView);
    assert(_splitView != null);

    _toolbar = [ZGToolbar new];
    assert(_toolbar != null);
    _toolbarDelegate = [[ZGToolbarDelegate alloc] initWithDocument: self];
    assert(_toolbarDelegate != null);
    _toolbar.delegate = _toolbarDelegate; // weak reference
    _window.toolbar = _toolbar;
 
    //  assert(_levelIndicator != null);
    
    NSTableColumn* tableColumn = [NSTableColumn new];

    [_tableView addTableColumn: tableColumn];
    tableColumn.dataCell = [ZGImageAndTextCell new];
    tableColumn.minWidth = 92;
    tableColumn.maxWidth = 3000;
    tableColumn.editable = true;
    assert(_tableView.tableColumns[0] == tableColumn);

    NSSortDescriptor *sd = [NSSortDescriptor sortDescriptorWithKey:@"name" ascending:YES
                                                selector:@selector(localizedCaseInsensitiveCompare:)];
    tableColumn.sortDescriptorPrototype = sd;
    tableColumn.resizingMask = NSTableColumnAutoresizingMask | NSTableColumnUserResizingMask;
    [tableColumn addObserver:self forKeyPath:@"width" options: 0 context: null];
    for (int i = 1; i < 3; i++) {
        tableColumn = [NSTableColumn new];
        [_tableView addTableColumn: tableColumn];
        assert(_tableView.tableColumns[i] == tableColumn);
        tableColumn.dataCell = [NSTextFieldCell new];
        tableColumn.minWidth = 92;
        tableColumn.maxWidth = 3000;
        tableColumn.editable = true;
    }
    
    _contentView.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;

    _tableView.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
    _tableView.allowsColumnReordering = true;
    _tableView.allowsColumnResizing = true;
    _tableView.allowsMultipleSelection = true;
    _tableView.allowsColumnSelection = false;
    _tableView.allowsEmptySelection = true; // otherwise deselectAll won't work
    _tableView.allowsTypeSelect = true;
    _tableView.usesAlternatingRowBackgroundColors = true;
//  _tableView.menu = _tableRowContextMenu; // TODO:
    NSFont* font = [NSFont systemFontOfSize: NSFont.smallSystemFontSize - 1];
    for (int i = 0; i < _tableView.tableColumns.count; i++) {
        NSTableColumn* tc = _tableView.tableColumns[i];
        NSTableHeaderCell* hc = tc.headerCell;
        hc.font = font;
    }
    
    [_tableView setDraggingSourceOperationMask : NSDragOperationCopy forLocal: NO];
    [_tableView setDraggingSourceOperationMask : NSDragOperationGeneric forLocal: YES];
    // TODO: for now:
    [_tableView registerForDraggedTypes: @[NSFilenamesPboardType, NSFilesPromisePboardType]];

    
    _outlineViewDelegate = [[ZGOutlineViewDelegate alloc] initWithDocument: self];;
    _outlineView.delegate = _outlineViewDelegate;
    
    _tableViewDelegate = [[ZGTableViewDelegate alloc] initWithDocument: self];
    _tableView.delegate = _tableViewDelegate;

//  [self reloadOutlineView];
    
    // trace(@"0x%016llx 0x%016llx", (UInt64)(id)_outlineView, (UInt64)(id)(_outlineView.dataSource));
    _splitViewDelegate = [[ZGSplitViewDelegate alloc] initWithDocument: self];
    _splitView.delegate = _splitViewDelegate;
    _splitView.hidden = true;
    _heroView = [[ZGHeroView alloc] initWithFrame:_splitView.frame];
    _heroView.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
    _levelIndicator.maxValue = 10000;
    _levelIndicator.intValue = 5000;

    
    NSClipView * clipView = [[_outlineView enclosingScrollView] contentView];
    clipView.postsFrameChangedNotifications = true;
    [NSNotificationCenter.defaultCenter addObserver:self
                                           selector: @selector(oulineViewContentBoundsDidChange:)
                                               name: NSViewBoundsDidChangeNotification
                                             object: clipView];
    [NSNotificationCenter.defaultCenter addObserver:self
                                           selector: @selector(oulineViewContentBoundsDidChange:)
                                               name: NSViewFrameDidChangeNotification
                                             object: clipView];
    [NSNotificationCenter.defaultCenter addObserver: self
                                           selector: @selector(outlineViewSelectionDidChange:)
                                               name: NSOutlineViewSelectionDidChangeNotification
                                             object: _outlineView];
    [NSNotificationCenter.defaultCenter addObserver: self
                                           selector: @selector(windowWillClose:)
                                               name: NSWindowWillCloseNotification
                                             object: _window];
    // TODO: useless remove me
    [NSNotificationCenter.defaultCenter addObserver: self
                                           selector: @selector(windowDidUpdateFirstTime:)
                                               name: NSWindowDidUpdateNotification
                                             object: _window];
    
    _windowPresenter = [ZGWindowPresenter windowPresenterFor: controller.window];
    
    [_contentView addSubview: _heroView];
//  dumpViews(_contentView);
}

- (void) windowDidUpdateFirstTime: (NSNotification*) n {
    [NSNotificationCenter.defaultCenter removeObserver: self name: NSWindowDidUpdateNotification object: _window];
    [NSNotificationCenter.defaultCenter addObserver: self
                                           selector: @selector(windowDidUpdate:)
                                               name: NSWindowDidUpdateNotification
                                             object: _window];
    if (_url != null) {
        OpenArchiveOperation *operation = [[OpenArchiveOperation alloc] initWithDocument:self];
        [_operationQueue addOperation:operation];
    }
    [self windowDidUpdate: n];
}

- (void) windowDidUpdate: (NSNotification*) n {
    
}

- (void) resetViews: (NSView*) v {
    id i = v;
    if ([i respondsToSelector:@selector(setDelegate:)]) {
        [i setDelegate: null];
    }
    if (v.subviews != null) {
        NSArray* sv = [NSArray arrayWithArray:v.subviews];
        for (id s in sv) { // to avoid array was mutated while being enumerated
            [self resetViews: (NSView*) s];
        }
    }
    if ([v.superview isKindOfClass: NSClipView.class] &&
        [v.superview.superview isKindOfClass: NSScrollView.class]) {
        // ((NSScrollView*)v.superview.superview).contentView = null; // scrollview does not take null :(
    } else  if ([v isKindOfClass: NSClipView.class] &&
                [v.superview isKindOfClass: NSScrollView.class]) {
        // skip this too
    } else if (v != _window.contentView) {
        [v removeFromSuperview];
    }
}

- (void) windowWillClose: (NSNotification*) n {
    [self resetViews: _window.contentView];
    self.window.toolbar.delegate = null;
    self.window.toolbar = null;
    dumpAllViews();
    [_splitView removeFromSuperview];
    _splitView.subviews = @[];
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

- (void)observeValueForKeyPath: (NSString*) keyPath ofObject: (id) o change: (NSDictionary*)change context: (void*) context {
    NSInteger resizedColumn = _tableView.headerView.resizedColumn;
    if (resizedColumn != -1) {
        if ([o isKindOfClass:NSTableColumn.class] &&
            o == [_tableView.tableColumns objectAtIndex: resizedColumn]) {
            NSTableColumn* tc = (NSTableColumn*)o;
            console(@"User resized table column %@", tc);
            // TODO: does it affect autosizing? for how long?
        }
    }
}

- (void)oulineViewContentBoundsDidChange:(NSNotification *)notification {
    [self sizeToContent];
}

- (void) sizeToContent {
    [_outlineViewDelegate sizeToContent: _outlineView];
    [_tableViewDelegate sizeToContent: _tableView];
}

- (void) outlineViewSelectionDidChange: (NSNotification *) notification  {
    [_tableView deselectAll: null];
    [_tableView reloadData];
    [_tableViewDelegate sizeToContent: _tableView];
}

+ (BOOL)autosavesInPlace {
    // this is for autosaving documents like text files... see NSDocumentController -setAutosavingDelay:
    return false;
}

- (BOOL)hasUnautosavedChanges {
    return false;
}

- (BOOL) isDocumentEdited {
    return false;
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
        [_window orderOut:null]; // ???
        // TODO: cannot do it here because other documents can still be working.... [NSApp terminate:nil];
    } else {
        // trace(@"Quit - canceled");
    }
    // trace(@"");
}

- (void)close {
    NSTableColumn* tc = _tableView.tableColumns[0];
    assert(tc != null);
    [tc removeObserver:self forKeyPath:@"width"];
    [_operationQueue cancelAllOperations];
    [_operationQueue waitUntilAllOperationsAreFinished];
    if (_archive != null) {
        [_archive close];
        _archive = null;
        _root = null;
    }
    [super close];
}

- (BOOL)writeSelectionToPasteboard:(NSPasteboard *)pasteboard {
    // This method will be called for services, or for drags originating from the preview column ZipEntryView, and it calls the previous method
    return false; // TODO: depending on FirstResponder being table or outline view
}

+ (void)registerServices {
    static BOOL registeredServices = NO;
    if (!registeredServices) {
        [NSApp setServicesProvider:self];
        registeredServices = YES;
    }
}

+ (void)exportData:(NSPasteboard *)pasteboard userData:(NSString *)data error:(NSString **)error {
    ZGDocument *d = [[[NSApp makeWindowsPerform: @selector(windowController) inOrder:YES] windowController] document];
    if (d) {
        [d writeSelectionToPasteboard:pasteboard];
    }
}


- (NSData *)dataOfType:(NSString *)typeName error:(NSError **)outError {
    // Insert code here to write your document to data of the specified type.
    // If outError != NULL, ensure that you create and set an appropriate
    // error when returning nil.
    // You can also choose to override -fileWrapperOfType:error:,
    // -writeToURL:ofType:error:,
    // or
    // -writeToURL:ofType:forSaveOperation:originalContentsURL:error: instead.
    NSException *exception = [NSException exceptionWithName:@"UnimplementedMethod"
                                          reason:[NSString
                                          stringWithFormat:@"%@ is unimplemented",
                                          NSStringFromSelector(_cmd)] userInfo:nil];
    @throw exception;
    return nil;
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
    ZGDocument* __block doc = self;
    dispatch_async(dispatch_get_main_queue(), ^{
        assert([NSThread isMainThread]);
        if (a != null) {
            _archive = a;
            // archive = [ZGFileSystem new];
            [self reloadData];
            _heroView.hidden = true;
            _splitView.hidden = false;
            // TODO: or table view if outline view is hidden
            [[self.windowControllers[0] window] makeFirstResponder:_outlineView];
        } else if (error != null) {
            NSAlert* alert = [NSAlert alertWithError: error];
            NSWindowController* wc = doc.windowControllers[0];
            assert(wc.window == doc.windowPresenter.window);
            [doc.windowPresenter presentSheetWithSheet:alert
                                                  done: ^(int rc) {
                                                      [_window orderOut: self];
                                                      [_window performClose:self];
                                                  }];
        } else {
            // error == null - aborted by user
        }
    });
}

- (NSString*) askForPasswordFromBackgroundThread {
    assert(![NSThread isMainThread]);
    dispatch_semaphore_t password_semaphore = dispatch_semaphore_create(0);
    NSString* __block password;
    dispatch_async(dispatch_get_main_queue(), ^{
        assert([NSThread isMainThread]);
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
/*
        [alert beginSheetModalForWindow: _window modalDelegate: self
                     didEndSelector: @selector(didEndPresentedAlert:returnCode:contextInfo:)
                        contextInfo: null];
*/
        [self.windowPresenter presentSheetWithSheet: alert
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

- (void) didEndPresentedAlert: (NSAlert*) a returnCode: (NSInteger) rc contextInfo: (void*) c {
// ignore for now...
}

- (void) didEndPasswordInput: (NSAlert*) a returnCode: (NSInteger)rc contextInfo: (void*) ctx {
}

- (BOOL) progress:(long long)pos ofTotal:(long long)total {
    // TODO: connect to progress bar(s)
    // trace(@"%llu of %llu", pos, total);
    return true; // TODO: cancel button
}

- (BOOL) progressFile:(long long)fileno ofTotal:(long long)totalNumberOfFiles {
    // TODO: connect to progress bar(s)
    // trace(@"%llu of %llu", fileno, totalNumberOfFiles);
    return true; // TODO: cancel button
}


- (void) search: (NSString*) s {
    assert([NSThread isMainThread]);
    if (_archive == null) {
        return;
    }
    if (_searchTextColor == null) {
        _searchTextColor = [_searchField textColor];
    }
    ZGDocument* __block doc = self;
    SearchArchiveOperation* op = [[SearchArchiveOperation alloc]
      initWithDocument: self searchString: s
        done: (^(BOOL found){
            assert([NSThread isMainThread]);
            [doc reloadData];
            _searchField.textColor = found ? _searchTextColor : NSColor.redColor;
        })];
    [_operationQueue addOperation: op];
}

- (void) searchArchiveWithString: (NSString*) s forOperation: (NSOperation*) op done: (void(^)(BOOL)) block {
    assert(![NSThread isMainThread]);
    [_archive setFilter: s operation: op done: block];
}


@end

