#import "ZGApp.h"
#import "ZGUtils.h"
#import "ZGFileTypesPreferencesViewController.h"
#import "ApplicationServices/ApplicationServices.h"

@interface ZGFileTypesPreferencesViewController() {
    NSTextField* _textField;
    NSTableView* _tableView;
}
@end

@implementation ZGFileTypesPreferencesViewController

static NSDictionary* ext2uti; // @"zip" -> NSDictionary.allKeys UTIs -> @true
static NSArray* exts; // in UI order @[..., @[@"rar", @"r00"], ...]

+ (void) initialize {
/*
    trace("%@", getDescription(@[@"zipx"]));
    trace("%@", getDescription(@[@"lzma", @"lzma86"]));
    trace("%@", getDescription(@[@"bz2", @"bzip", @"bzip2", @"tbz2", @"tbz"]));
    trace(@"apps(%@)=%@", @"zip", getApps(@[@"zip"]));
*/
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
      @[@"zipx"],
      @[@"rar", @"r00"],
      @[@"gz", @"gzip", @"tgz", @"tpz"],
      @[@"tar"],
      @[@"bz2", @"bzip", @"bzip2", @"tbz2", @"tbz"],
      @[@"7z"],
      @[@"xz", @"txz"],
      @[@"arj"],
      @[@"lzh", @"lha"],
      @[@"z", @"taz"],
      @[@"ear"],
      @[@"war"],
      @[@"jar"],
      @[@"cbr"],
      @[@"cbz"],
      @[@"cpio"],
      @[ @"msi"],
      @[@"deb"],
      @[@"dmg"],
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
        NSDictionary* s = [NSUserDefaults.standardUserDefaults dictionaryForKey: @"com.zipeg.filetypes"];
        [NSUserDefaults.standardUserDefaults setObject: s forKey: @"com.zipeg.filetypes"];
//???
        NSView* v = NSView.new;
        v.autoresizesSubviews = true;
        int width2 = width + 30;
        v.frameSize = NSMakeSize(width2, 390);

        _tableView = NSTableView.new;
        _tableView.frame = NSMakeRect(0, 0, width2, 390);
        _tableView.selectionHighlightStyle = NSTableViewSelectionHighlightStyleNone;

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
        NSButtonCell* cbx = NSPopUpButtonCell.new;
        tc1.dataCell = cbx;
        [_tableView addTableColumn: tc1];

        NSTableColumn* tc2 = NSTableColumn.new;
        tc2.width = width2 - tc0.width - tc1.width - 8;
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

        NSArray* utis = ext2uti[@"zip"];
        [self alwaysOpenFile];

        for (NSString* uti in utis) {
            CFStringRef ct = (__bridge CFStringRef)uti;
            CFStringRef id = (__bridge CFStringRef)(NSBundle.mainBundle.bundleIdentifier);
            OSStatus r = noErr;
            r = LSSetHandlerOptionsForContentType(ct, kLSHandlerOptionsIgnoreCreator);
            trace("LSSetHandlerOptionsForContentType %@ %d", uti, r);
            // kLSRolesNone | kLSRolesViewer | kLSRolesEditor | kLSRolesShell | kLSRolesAll;
            int roles = kLSRolesNone | kLSRolesViewer | kLSRolesEditor | kLSRolesShell;
            r = LSSetDefaultRoleHandlerForContentType(ct, roles, id); // Finder.app will update items icons when the app Quits
            trace("LSSetDefaultRoleHandlerForContentType %@ %d", ct, r);
            roles = kLSRolesAll;
            r = LSSetDefaultRoleHandlerForContentType(ct, roles, id); // Finder.app will update items icons when the app Quits
            trace("LSSetDefaultRoleHandlerForContentType %@ %d", ct, r);
            roles = kLSRolesEditor;
            r = LSSetDefaultRoleHandlerForContentType(ct, roles, id); // Finder.app will update items icons when the app Quits
            trace("LSSetDefaultRoleHandlerForContentType %@ %d", ct, r);

            CFStringRef drh = LSCopyDefaultRoleHandlerForContentType(ct, kLSRolesNone);
            NSString* bi = (__bridge NSString *)(drh);
            NSURL* appURL = [NSWorkspace.sharedWorkspace URLForApplicationWithBundleIdentifier: bi];
            trace("LSCopyDefaultRoleHandlerForContentType=%@ URLForApplicationWithBundleIdentifier=%@", drh, appURL);
            CFRelease(drh);

            NSArray* desktop = NSSearchPathForDirectoriesInDomains(NSDesktopDirectory, NSAllDomainsMask, true);
            [NSWorkspace.sharedWorkspace noteFileSystemChanged: desktop[0]];
            NSString* fullPath = @"/Users/leo/Desktop/quincy-absolutenoobcocoacheckboxes-fb3537315428.zip";
            [NSWorkspace.sharedWorkspace noteFileSystemChanged: fullPath];

            NSFileManager* fm = NSFileManager.defaultManager;
            NSError* err = null;
            NSDictionary* attrs = [fm attributesOfItemAtPath: fullPath error: &err];
            [fm setAttributes: attrs ofItemAtPath: fullPath error: &err];

            NSString* appName;
            NSString* type;
            BOOL b = [NSWorkspace.sharedWorkspace  getInfoForFile: fullPath application: &appName type: &type];
            trace("getInfoForFile=%d %@ %@", b, type, appName);
        }
        NSScrollView* sv = NSScrollView.new;
        sv.frame = NSMakeRect(0, 0, width2, 390);
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
        // dumpViews(self.view);
    }
    return self;
}


+ (BOOL) setMyselfAsDefaultApplicationForFileExtension: (NSString*) ext {
    CFStringRef uti = UTTypeCreatePreferredIdentifierForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)ext, null);
    CFStringRef bi = (__bridge CFStringRef)NSBundle.mainBundle.bundleIdentifier;
    return LSSetDefaultRoleHandlerForContentType(uti, kLSRolesAll, bi) == 0;
}

- (void) alwaysOpenFile {
    FSRef ref;
    NSString* fp = @"/Users/leo/Desktop/quincy-absolutenoobcocoacheckboxes-fb3537315428.zip";
    OSStatus os_status = FSPathMakeRef((const UInt8 *)fp.fileSystemRepresentation, &ref, null);
    assert(os_status == noErr);
    CFStringRef type = null;
    LSCopyItemAttribute(&ref, kLSRolesNone, kLSItemContentType, (CFTypeRef *)&type);
//    LSSetDefaultRoleHandlerForContentType(type, kLSRolesAll, (CFStringRef) [[NSBundle bundleWithPath:[iObject singleFilePath]] bundleIdentifier]);
    CFRelease(type);
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
    return exts.count;
}

- (id)tableView: (NSTableView*) tv objectValueForTableColumn: (NSTableColumn*) tc row:(NSInteger) row {
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
        NSDictionary* utis = ext2uti[exts[row]];
        return utis;
    } else {
        return getDescription(exts[row]);
    }
}

- (void) tableView: (NSTableView*) v willDisplayCell: (id) cell
    forTableColumn: (NSTableColumn*) column row: (NSInteger) row {
    NSObject* o = [self tableView: v objectValueForTableColumn: column row: row];
    if ([cell isKindOfClass: NSButtonCell.class]) {
        NSPopUpButtonCell* cbx = (NSPopUpButtonCell*)cell;
        NSMenu *m = NSMenu.new;
        NSMenuItem *it = [NSMenuItem.alloc initWithTitle: @"bla" action: null keyEquivalent:@""];
        it.image = ZGApp.appIcon16x16;
        it.tag = 123;
        [m insertItem: it atIndex: m.itemArray.count];
        it = [NSMenuItem.alloc initWithTitle: @"foo" action: null keyEquivalent:@""];
        it.image = ZGApp.appIcon16x16;
        it.tag = 231;
        [m insertItem: it atIndex: m.itemArray.count];
        it = [NSMenuItem.alloc initWithTitle: @"bar" action: null keyEquivalent:@""];
        it.tag = 321;
        it.image = ZGApp.appIcon16x16;
        [m insertItem: it atIndex: m.itemArray.count];
        [cbx setMenu: m];
/*
        cbx.state = isEqual(o, @true) ? NSOnState : NSOffState;
        cbx.title = null;
        cbx.action = @selector(check:);
        cbx.target = self;
*/
    } else if ([cell isKindOfClass: NSTextFieldCell.class]) {
        NSTextFieldCell* t = (NSTextFieldCell*)cell;
        t.stringValue = o.description;
    } else {
        assert(false);
    }
}

/*
- (void)drawSelectionInRect:(NSRect)dirtyRect {
    if (self.selectionHighlightStyle != NSTableViewSelectionHighlightStyleNone) {
        NSRect selectionRect = NSInsetRect(self.bounds, 2.5, 2.5);
        [[NSColor colorWithCalibratedWhite:.65 alpha:1.0] setStroke];
        [[NSColor colorWithCalibratedWhite:.82 alpha:1.0] setFill];
        NSBezierPath *selectionPath = [NSBezierPath bezierPathWithRoundedRect:selectionRect xRadius:6 yRadius:6];
        [selectionPath fill];
        [selectionPath stroke];
    }
}
*/

- (void) check: (id) sender {
    trace("check %ld", _tableView.selectedRow);
    NSDictionary* utis = ext2uti[exts[_tableView.selectedRow]];
    for (NSString* uti in utis.allKeys) {
/*
        NSNumber* b = _state[uti];
        _state[uti] = @(!b.boolValue);
*/
    }
    self.view.needsDisplay = true;
}

static NSString* getDescription(NSArray* exts) {
    assert(exts != null && exts.count > 0);
    CFStringRef desc = null;
    for (NSString* ext in exts) {
        CFArrayRef utis = UTTypeCreateAllIdentifiersForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)ext, null);
        if (utis != null) {
            for (int j = 0; j < CFArrayGetCount(utis) && desc == null; j++) {
                desc = UTTypeCopyDescription((CFStringRef)CFArrayGetValueAtIndex(utis,j));
            }
            CFRelease(utis);
        }
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

static NSDictionary* getApps(NSArray* exts) {
    NSMutableDictionary* res = [NSMutableDictionary dictionaryWithCapacity: exts.count * 16];
    for (NSString* ext in exts) {
        CFArrayRef cts = UTTypeCreateAllIdentifiersForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)ext, null);
        if (cts != null) {
            for (int j = 0; j < CFArrayGetCount(cts); j++) {
                CFStringRef ct = (CFStringRef)CFArrayGetValueAtIndex(cts, j);
                CFArrayRef rhs = LSCopyAllRoleHandlersForContentType(ct, kLSRolesAll);
                for (int k = 0; k < CFArrayGetCount(rhs); k++) {
                    CFStringRef rh = (CFStringRef)CFArrayGetValueAtIndex(rhs, k);
                    NSString* bi = [NSString stringWithFormat: @"%@", (__bridge NSString*)rh];
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
                            res[bi] = @{ @"bundle": b, @"icon": icon, @"name": name, @"path": path };
                        }
                    }
                }
            }
            CFRelease(cts);
        }
    }
    return res;
}

@end
