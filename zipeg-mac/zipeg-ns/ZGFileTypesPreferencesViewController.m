#import "ZGApp.h"
#import "ZGUtils.h"
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

// "amp", "bz2 bzip2 tbz2 tbz" "msi msp doc xls ppt" "cramfs" "deb" "dmg" "elf" "fat img" "flv" "gz gzip tgz tpz"
// "hfs" "iso img" "lzh lha" "lzma lzma86" "MachO" "mbr" "MsLZ"
// "mub" "nsis" "ntfs img" "exe dll sys" "ppmd pmd" "rar" "r00" "rpm" "001" "squashfs" "swf" "tar" "udf"
// "iso img" "vhd" "wim swm" "xar" "xz txz" "z taz" "zip jar xpi odt ods docx xlsx"

+ (void) initialize {
    trace("%@", getDescription(@[@"zipx"]));
    trace("%@", getDescription(@[@"lzma", @"lzma86"]));
    trace("%@", getDescription(@[@"bz2", @"bzip", @"bzip2", @"tbz2", @"tbz"]));
    trace(@"apps(%@)=%@", @"zip", getApps(@[@"zip"]));

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
    int j = 0;
    NSMutableDictionary* e2u = [NSMutableDictionary dictionaryWithCapacity: a.count];
    NSMutableArray* k = [NSMutableArray arrayWithCapacity: a.count / 2];
    for (int i = 0; i < a.count; i += 2) {
        CFStringRef uti = UTTypeCreatePreferredIdentifierForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)a[i], null);
        CFRelease(uti);
        CFArrayRef at = UTTypeCreateAllIdentifiersForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)a[i], null);
        NSArray* na = (__bridge NSArray*)at;
        for (int j = 0; j < na.count; j++) {
            trace("%@=%@", a[i], na[j]);
        }
        CFRelease(at);
        k[j++] = a[i];
        e2u[a[i]] = a[i + 1];
    }
    keys = k; // to maintain order of extensions (most frequently used on top)
    ext2uti = e2u;

    NSArray* exts = @[
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
//                 @[@"amp"],
                   @[ @"msi" /*, @"msp", @"doc", @"xls", @"ppt" */],
                   @[@"deb"],
                   @[@"dmg"],
//                 @[@"cramfs"],
//                 @[@"elf"],
//                 @[@"fat"],
                   @[@"img"],
                   @[@"iso"],
//                 @[@"flv"],
//                 @[@"hfs"],
                   @[@"lzma", @"lzma86"],
                   @[@"cab"],
                   @[@"chm"],
//                 @[@"xpi", @"odt", @"ods", @"docx", @"xlsx"],
//                 @[@"MachO"],
//                 @[@"mbr"],
//                 @[@"MsLZ"],
//                 @[@"mub"],
                   @[@"nsis"],
//                 @[@"ntfs"],
                   @[@"exe", @"dll" /*, @"sys"*/],
//                 @[@"ppmd", @"pmd"],
//                 @[@"squashfs"],
//                 @[@"swf"],
                   @[@"rpm"],
//                 @[@"udf"],
//                 @[@"vhd"],
//                 @[@"wim", @"swm"],
                   @[@"xar"]];
    for (int i = 0; i < exts.count; i++) {
        NSArray* na = exts[i];
        for (int j = 0; j < na.count; j++) {
            NSString* ext = na[j];
            CFArrayRef at = UTTypeCreateAllIdentifiersForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)ext, null);
            NSArray* utis = (__bridge NSArray*)at;
            NSString* desc = getDescription(exts[i]);
            assert(desc != null);
            NSMutableDictionary* us = [NSMutableDictionary dictionaryWithCapacity: 32];
            NSArray* ua = ext2uti[ext];
            for (NSString* u in ua) {
                us[u] = @true;
            }
            for (int j = 0; j < utis.count; j++) {
                CFStringRef mime = UTTypeCopyPreferredTagWithClass((__bridge CFStringRef)utis[j], kUTTagClassMIMEType);
//              trace("%@=%@ %@ %@", ext, utis[j], desc, mime);
                if (![utis[j] hasPrefix: @"dyn."]) {
                    us[utis[j]] = @true;
                }
                if (mime != null) {
                    CFRelease(mime);
                }
            }
            CFRelease(at);
            NSString* res = @"";
            for (NSString* k in us.allKeys) {
                if (res.length > 0) {
                    res = [NSString stringWithFormat: @"%@, @\"%@\"", res, k];
                } else {
                    res = [NSString stringWithFormat: @"@\"%@\"", k];
                }
            }
            NSLog(@"@\"%@\", @[%@],", ext, res);
        }
    }
    [ZGApp registerApp: true];
}

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
                NSArray* utis = ext2uti[keys[i]];
                for (NSString* uti in utis) {
                     _state[uti] = @true;
                }
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
    return keys.count;
}

- (id)tableView: (NSTableView*) tv objectValueForTableColumn: (NSTableColumn*) tc row:(NSInteger) row {
    if (tc == tv.tableColumns[0]) {
        NSArray* utis = ext2uti[keys[row]];
        return _state[utis[0]];
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
    NSArray* utis = ext2uti[keys[_tableView.selectedRow]];
    for (NSString* uti in utis) {
        NSNumber* b = _state[uti];
        _state[uti] = @(!b.boolValue);
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
