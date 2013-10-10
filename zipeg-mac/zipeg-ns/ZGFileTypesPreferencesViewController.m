#import "ZGApp.h"
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
    NSArray* a = @[
        @"zip",   @[@"public.zip-archive", @"com.winzip.zip-archive", @"com.pkware.zip-archive"],
        @"zipx",  @[@"com.winzip.zipx-archive"],
        @"rar",   @[@"com.rarlab.rar-archive"],
        @"gz",    @[@"org.gnu.gnu-zip-archive"],
        @"gzip",  @[@"org.gnu.gnu-zip-archive"],
        @"tgz",   @[@"org.gnu.gnu-zip-tar-archive"],
        @"tar",   @[@"public.tar-archive"],
        @"bz2",   @[@"public.bzip2-archive"],
        @"bzip",  @[@"public.bzip2-archive"],
        @"bzip2", @[@"public.bzip2-archive"],
        @"7z",    @[@"org.7-zip.7-zip-archive"],
        @"xz",    @[@"org.tukaani.xz-archive"],
        @"arj",   @[@"public.arj-archive"],
        @"lzh",   @[@"public.lzh-archive"],
        @"z",     @[@"com.public.z-archive"],
        @"cab",   @[@"com.microsoft.cab-archive"],
        @"chm",   @[@"com.microsoft.chm-archive"], // dyn.age80g4dr not known to Apple
        @"ear",   @[@"com.sun.ear-archive"],
        @"war",   @[@"com.sun.war-archive"],
        @"cbr",   @[@"com.public.cbr-archive"],
        @"cbz",   @[@"com.public.cbz-archive"],
        @"cpio",  @[@"public.cpio-archive"]
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
                   @[@"zip", @"jar" /*, @"xpi", @"odt", @"ods", @"docx", @"xlsx" */],
                   @"zipx",
                   @[@"rar", @"r00"],
                   @[@"gz", @"gzip", @"tgz", @"tpz"],
                   @"tar",
                   @[@"bz2", @"bzip", @"bzip2", @"tbz2", @"tbz"],
                   @"7z",
                   @[@"xz", @"txz"],
                   @"arj",
                   @[@"lzh", @"lha"],
                   @[@"z", @"taz"],
                   @"cab",
//                 @"chm",
                   @"ear",
                   @"war",
                   @"cbr",
                   @"cbz",
                   @"cpio",
//                 @"amp",
                   @[ @"msi" /*, @"msp", @"doc", @"xls", @"ppt" */],
                   @"deb",
                   @"dmg",
//                 @"cramfs",
//                 @"elf",
//                 @"fat",
                   @"img",
                   @"iso",
//                 @"flv",
//                 @"hfs",
                   @[@"lzma", @"lzma86"],
//                 @"MachO",
//                 @"mbr",
//                 @"MsLZ",
//                 @"mub",
                   @"nsis",
//                 @"ntfs",
                   @[@"exe", @"dll" /*, @"sys"*/],
//                 @[@"ppmd", @"pmd"],
//                 @"squashfs",
//                 @"swf",
                   @"rpm",
//                 @"udf",
//                 @"vhd",
//                 @[@"wim", @"swm"],
                   @"xar"];
    for (int i = 0; i < exts.count; i++) {
        NSObject* o = exts[i];
        if ([o isKindOfClass: NSString.class]) {
            trace("\n------------");
            NSString* ext = exts[i];
            CFArrayRef at = UTTypeCreateAllIdentifiersForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)ext, null);
            NSArray* utis = (__bridge NSArray*)at;
            for (int j = 0; j < utis.count; j++) {
                CFStringRef desc = UTTypeCopyDescription((__bridge CFStringRef)utis[j]);
                CFStringRef mime = UTTypeCopyPreferredTagWithClass((__bridge CFStringRef)utis[j], kUTTagClassMIMEType);
                trace("%@=%@ %@ %@", ext, utis[j], desc, mime);
                if (mime != null) {
                    CFRelease(mime);
                }
                if (desc != null) {
                    CFRelease(desc);
                }
            }
            CFRelease(at);
        } else {
            NSArray* na = exts[i];
            trace("\n------------");
            for (int j = 0; j < na.count; j++) {
                NSString* ext = na[j];
                CFArrayRef at = UTTypeCreateAllIdentifiersForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)ext, null);
                NSArray* utis = (__bridge NSArray*)at;
                for (int j = 0; j < utis.count; j++) {
                    CFStringRef desc = UTTypeCopyDescription((__bridge CFStringRef)utis[j]);
                    CFStringRef mime = UTTypeCopyPreferredTagWithClass((__bridge CFStringRef)utis[j], kUTTagClassMIMEType);
                    trace("%@=%@ %@ %@", ext, utis[j], desc, mime);
                    if (mime != null) {
                        CFRelease(mime);
                    }
                    if (desc != null) {
                        CFRelease(desc);
                    }
                }
                CFRelease(at);
            }
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



//          NSImage* icns = [NSWorkspace.sharedWorkspace iconForFileType: uti];

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
/*
        char ** e = environ;
        char * a[2];
        a[0] = "";
        a[1] = 0;
        //r = system("/usr/bin/osascript /Users/leo/3ipeg/zipeg-mac/zipeg-ns/Resources/fnotify.scpt");
        //trace("system=%d", r);
        NSArray* desktop = NSSearchPathForDirectoriesInDomains(NSDesktopDirectory, NSAllDomainsMask, true);
        [NSWorkspace.sharedWorkspace noteFileSystemChanged: desktop[0]];
//      FSEvents?
*/
#if 1
        for (NSString* uti in utis) {
            CFStringRef ct = (__bridge CFStringRef)uti;
            int role = kLSRolesAll; // kLSRolesNone | kLSRolesViewer | kLSRolesEditor | kLSRolesShell | kLSRolesAll;
            CFArrayRef cfa = LSCopyAllRoleHandlersForContentType(ct, role);
            NSArray* nsa = [NSArray arrayWithArray: (__bridge NSArray *)(cfa)];
            CFRelease(cfa);
            trace("uti=%@ bundles=%@", uti, nsa);
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
        }
#endif
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

@end
