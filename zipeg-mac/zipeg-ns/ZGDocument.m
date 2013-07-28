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


@interface ZGOutlineHeaderView : NSTableHeaderView {
    
}
@property ZGDocument* document;
@end

@interface ZGDocument() {
    NSObject<ZGItemFactory>* archive;
    NSWindow* __weak window;
}
/*
@property (weak) IBOutlet NSSearchField* searchField;
@property (weak) IBOutlet NSView* contentView;
@property (weak) IBOutlet NSToolbar* toolbar;
@property (weak) IBOutlet NSSplitView* splitView;
@property (weak) IBOutlet NSLevelIndicator* levelIndicator;
@property (strong) IBOutlet NSMenu *tableRowContextMenu;
*/
@property NSSearchField* searchField;
@property NSView* contentView;
@property NSToolbar* toolbar;
@property NSSplitView* splitView;
@property NSLevelIndicator* levelIndicator;
@property NSMenu *tableRowContextMenu;

@property NSTextFieldCell* textCell;
@property ZGOutlineViewDelegate* outlineViewDelegate; // TODO: can be local
@property ZGOutlineViewDataSource* outlineViewDataSource; // TODO: can be local
@property ZGTableViewDelegate* tableViewDelegate; // TODO: can be local
@property ZGTableViewDataSource* tableViewDatatSource; // TODO: can be local
@property ZGSplitViewDelegate* splitViewDelegate; // TODO: can be local
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

- (IBAction) searchFieldAction: (id)sender;
- (void) openArchiveForOperation: (NSOperation*) op;

@end

// TODO: this is not a good place for any controls
// because I intend to turn this view OFF for plain (folderless) archives
// also it probably better to have ZGOutlineHeaderViewCell implemented?
@implementation ZGOutlineHeaderView

- (id) initWithFrame:(NSRect)frame {
    frame.size.height = 17;
    self = [super initWithFrame:frame];
    self.autoresizesSubviews = true;
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

- (void)  dealloc {
    dealloc_count(self);
    [[NSNotificationCenter defaultCenter] removeObserver:self];
    trace(@"");
}

- (void)makeWindowControllers {
    ZGWindowController* wc = [ZGWindowController new];
    [self addWindowController: wc];
    trace("wc.document=%@ %s (self %@)", wc.document, wc.document == self ? "==" : "!=", self);
    [wc window]; // this actually loads Nib (see docs)
}

/* TODO: gone! because of makeWindowControllers
- (NSString*) windowNibName {
    return @"ZGDocument";
}
*/
 
- (void) reloadOutlineView {
    if (_outlineView != null && archive != null) {
        _outlineViewDataSource = [[ZGOutlineViewDataSource alloc] initWithDocument: self andRootItem: archive.root];
        _outlineView.dataSource = _outlineViewDataSource;
        [_outlineView reloadData];
        NSIndexSet* is = [NSIndexSet indexSetWithIndex:0];
        [_outlineView selectRowIndexes:is byExtendingSelection:false];
        _tableViewDatatSource = [[ZGTableViewDataSource alloc] initWithDocument: self];
        _tableView.dataSource = _tableViewDatatSource;
        [_tableView reloadData];
        dispatch_async(dispatch_get_current_queue(), ^{
            //          [_outlineView expandItem:null expandChildren:true];
            //          [self sizeOutlineViewToContents];
            [_outlineViewDelegate expandOne:_outlineView];
        });
    }
}

static void dumpViews(NSView* v, int level) {
    NSString* indent = @"";
    for (int i = 0; i < level; i++) {
        indent = [indent stringByAppendingString:@"    "];
    }
    trace(@"%@%@ %@", indent, v.class, NSStringFromRect(v.frame));
    if (v.subviews != null) {
        for (id s in v.subviews) {
            dumpViews(s, level + 1);
        }
    }
}

static NSSplitView* createSplitView(NSRect r, NSView* left, NSView* right) {
    NSSplitView* sv = [[NSSplitView alloc] initWithFrame: r];
    sv.vertical = true;
    sv.dividerStyle = NSSplitViewDividerStyleThin;
    sv.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
    sv.autoresizesSubviews = true;
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

- (void)windowControllerWillLoadNib:(NSWindowController *)windowController {
    trace(@"");
}

- (void) windowControllerDidLoadNib: (NSWindowController*) controller {
    // this is called after readFromURL
    window = controller.window;
    window.collectionBehavior = NSWindowCollectionBehaviorFullScreenPrimary;
    dumpViews(window.contentView, 0);
    [super windowControllerDidLoadNib:controller];
    _contentView = [[NSView alloc] initWithFrame: [window.contentView frame]];
    NSRect bounds = _contentView.frame;
    bounds.origin.y += 30;
    bounds.size.height -= 60;
    NSRect tbounds = bounds;
    tbounds.size.width /= 2;
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
    dumpViews(_contentView, 0);
    
    assert(_contentView != null);
    assert(controller.window.contentView == _contentView);
    assert(_tableView != null);
//  assert(_toolbar != null);
//  assert(_levelIndicator != null);
    assert(_splitView != null);
    assert(_outlineView != null);

    NSTableColumn* tableColumn = [NSTableColumn new];
    [_outlineView addTableColumn: tableColumn];
    _outlineView.outlineTableColumn = tableColumn;
    tableColumn.dataCell = [ZGImageAndTextCell new];
    tableColumn.minWidth = 100;
    tableColumn.maxWidth = 10000;
    tableColumn.editable = true;
    assert(_outlineView.outlineTableColumn == tableColumn);
    assert(_outlineView.tableColumns[0] == tableColumn);

    tableColumn = [NSTableColumn new];
    [_tableView addTableColumn: tableColumn];
    tableColumn.dataCell = [ZGImageAndTextCell new];
    tableColumn.minWidth = 100;
    tableColumn.maxWidth = 10000;
    tableColumn.editable = true;
    assert(_tableView.tableColumns[0] == tableColumn);

    NSSortDescriptor *sd = [NSSortDescriptor sortDescriptorWithKey:@"name" ascending:YES
                                                selector:@selector(localizedCaseInsensitiveCompare:)];
/*
    NSSortDescriptor* sd = [NSSortDescriptor sortDescriptorWithKey:@"date" ascending: true
        comparator:^NSComparisonResult(id o1, id o2) {
            return NSOrderedSame;
    }];
*/
    tableColumn.sortDescriptorPrototype = sd;
    tableColumn.resizingMask = NSTableColumnAutoresizingMask | NSTableColumnUserResizingMask;
    [tableColumn addObserver:self forKeyPath:@"width" options: 0 context: null];
    for (int i = 1; i < 3; i++) {
        tableColumn = [NSTableColumn new];
        [_tableView addTableColumn: tableColumn];
        assert(_tableView.tableColumns[i] == tableColumn);
        tableColumn.dataCell = [NSTextFieldCell new];
        tableColumn.minWidth = 100;
        tableColumn.maxWidth = 10000;
        tableColumn.editable = true;
    }
    [controller.window addObserver:self forKeyPath:@"firstResponder" options: 0 context: null];
    
    _contentView.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
    _outlineView.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
    _outlineView.allowsEmptySelection = false;
    _outlineView.menu = _tableRowContextMenu;

    _tableView.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
    _tableView.allowsColumnReordering = true;
    _tableView.allowsColumnResizing = true;
    _tableView.allowsMultipleSelection = true;
    _tableView.allowsColumnSelection = false;
    _tableView.allowsEmptySelection = true; // otherwise deselectAll won't work
    _tableView.menu = _tableRowContextMenu;
    
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
    _outlineView.headerView.autoresizingMask =  NSViewWidthSizable | NSViewMaxXMargin;
    
    
    NSClipView * clipView = [[_outlineView enclosingScrollView] contentView];
    clipView.postsFrameChangedNotifications = true;
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(oulineViewContentBoundsDidChange:)
                                                 name:NSViewBoundsDidChangeNotification
                                               object:clipView];
    [[NSNotificationCenter defaultCenter] addObserver:self
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
}

- (void)observeValueForKeyPath: (NSString*) keyPath ofObject: (id) o change: (NSDictionary*)change context: (void*) context {
    if ([keyPath isEqualToString:@"firstResponder"]) {
        NSResponder* fr = [[self.windowControllers[0] window] firstResponder];
        // trace(@"first responder changed to %@", fr);
        if (fr == _tableView) {
            [_tableViewDelegate tableViewBecameFirstResponder: _tableView];
        }
        return;
    }
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

- (void)close {
/* TODO: later
    NSTableColumn* tc = _tableView.tableColumns[0];
    assert(tc != null);
    [tc removeObserver:self forKeyPath:@"width"];
*/
/* TODO:
 it is impossible to add remove window FirstResponder observers w/o subclassing NSWindowController
 which is actually recommended by Apple:
 https://developer.apple.com/library/mac/#documentation/DataManagement/Conceptual/DocBasedAppProgrammingGuideForOSX/KeyObjects/KeyObjects.html
    NSWindow* w = [self.windowControllers[0] window];
    assert(w != null);
    [w removeObserver:self forKeyPath:@"firstResponder"];
*/
    [_operationQueue cancelAllOperations];
    [_operationQueue waitUntilAllOperationsAreFinished];
    [super close];
    trace_allocs();
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
    // this is called before windowControllerDidLoadNib
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
        } else {
            NSAlert* alert = [NSAlert alertWithError:error];
            NSWindowController* wc = doc.windowControllers[0];
            assert(wc.window == doc.windowPresenter.window);
            [doc.windowPresenter presentSheetWithSheet:alert delegate:self
                                        didEndSelector:@selector(didEndPresentedAlert:returnCode:contextInfo:)
                                           contextInfo: null];
        }
    });
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


- (void) didEndPresentedAlert: (NSAlert*) a returnCode: (NSInteger)rc contextInfo: (void*) ctx {
    [self close];
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

- (IBAction) searchFieldAction: (id) sender {
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
        [_outlineView expandItem:null expandChildren:true];
        [self sizeOutlineViewToContents];
        _searchField.textColor = _searchTextColor;
    } else {
        _searchField.textColor = NSColor.redColor;
    }
}

@end
