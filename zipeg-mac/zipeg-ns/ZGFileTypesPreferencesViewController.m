#import "ZGFileTypesPreferencesViewController.h"
#import "ApplicationServices/ApplicationServices.h"

@interface ZGFileTypesPreferencesViewController() {
    NSTextField* _textField;
    NSTableView* _tableView;
    NSMutableDictionary* _state; // uti->bool
}
@end

@implementation ZGFileTypesPreferencesViewController

static NSDictionary* ext2uti;
static NSArray* keys;

+ (void) initialize {
    NSArray* a = @[
        @"zip",   @"public.zip-archive",
        @"rar",   @"com.rarlab.rar-archive",
        @"gz",    @"org.gnu.gnu-zip-archive",
        @"gzip",  @"org.gnu.gnu-zip-archive",
        @"tgz",   @"org.gnu.gnu-zip-tar-archive",
        @"tar",   @"public.tar-archive",
        @"bz2",   @"public.bzip2-archive",
        @"bzip",  @"public.bzip2-archive",
        @"bzip2", @"public.bzip2-archive",
        @"7z",    @"org.7-zip.7-zip-archive",
        @"arj",   @"public.arj-archive",
        @"lzh",   @"public.lzh-archive",
        @"z",     @"com.public.z-archive",
        @"cab",   @"com.microsoft.cab-archive",
        @"chm",   @"com.microsoft.chm-archive",
        @"ear",   @"com.sun.ear-archive",
        @"war",   @"com.sun.war-archive",
        @"cbr",   @"com.public.cbr-archive",
        @"cbz",   @"Comic Book Archive (zip)",
        @"cpio",  @"public.cpio-archive"
    ];
    int j = 0;
    NSMutableDictionary* e2u = [NSMutableDictionary dictionaryWithCapacity: a.count];
    NSMutableArray* k = [NSMutableArray arrayWithCapacity: a.count / 2];
    for (int i = 0; i < a.count; i += 2) {
        k[j++] = a[i];
        e2u[a[i]] = a[i + 1];
    }
    keys = k; // to maintain order of extensions (most frequently used on top)
    ext2uti = e2u;
}

extern char **environ;

- (id) init {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        NSDictionary* s = [NSUserDefaults.standardUserDefaults dictionaryForKey: @"com.zipeg.filetypes"];
        if (s != null && s.count == keys.count) {
            _state = [NSMutableDictionary dictionaryWithDictionary: s];
        } else {
            _state = [NSMutableDictionary dictionaryWithCapacity: keys.count * 2];
            for (int i = 0; i < keys.count; i++) {
                _state[ext2uti[keys[i]]] = @true;
            }
            [NSUserDefaults.standardUserDefaults setObject: _state forKey: @"com.zipeg.filetypes"];
        }
        NSView* v = NSView.new;
        v.autoresizesSubviews = true;
        v.frameSize = NSMakeSize(width, 390);

        _tableView = NSTableView.new;
        _tableView.frame = NSMakeRect(0, 0, width, 390);

        NSTableColumn* tc0 = NSTableColumn.new;
        tc0.width = 22;
        NSButtonCell* cbx = NSButtonCell.new;
        cbx.buttonType = NSSwitchButton;
        tc0.dataCell = cbx;
        [_tableView addTableColumn: tc0];

        NSTableColumn* tc1 = NSTableColumn.new;
        tc1.width = width - tc0.width - 4;
        NSTextFieldCell* tfc = NSTextFieldCell.new;
        tc1.dataCell = tfc;
        [_tableView addTableColumn: tc1];

        _tableView.dataSource = self;
        _tableView.delegate = self;
        _tableView.headerView = null;
        _tableView.backgroundColor = NSColor.clearColor;

        CFStringRef ct = (__bridge CFStringRef)ext2uti[@"zip"];
        CFStringRef id = (__bridge CFStringRef)(NSBundle.mainBundle.bundleIdentifier);
        int roles = kLSRolesAll; // kLSRolesNone | kLSRolesViewer | kLSRolesEditor | kLSRolesShell | kLSRolesAll;
        OSStatus r = LSSetDefaultRoleHandlerForContentType(ct, roles, id); // Finder.app will update items icons when the app Quits
        trace("%d", r);
        char ** e = environ;
        char * a[2];
        a[0] = "";
        a[1] = 0;
        //r = system("/usr/bin/osascript /Users/leo/3ipeg/zipeg-mac/zipeg-ns/Resources/fnotify.scpt");
        //trace("system=%d", r);
        NSArray* desktop = NSSearchPathForDirectoriesInDomains(NSDesktopDirectory, NSAllDomainsMask, true);
        //[NSWorkspace.sharedWorkspace noteFileSystemChanged: desktop[0]];
        NSImage* icns = [NSWorkspace.sharedWorkspace iconForFileType: ext2uti[@"zip"]];
        // FSEvents?


        ct = (__bridge CFStringRef)ext2uti[@"zip"];
        int role = kLSRolesAll; // kLSRolesNone | kLSRolesViewer | kLSRolesEditor | kLSRolesShell | kLSRolesAll;
        CFArrayRef cfa = LSCopyAllRoleHandlersForContentType(ct, role);
        NSArray* nsa = [NSArray arrayWithArray: (__bridge NSArray *)(cfa)];
        CFRelease(cfa);
        trace("%@", nsa);

        for (NSString* bi in nsa) {
            NSString* path = [NSWorkspace.sharedWorkspace absolutePathForAppBundleWithIdentifier: bi];
            NSBundle* b = [NSBundle bundleWithPath: path];
            NSDictionary* bid = [b localizedInfoDictionary];
            NSImage* icon = [NSWorkspace.sharedWorkspace iconForFile: path];
            NSString* name = bid[@"CFBundleDisplayName"];
            if (name == null) {
                name = path.lastPathComponent.stringByDeletingPathExtension;
            }
            trace("\n\n---------\n*** %@ ***\npath=%@\ndisplay=%@\nicon=%@\n%@", bi, path, name, icon, bid);
        }
        NSScrollView* sv = NSScrollView.new;
        sv.frame = NSMakeRect(0, 0, width, 390);
        sv.documentView = _tableView;
        sv.hasVerticalScroller = true;
        sv.hasHorizontalScroller = false;
        sv.autoresizingMask = kSizableWH;
        sv.autoresizesSubviews = true;
        sv.autohidesScrollers = false;
        sv.scrollsDynamically = true; // must be true
        sv.drawsBackground = false;
        v.subviews = @[ sv ];
        self.view = v;
        dumpViews(self.view);
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (NSString*) ident {
    return @"FileTypesPreferences";
}

- (NSImage *) image {
    return [NSImage imageNamed: NSImageNameInfo]; // NSImageNameMultipleDocuments 
}

- (NSString *) label {
    return NSLocalizedString(@"File Types", @"Zipeg File Types Preferences");
}

- (NSView *) initialKeyView {
    NSInteger focusedControlIndex = [[NSApp valueForKeyPath: @"delegate.focusedAdvancedControlIndex"] integerValue];
    return (focusedControlIndex == 0 ? _textField : _tableView);
}

- (NSInteger) numberOfRowsInTableView: (NSTableView*) tableView {
    return keys.count;
}

- (id)tableView: (NSTableView*) tv objectValueForTableColumn: (NSTableColumn*) tc row:(NSInteger) row {
    if (tc == tv.tableColumns[0]) {
        return _state[ext2uti[keys[row]]];
    } else {
        return keys[row];
    }
}

- (void) tableView: (NSTableView*) v willDisplayCell: (id) cell
    forTableColumn: (NSTableColumn*) column row: (NSInteger) row {
    NSObject* o = [self tableView: v objectValueForTableColumn: column row: row];
    if ([cell isKindOfClass: NSButtonCell.class]) {
        NSButtonCell* cbx = (NSButtonCell*)cell;
        cbx.state = isEqual(o, @true) ? NSOnState : NSOffState;
        cbx.title = null;
        cbx.action = @selector(check:);
        cbx.target = self;
    } else if ([cell isKindOfClass: NSTextFieldCell.class]) {
        NSTextFieldCell* t = (NSTextFieldCell*)cell;
        t.stringValue = o.description;
    } else {
        assert(false);
    }
}

- (void) check: (id) sender {
    trace("check %ld", _tableView.selectedRow);
    NSString* uti = ext2uti[keys[_tableView.selectedRow]];
    NSNumber* b = _state[uti];
    _state[uti] = @(!b.boolValue);
    self.view.needsDisplay = true;
}

@end
