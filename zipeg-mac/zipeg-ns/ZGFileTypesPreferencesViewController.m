#import "ZGApp.h"
#import "ZGUtils.h"
#import "ZGFileTypesPreferencesViewController.h"
#import "ApplicationServices/ApplicationServices.h"

@interface ZGFileTypesPreferencesViewController() {
    NSTableView* _tableView;
    NSButton* _more;
    NSArray* _menus;
    int _rows;
    enum {
        ROWS = 6,
        _minHeight = 260,
        _maxHeight = 850
    };
}
@end

@implementation ZGFileTypesPreferencesViewController

static NSDictionary* ext2uti; // @"zip" -> NSDictionary.allKeys UTIs -> @true
static NSArray* exts; // in UI order @[..., @[@"rar", @"r00"], ...]

+ (void) initialize {
    NSArray* a = @[
      @"zip",    @[@"com.pkware.zip-archive", @"public.zip-archive", @"com.winzip.zip-archive"],
      @"zipx",   @[@"com.winzip.zipx-archive"],
      @"rar",    @[@"com.rarlab.rar-archive"],
      @"r00",    @[@"com.rarlab.rar-archive"],
      @"gz",     @[@"org.gnu.gnu-zip-archive"],
      @"gzip",   @[@"org.gnu.gnu-zip-archive"],
      @"tgz",    @[@"org.gnu.gnu-zip-tar-archive"],
      @"tpz",    @[@"org.gnu.gnu-zip-tar-archive"],
      @"tar",    @[@"public.tar-archive"],
      @"bz2",    @[@"public.bzip2-archive"],
      @"bzip",   @[@"public.bzip2-archive"],
      @"bzip2",  @[@"public.bzip2-archive"],
      @"tbz2",   @[@"public.tar-bzip2-archive"],
      @"tbz",    @[@"public.tar-bzip2-archive"],
      @"7z",     @[@"org.7-zip.7-zip-archive"],
      @"xz",     @[@"org.tukaani.xz-archive"],
      @"txz",    @[@"org.tukaani.tar-xz-archive"],
      @"arj",    @[@"public.arj-archive", @"cx.c3.arj-archive"],
      @"lzh",    @[@"public.lzh-archive", @"public.archive.lha", @"com.winzip.lha-archive", @"cx.c3.lha-archive"],
      @"lha",    @[@"public.lzh-archive", @"public.archive.lha", @"com.winzip.lha-archive", @"cx.c3.lha-archive"],
      @"z",      @[@"public.z-archive", @"com.public.z-archive"],
      @"taz",    @[@"cx.c3.compress-tar-archive"],
      @"ear",    @[@"com.sun.ear-archive"],
      @"war",    @[@"com.sun.web-application-archive", @"com.sun.war-archive"],
      @"jar",    @[@"com.sun.java-archive"],
      @"cbr",    @[@"public.cbr-archive", @"com.public.cbr-archive"],
      @"cbz",    @[@"public.cbz-archive", @"com.public.cbz-archive"],
      @"cpio",   @[@"public.cpio-archive"],
      @"msi",    @[@"com.microsoft.msi-installer"],
      @"deb",    @[@"org.debian.deb-archive"],
      @"dmg",    @[@"com.apple.disk-image-udif"],
      @"img",    @[@"com.apple.disk-image-ndif"],
      @"iso",    @[@"public.iso-image"],
      @"lzma",   @[@"org.tukaani.lzma-archive"],
      @"lzma86", @[@"org.tukaani.lzma-archive"],
      @"cab",    @[@"com.microsoft.cab-archive"],
      @"chm",    @[@"com.microsoft.chm-archive"],
      @"nsis",   @[@"com.nullsoft.nsis"],
      @"exe",    @[@"com.microsoft.windows-executable"],
      @"dll",    @[@"com.microsoft.windows-dynamic-link-library"],
      @"rpm",    @[@"com.redhat.rpm-archive"],
      @"xar",    @[@"com.apple.xar-archive"],
    ];
    NSMutableDictionary* e2u = [NSMutableDictionary dictionaryWithCapacity: a.count];
    for (int i = 0; i < a.count; i += 2) {
        CFArrayRef at = UTTypeCreateAllIdentifiersForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)a[i], null);
        CFIndex n = CFArrayGetCount(at);
        NSArray* ai1 = a[i + 1];
        NSMutableDictionary* us = [NSMutableDictionary dictionaryWithCapacity: (n + ai1.count) * 2];
        for (int j = 0; j < n; j++) {
            CFStringRef cfs = CFArrayGetValueAtIndex(at, j);
            us[(__bridge NSString*)cfs] = @true;
        }
        for (NSString* u in ai1) {
            us[u] = @true;
        }
        CFRelease(at);
        e2u[a[i]] = us;
    }
    ext2uti = e2u;
    exts = @[
      @[@"zip"],
      @[@"rar", @"r00"],
      @[@"7z"],
      @[@"zipx"],
      @[@"lzh", @"lha"],
      @[@"arj"],
      @[@"gz", @"gzip", @"tgz", @"tpz"],
      @[@"tar"],
      @[@"bz2", @"bzip", @"bzip2", @"tbz2", @"tbz"],
      @[@"xz", @"txz"],
      @[@"z", @"taz"],
      @[@"ear"],
      @[@"war"],
      @[@"jar"],
      @[@"cbr"],
      @[@"cbz"],
      @[@"cpio"],
      @[ @"msi"],
      @[@"deb"],
//    @[@"dmg"],  // to change association of .dmg is BAD idea... because of Apple install app workflow
      @[@"img"],
      @[@"iso"],
      @[@"lzma", @"lzma86"],
      @[@"cab"],
      @[@"chm"],
      @[@"nsis"],
      @[@"exe", @"dll"],
      @[@"rpm"],
      @[@"xar"]
    ];
    [ZGApp registerApp: true];
}

- (id) init {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        NSMutableArray* menus = [NSMutableArray arrayWithCapacity: exts.count];
        for (int i = 0; i < exts.count; i++) {
            [menus addObject: NSMenu.new];
        }
        _menus = menus;

        NSView* v = NSView.new;
        v.autoresizesSubviews = true;
        v.autoresizingMask = NSViewHeightSizable | NSViewMinYMargin;
        v.frameSize = NSMakeSize(width, _minHeight);

        _tableView = NSTableView.new;
        _tableView.frame = NSMakeRect(0, 0, width, _maxHeight);
        _tableView.selectionHighlightStyle = NSTableViewSelectionHighlightStyleNone;
        _rows = ROWS;

        NSTableColumn* tc0 = NSTableColumn.new;
        tc0.width = 180;
        NSTextFieldCell* tfc = NSTextFieldCell.new;
        tfc.usesSingleLineMode = true;
        tfc.wraps = false;
        tfc.scrollable = true;
        tc0.dataCell = tfc;
        tfc.alignment = NSRightTextAlignment;
        [_tableView addTableColumn: tc0];

        NSTableColumn* tc1 = NSTableColumn.new;
        tc1.width = 150;
        NSPopUpButtonCell* pub = NSPopUpButtonCell.new;
        tc1.dataCell = pub;
        [_tableView addTableColumn: tc1];

        NSTableColumn* tc2 = NSTableColumn.new;
        tc2.width = width - tc0.width - tc1.width - 8;
        tfc = NSTextFieldCell.new;
        tfc.wraps = false;
        tfc.scrollable = true;
//        tfc.usesSingleLineMode = true;
        tc2.dataCell = tfc;
        [_tableView addTableColumn: tc2];

        _tableView.dataSource = self;
        _tableView.delegate = self;
        _tableView.headerView = null;
        _tableView.backgroundColor = NSColor.clearColor;

        _tableView.rowHeight = 25;

        NSFont* font = ZGBasePreferencesViewController.font;
        CGFloat h = font.boundingRectForFont.size.height;
        CGFloat y = v.frame.size.height - h;
        y = button(v, y, @"", @"All ", @"set Zipeg for these filetypes", @"", self, @selector(allTypes));
        y += h * 0.25;
        _more = createCheckBox(v, &y, @"Show ", @" more file types in the scrollable list below",
                               @"Open files of following types with selected application:", @"com.zipeg.preferences.showAllUTIs");
        _more.target = self;
        _more.action = @selector(moreTypes);
        NSScrollView* sv = NSScrollView.new;
        sv.frame = NSMakeRect(0, h * 0.5, width, y + h * 0.25);
        sv.documentView = _tableView;
        sv.hasVerticalScroller = true;
        sv.hasHorizontalScroller = false;
        sv.autoresizingMask = kSizableWH;
        sv.autoresizesSubviews = true;
        sv.autohidesScrollers = false;
        sv.scrollsDynamically = true; // must be true
        sv.drawsBackground = false;
        [v addSubview: sv];
        self.view = v;
        // dumpViews(self.view);
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (void) allTypes {
    for (int i = 0; i < _rows; i++) {
        setDefaultRoleHandler(exts2utis(exts[i]), NSBundle.mainBundle.bundleIdentifier, false);
    }
    [_tableView reloadData];
    notifyFinder();
}

- (void) moreTypes {
    [self resize: true];
}

- (void) resize: (BOOL) animate {
    NSWindow* w = self.view.window;
    NSRect f = w.frame;
    f.origin.y += f.size.height;
    NSNumber* all = [NSUserDefaults.standardUserDefaults objectForKey: @"com.zipeg.preferences.showAllUTIs"];
    if (!all.boolValue) {
        _rows = ROWS;
        f.size.height = _minHeight + 80;
        f.origin.y -= f.size.height;
        [w setFrame: f display: true animate: animate];
    } else {
        _rows = (int)exts.count;
        f.size.height = _maxHeight + 80;
        f.origin.y -= f.size.height;
        [w setFrame: f display: true animate: animate];
    }
    [_tableView reloadData];
}

+ (BOOL) setMyselfAsDefaultApplicationForFileExtension: (NSString*) ext {
    CFStringRef uti = UTTypeCreatePreferredIdentifierForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)ext, null);
    CFStringRef bi = (__bridge CFStringRef)NSBundle.mainBundle.bundleIdentifier;
    return LSSetDefaultRoleHandlerForContentType(uti, kLSRolesAll, bi) == 0;
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
    NSWindow* w = self.view.window;
    NSScreen* scr = w.screen;
    CGFloat height = MIN(_maxHeight, scr.visibleFrame.size.height - 60);
    self.view.window.contentMaxSize = NSMakeSize(width, height);
    self.view.window.contentMinSize = NSMakeSize(width, _minHeight);
    [self resize: false];
    return _tableView;
}

- (NSInteger) numberOfRowsInTableView: (NSTableView*) tableView {
    return _rows;
}

- (id) tableView: (NSTableView*) tv objectValueForTableColumn: (NSTableColumn*) tc row: (NSInteger) row {
    if (tc == tv.tableColumns[0]) {
        NSString* s = @"";
        for (NSString* e in exts[row]) {
            if (s.length == 0) {
                s = [NSString stringWithString: e];
            } else {
                s = [NSString stringWithFormat: @"%@, %@", s, e];
            }
        }
        return [NSString stringWithFormat: @"%@:", s];
    } else if (tc == tv.tableColumns[1]) {
        NSMenu* m = _menus[row];
        NSPopUpButtonCell* pub = (NSPopUpButtonCell*)tc.dataCell;
        if (m.itemArray.count == 0) {
            [self setupMenu: (int)row cell: pub];
        }
        pub.menu = _menus[row];
        NSString* drh = getDefaultRoleHandler(exts2utis(exts[row]));
        NSMutableArray* apps = [NSMutableArray arrayWithCapacity: pub.menu.itemArray.count];
        NSMutableArray* lcs = [NSMutableArray arrayWithCapacity: pub.menu.itemArray.count];
        for (NSMenuItem* it in pub.menu.itemArray) {
            NSDictionary* d = it.representedObject;
            NSString* id = d[@"id"];
            [apps addObject: id];
            [lcs addObject: id.lowercaseString];
        }
        CFIndex index = [apps indexOfObject: drh];
        if (index == NSNotFound) { // just because WinZip's bindings are fucked up
            index = [lcs indexOfObject: drh.lowercaseString];
        }
        return index == NSNotFound ? @0 : @(index);
    } else {
        return getDescription(exts[row]);
    }
}

- (void)tableView: (NSTableView*) tv setObjectValue: (id) o forTableColumn: (NSTableColumn*) tc row: (NSInteger) row {
    if (tc == tv.tableColumns[1]) {
        NSNumber* n = o;
        NSMenu* m = _menus[row];
        NSMenuItem* it = m.itemArray[n.intValue];
        NSDictionary* d = it.representedObject;
        setDefaultRoleHandler(exts2utis(exts[row]), d[@"id"], true);
    }
}

- (void) tableView: (NSTableView*) v willDisplayCell: (id) cell forTableColumn: (NSTableColumn*) c row: (NSInteger) r {
    NSObject* o = [self tableView: v objectValueForTableColumn: c row: r];
    if ([cell isKindOfClass: NSPopUpButtonCell.class]) {
        NSMenu *m = _menus[r];
        assert(m.itemArray.count > 0);
    } else if ([cell isKindOfClass: NSTextFieldCell.class]) {
        NSTextFieldCell* t = (NSTextFieldCell*)cell;
        t.stringValue = o.description;
    } else {
        assert(false);
    }
}

- (void) setupMenu: (int) r cell: (NSPopUpButtonCell*) pub {
    NSMenu* m = _menus[r];
    NSString* drh = getDefaultRoleHandler(exts[r]);
    if (drh == null) {
        drh = NSBundle.mainBundle.bundleIdentifier;
    }
    NSArray* apps = getApps(exts[r]);
    int sel = -1;
    for (NSString* k in apps) {
        NSDictionary* app = getAppDetails(k);
        NSMenuItem *it = NSMenuItem.new;
        it.title = app[@"name"];
        it.image = app[@"icon"];
        it.image.size = NSMakeSize(16, 16);
        it.tag = (int)m.itemArray.count;
        it.representedObject = app;
        // res[bi] = @{ @"bundle": b, @"icon": icon, @"name": name, @"path": path };
        if ([k isEqualToString: drh]) {
            sel = (int)m.itemArray.count;
        }
        [m insertItem: it atIndex: m.itemArray.count];
    }
    pub.menu = m;
    NSMenuItem *it = m.itemArray[sel < 0 ? 0 : sel];
    pub.title = it.title;
}

static NSArray* exts2utis(NSArray* exts) {
    assert(exts != null && exts.count > 0);
    NSMutableDictionary* us = [NSMutableDictionary dictionaryWithCapacity: exts.count * 16];
    for (NSString* ext in exts) {
        CFArrayRef utis = UTTypeCreateAllIdentifiersForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)ext, null);
        if (utis != null) {
            for (int j = 0; j < CFArrayGetCount(utis); j++) {
                CFStringRef cfs = (CFStringRef)CFArrayGetValueAtIndex(utis,j);
                NSString* uti = [NSString stringWithFormat: @"%@", (__bridge NSString*)cfs];
                us[uti] = @true;
            }
            CFRelease(utis);
        }
        NSMutableDictionary* known = ext2uti[ext];
        for (NSString* uti in known.allKeys) {
            us[uti] = @true;
        }
    }
    return us.allKeys;
}

static BOOL isZip(NSArray* utis) {
    NSDictionary* ziputis = ext2uti[@"zip"];
    for (NSString* u in utis) {
        if (ziputis[u] != null) {
            return true;
        }
    }
    return false;
}

static NSString* getDefaultRoleHandler(NSArray* utis) {
    LSRolesMask roles = isZip(utis) ? (kLSRolesEditor | kLSRolesViewer) : kLSRolesViewer;
    for (NSString* uti in utis) {
        CFStringRef drh = LSCopyDefaultRoleHandlerForContentType((__bridge CFStringRef)uti, roles);
        if (drh != null) {
            NSString* r = [NSString stringWithFormat: @"%@", (__bridge NSString*)drh];
            CFRelease(drh);
            return r;
        }
    }
    return null;
}

static void setDefaultRoleHandler(NSArray* utis, NSString* bi, BOOL notify) {
    LSRolesMask roles = isZip(utis) ? (kLSRolesEditor | kLSRolesViewer) : kLSRolesViewer;
    roles = kLSRolesAll; // this is necessary hack because WinZip is super greedy
    for (NSString* uti in utis) {
        CFStringRef ct = (__bridge CFStringRef)uti;
        CFStringRef id = (__bridge CFStringRef)bi;
        OSStatus r = LSSetHandlerOptionsForContentType(ct, kLSHandlerOptionsIgnoreCreator);
        if (r != noErr) {
            console(@"LSSetHandlerOptionsForContentType error=%d", r);
        }
        r = LSSetDefaultRoleHandlerForContentType(ct, roles, id);
        if (r != noErr) {
            console(@"LSSetDefaultRoleHandlerForContentType error=%d", r);
        }
    }
    if (notify) {
        notifyFinder();
    }
}

static void notifyFinder() {
    // This option is obsolete
    //    NSArray* desktop = NSSearchPathForDirectoriesInDomains(NSDesktopDirectory, NSAllDomainsMask, true);
    //    [NSWorkspace.sharedWorkspace noteFileSystemChanged: desktop[0]];
    // and does not do any good anyway...
    // The icon previews are not updated because they are pooled from quicklook plugins in the Finder
    // In order to update them I need to implement QuickLook plugin. Later... :(
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_LOW, 0), ^{
//      timestamp("notifyFinder");
        NSString* src =
        @"tell application \"Finder\"\n"
        "  try\n"
        "    tell desktop to update items with necessity\n"
        "  end try\n"
        "  set ws to windows\n"
        "  repeat with w in ws\n"
        "    try\n"
        "      tell w to update items with necessity\n"
        "    end try\n"
        "  end repeat\n"
        "end tell";
        NSAppleScript *script = [[NSAppleScript alloc] initWithSource: src];
        NSDictionary *errors = null;
        NSAppleEventDescriptor *descriptor = [script executeAndReturnError: &errors];
        if (errors != null || descriptor == null) {
            console("applescript errors=%@", errors);
        }
//      timestamp("notifyFinder"); // ~ < 200ms
    });
}

static NSString* getDescription(NSArray* exts) {
    NSArray* utis = exts2utis(exts);
    CFStringRef desc = null;
    for (NSString* u in utis) {
        desc = UTTypeCopyDescription((__bridge CFStringRef)u);
        if (desc != null) {
            break;
        }
    }
    if (desc != null) {
        return (__bridge_transfer NSString*)desc;
    }
    // Plan B - no localized description:
    // public.zip-archive "Zip-архив" (base)
    // public.z-archive "Архив Z" (tested with)
    // com.winzip.zipx-archive "WinZip Zipx Archive" (tested with)
    desc = UTTypeCopyDescription((__bridge CFStringRef)@"public.zip-archive");
    NSString* ext = exts[0];
    NSString* res = null;
    if (desc != null) {
        res = (__bridge_transfer NSString*)desc;
        NSString* lc = res.lowercaseString;
        int ix = [lc indexOf: @"zip"];
        if (ix >= 0) {
            if (ix == 0) {
                res = [NSString stringWithFormat:@"%@%@", ext, [res substringFrom: 3]];
            } else if (ix + 3 == lc.length) {
                res = [NSString stringWithFormat:@"%@%@", [res substringFrom: 0 to: ix], ext];
            } else {
                res = [NSString stringWithFormat:@"%@%@%@", [res substringFrom: 0 to: ix], ext, [res substringFrom: ix + 3]];
            }
        } else {
            res = [NSString stringWithFormat:@"%@ archive", ext];
        }
    } else {
        res = [NSString stringWithFormat:@"%@ archive", ext];
    }
    return res;
}

static NSArray* getApps(NSArray* exts) {
    NSArray* utis = exts2utis(exts);
    NSMutableDictionary* bis = [NSMutableDictionary dictionaryWithCapacity: utis.count * 4];
    NSMutableArray* res = [NSMutableArray arrayWithCapacity: 16];
    bis[NSBundle.mainBundle.bundleIdentifier] = @true;
    for (NSString* u in utis) {
        CFArrayRef rhs = LSCopyAllRoleHandlersForContentType((__bridge CFStringRef)u, kLSRolesAll);
        if (rhs != null) {
            for (int k = 0; k < CFArrayGetCount(rhs); k++) {
                CFStringRef rh = (CFStringRef)CFArrayGetValueAtIndex(rhs, k);
                NSString* bi = [NSString stringWithFormat: @"%@", (__bridge NSString*)rh];
                NSDictionary* d = getAppDetails(bi);
                if (bis[bi] == null && d != null) {
                    bis[bi] = @true;
                    [res addObject: d];
                }
            }
            CFRelease(rhs);
        }
    }
    NSArray* sorted = [res sortedArrayUsingComparator: ^NSComparisonResult(id a, id b) {
        NSString *first = ((NSDictionary*)a)[@"name"];
        NSString *second = ((NSDictionary*)a)[@"name"];
        return [first localizedCaseInsensitiveCompare:second];
    }];
    res = [NSMutableArray arrayWithCapacity: sorted.count + 1];
    [res addObject: NSBundle.mainBundle.bundleIdentifier];
    for (NSDictionary* d in sorted) {
        [res addObject: d[@"id"]];
    }
    return res;
}

static NSDictionary* getAppDetails(NSString* bi) {
    NSString* path = [NSWorkspace.sharedWorkspace absolutePathForAppBundleWithIdentifier: bi];
    NSBundle* b = [NSBundle bundleWithPath: path];
    if (path != null && b != null) {
        NSDictionary* bid = [b localizedInfoDictionary];
        NSImage* icon = [NSWorkspace.sharedWorkspace iconForFile: path];
        NSString* name = bid[@"CFBundleDisplayName"];
        if (name == null) {
            name = path.lastPathComponent.stringByDeletingPathExtension;
        }
        if (name != null && icon != null) {
            return @{ @"id": bi, @"bundle": b, @"icon": icon, @"name": name, @"path": path };
        }
    }
    return null;
}

@end
