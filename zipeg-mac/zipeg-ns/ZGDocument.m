#import "ZGDocument.h"
#import "ZG7zip.h"
#import "ZGUtils.h"
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
#import "ZGApplication.h"

@interface ZGDocument() {
    NSObject<ZGItemFactory>* archive;
    NSWindow* __weak _window;
}

@property NSSearchField* searchField;
@property NSView* contentView;
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

@property NSString* password;
@property NSTextField* password_input;
@property dispatch_semaphore_t password_semaphore;

- (void) searchFieldAction: (id)sender;
- (void) openArchiveForOperation: (NSOperation*) op;

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
    ZGDocument* _document;
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
    // Call on the document to do the actual work
    [_document openArchiveForOperation: self];
}

@end

@implementation ZGDocument

- (id) init {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        _operationQueue = [NSOperationQueue new];
        _operationQueue.maxConcurrentOperationCount = 1; // TODO: can it be 2?
        _encoding = (CFStringEncoding)-1;
    }
    return self;
}

- (void) dealloc {
    trace(@"%@", self);
    dealloc_count(self);
    [NSNotificationCenter.defaultCenter removeObserver:self];
    [ZGApplication deferedTraceAllocs];
}

- (void)makeWindowControllers {
    ZGWindowController* wc = [ZGWindowController new];
    [self addWindowController: wc];
    // trace("wc.document=%@ %s (self %@)", wc.document, wc.document == self ? "==" : "!=", self);
    // [wc window]; // this actually loads Nib (see docs)
    [self setupDocumentWindow: wc];
}

- (NSString*) windowNibName {
    assert(false);
    @throw @"ZGDocument should remain nib-less";
}

- (void) reloadOutlineView {
    if (_outlineView != null && archive != null) {
        _outlineViewDataSource = [[ZGOutlineViewDataSource alloc] initWithDocument: self andRootItem: archive.root];
        _outlineView.dataSource = _outlineViewDataSource;
        [_outlineView reloadData];
[_outlineView expandItem:null expandChildren:true]; // TODO: this is just temporary expand all, remove me
        
        NSIndexSet* is = [NSIndexSet indexSetWithIndex:0];
        [_outlineView selectRowIndexes:is byExtendingSelection:false];
        _tableViewDatatSource = [[ZGTableViewDataSource alloc] initWithDocument: self];
        _tableView.dataSource = _tableViewDatatSource;
        [_tableView reloadData];
        dispatch_async(dispatch_get_current_queue(), ^{
            [_outlineViewDelegate expandOne:_outlineView];
            [self sizeOutlineViewToContents];
            [_tableViewDelegate sizeTableViewToContents: _tableView];
        });
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

/* this is not needed because NSController.m window.setRestorable = false
- (void) restoreDocumentWindowWithIdentifier: (NSString*) id state: (NSCoder*) state
                           completionHandler: (void(^)(NSWindow*, NSError*)) completionHandler {
    trace(@"%@ %@", id, state);
    [super restoreDocumentWindowWithIdentifier: id state: state completionHandler: completionHandler];
}
*/

- (void) setupDocumentWindow: (NSWindowController*) controller { // TODO: rename me
    // this is called after readFromURL
    _window = controller.window;
    assert(_window != null);
    _window.collectionBehavior = NSWindowCollectionBehaviorFullScreenPrimary;
    [self setWindow: _window]; // weak
    NSRect bounds = [_window.contentView bounds];
    _contentView = [[NSView alloc] initWithFrame: [_window.contentView bounds]];
    bounds = _contentView.frame;
    bounds.origin.y += 30;
    bounds.size.height -= 60;
    NSRect tbounds = bounds;
    tbounds.size.width /= 2;
    tbounds.origin.x = 0;
    tbounds.origin.y = 0;
    _outlineView = [[NSOutlineView alloc] initWithFrame: tbounds];
    _outlineView.focusRingType = NSFocusRingTypeNone;
    _tableView = [[NSTableView alloc] initWithFrame: tbounds];
    _tableView.focusRingType = NSFocusRingTypeNone;
    _splitView = createSplitView(bounds,
                                 createScrollView(tbounds, _outlineView),
                                 createScrollView(tbounds, _tableView));
    _contentView.autoresizesSubviews = true;
    _contentView.subviews = @[_splitView];
    controller.window.contentView = _contentView;
    
    assert(_contentView != null);
    assert(controller.window.contentView == _contentView);
    assert(_tableView != null);
    assert(_splitView != null);
    assert(_outlineView != null);

    _toolbar = [ZGToolbar new];
    assert(_toolbar != null);
    _toolbarDelegate = [[ZGToolbarDelegate alloc] initWithDocument: self];
    assert(_toolbarDelegate != null);
    _toolbar.delegate = _toolbarDelegate; // weak reference
    _window.toolbar = _toolbar;
 
    //  assert(_levelIndicator != null);
    
    NSTableColumn* tableColumn = [NSTableColumn new];
    [_outlineView addTableColumn: tableColumn];
    _outlineView.outlineTableColumn = tableColumn;
    tableColumn.dataCell = [ZGImageAndTextCell new];
    tableColumn.minWidth = 92;
    tableColumn.maxWidth = 3000;
    tableColumn.editable = true;
    assert(_outlineView.outlineTableColumn == tableColumn);
    assert(_outlineView.tableColumns[0] == tableColumn);

    tableColumn = [NSTableColumn new];
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
    _outlineView.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
    _outlineView.allowsEmptySelection = false;
    _outlineView.selectionHighlightStyle = NSTableViewSelectionHighlightStyleSourceList;

    // Xcode:
    // unfocused: Gradient 0.96 0.96 0.96 -> 0.91, 0.91, 0.91
    // focused: Gradient 0.91 0.92 0.94 -> 0.85 0.87 0.90
//  _outlineView.backgroundColor = [NSColor colorWithCalibratedRed:0.88 green:0.89 blue:0.92 alpha:1];
//  _outlineView.menu = _tableRowContextMenu;

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

    [self reloadOutlineView];
    
    // trace(@"0x%016llx 0x%016llx", (UInt64)(id)_outlineView, (UInt64)(id)(_outlineView.dataSource));
    _splitViewDelegate = [[ZGSplitViewDelegate alloc] initWithDocument: self];
    _splitView.delegate = _splitViewDelegate;
    _splitView.hidden = true;
    _heroView = [[ZGHeroView alloc] initWithFrame:_splitView.frame];
    _heroView.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
    _levelIndicator.maxValue = 10000;
    _levelIndicator.intValue = 5000;

    _outlineView.headerView = [[ZGOutlineHeaderView alloc] initWithFrame:_outlineView.frame];
    
    NSClipView * clipView = [[_outlineView enclosingScrollView] contentView];
    clipView.postsFrameChangedNotifications = true;
    [NSNotificationCenter.defaultCenter addObserver:self
                                             selector:@selector(oulineViewContentBoundsDidChange:)
                                                 name:NSViewBoundsDidChangeNotification
                                               object:clipView];
    [NSNotificationCenter.defaultCenter addObserver:self
                                             selector:@selector(oulineViewContentBoundsDidChange:)
                                                 name:NSViewFrameDidChangeNotification
                                               object:clipView];
    [NSNotificationCenter.defaultCenter addObserver: self
                                           selector: @selector(outlineViewSelectionDidChange:)
                                               name: @"NSOutlineViewSelectionDidChangeNotification"
                                             object: _outlineView];
    
    _windowPresenter = [ZGWindowPresenter windowPresenterFor: controller.window];
    
    if (_url != null) {
        OpenArchiveOperation *operation = [[OpenArchiveOperation alloc] initWithDocument:self];
        [_operationQueue addOperation:operation];
    }
    [_contentView addSubview: _heroView];
//  dumpViews(_contentView);
}


- (void) windowDidBecomeKey {
    // unfocused: Gradient 0.96 0.96 0.96 -> 0.91, 0.91, 0.91
    // focused: Gradient 0.91 0.92 0.94 -> 0.85 0.87 0.90
/*
    NSColor* c = [[NSColor windowBackgroundColor] colorUsingColorSpaceName: NSCalibratedRGBColorSpace];
//  c = [NSColor redColor];
    NSColor* gb = [NSColor colorWithCalibratedRed: 0 green: 0 blue: 1 alpha:1];
    c = [c blendedColorWithFraction: 0.25 ofColor: gb];
c = [[NSColor windowBackgroundColor] colorUsingColorSpace: [NSColorSpace sRGBColorSpace]];
    float r = c.redComponent;
    float g = c.greenComponent;
    float b = c.blueComponent;
    float a = c.alphaComponent;
    trace(@"%f %f %f %f", r, g, b, a);
//    c = [NSColor colorWithCalibratedRed: r green: g blue: b alpha: a];
    _outlineView.backgroundColor = c; // [NSColor colorWithCalibratedRed:0.88 green:0.89 blue:0.92 alpha:1];
    _outlineView.needsDisplay = true;
*/
}

- (void) windowDidResignKey {
/*
    // unfocused: Gradient 0.96 0.96 0.96 -> 0.91, 0.91, 0.91
    // focused: Gradient 0.91 0.92 0.94 -> 0.85 0.87 0.90
    _outlineView.backgroundColor = [NSColor windowBackgroundColor];
    _outlineView.needsDisplay = true;
*/ 
}

- (void) firstResponderChanged {
    NSResponder* fr = [[self.windowControllers[0] window] firstResponder];
    trace(@"first responder changed to %@", fr);
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
    [self sizeOutlineViewToContents];
}

- (void) sizeOutlineViewToContents {
    [_outlineViewDelegate sizeOutlineViewToContents: _outlineView];
}

- (void) outlineViewSelectionDidChange: (NSNotification *) notification  {
    [_tableView deselectAll: null];
    [_tableView reloadData];
    [_tableViewDelegate sizeTableViewToContents: _tableView];
}

+ (BOOL)autosavesInPlace {
    // this is for autosaving documents like text files... see NSDocumentController -setAutosavingDelay:
    return false;
}

+ (BOOL)canConcurrentlyReadDocumentsOfType:(NSString *)typeName {
    return true;
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
        trace(@"Quit - canceled");
    }
    trace(@"");
}

- (void)close {
    NSTableColumn* tc = _tableView.tableColumns[0];
    assert(tc != null);
    [tc removeObserver:self forKeyPath:@"width"];
    [_operationQueue cancelAllOperations];
    [_operationQueue waitUntilAllOperationsAreFinished];
    if (archive != null) {
        [archive close];
        archive = null;
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
    return archive != null;
}

- (void) openArchiveForOperation: (NSOperation*) op {
    // This method is called on the background thread
    assert(![NSThread isMainThread]);
    NSObject<ZGItemFactory>* __block a = [ZG7zip new];
    // a = [ZGFileSystem new];
    NSError* __block error;
    BOOL b = [a readFromURL: _url ofType: _typeName encoding: _encoding
                   document: self operation: (NSOperation*) op error: &error];
    if (!b) {
        a = null;
    }
    ZGDocument* __block doc = self;
    dispatch_async(dispatch_get_main_queue(), ^{
        assert([NSThread isMainThread]);
        if (a != null) {
            archive = a;
            [self reloadOutlineView];
            _heroView.hidden = true;
            _splitView.hidden = false;
            // TODO: or table view if outline view is hidden
            [[self.windowControllers[0] window] makeFirstResponder:_outlineView];
        } else if (error != null) {
            NSAlert* alert = [NSAlert alertWithError: error];
            NSWindowController* wc = doc.windowControllers[0];
            assert(wc.window == doc.windowPresenter.window);
            [doc.windowPresenter presentSheetWithSheet:alert delegate:self
                                        didEndSelector:@selector(didEndOpenError:returnCode:contextInfo:)
                                           contextInfo: null];
        } else {
            // error == null - aborted by user
        }
    });
}

- (void) didEndOpenError: (NSAlert*) a returnCode: (NSInteger)rc contextInfo: (void*) ctx {
    [_window orderOut: null];
}

- (NSString*) askForPasswordFromBackgroundThread {
    assert(![NSThread isMainThread]);
    ZGDocument* __block doc = self;
    assert(_password_semaphore == null);
    _password_semaphore = dispatch_semaphore_create(0);
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
        assert(doc.password_input == null);
        doc.password_input = [[NSTextField alloc] initWithFrame:NSMakeRect(0, 0, 300, 24)];
        doc.password_input.autoresizingMask = NSViewWidthSizable | NSViewMaxXMargin | NSViewMinXMargin;
        [doc.password_input.cell setUsesSingleLineMode: true];
        doc.password_input.stringValue = @"";
        [alert setAccessoryView:doc.password_input];
        [doc.windowPresenter presentSheetWithSheet: alert delegate: self
                                    didEndSelector: @selector(didEndPasswordInput:returnCode:contextInfo:)
                                       contextInfo: null];
    });
    dispatch_semaphore_wait(_password_semaphore, DISPATCH_TIME_FOREVER);
    dispatch_release(_password_semaphore);
    _password_semaphore = null;
    return _password;
}

- (void) didEndPasswordInput: (NSAlert*) a returnCode: (NSInteger)rc contextInfo: (void*) ctx {
    if (rc == NSAlertDefaultReturn) {
        [_password_input validateEditing];
        _password = _password_input.stringValue;
    } else {
        _password = @"";
    }
    _password_input = null;
    dispatch_semaphore_signal(_password_semaphore);
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

- (void) searchFieldAction: (id) sender {
    if (!archive) {
        return;
    }
    NSString* s = [_searchField stringValue];
    // trace(@"%@", s);
    if (!_searchTextColor) {
        _searchTextColor = [_searchField textColor];
    }
    if ([archive setFilter:s]) {
        [_outlineView reloadData];
        [_outlineView expandItem:null expandChildren:true]; // expand all
        [self sizeOutlineViewToContents];
        _searchField.textColor = _searchTextColor;
    } else {
        _searchField.textColor = NSColor.redColor;
    }
}

@end

