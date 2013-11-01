#import "ZGLocalize.h"

@implementation ZGLocalize

// ls -ld /System/Library/Frameworks/AppKit.framework/Versions/C/Resources/*.lproj
// find /Library -name "ru.lproj"
// find /System -name "ru.lproj"

+ (void) collect {
    NSArray* frameworks = @[
                            @"/System/Library/PrivateFrameworks/IMDAppleServices.framework",
                            @"/Library/Application Support/iLifeMediaBrowser/Plug-Ins/iLMBAperture31Plugin.ilmbplugin",
                            @"/Library/Application Support/Apple/Mail/Stationery/Apple/Contents/Resources/Stationery/Contents/Resources/Sticky.mailstationery",
                            @"/Library/Widgets/Translation.wdgt",
                            @"/Library/Widgets/Dictionary.wdgt/Dictionary.widgetplugin",
                            @"/System/Library/Frameworks/Quartz.framework/Versions/A/Frameworks/ImageKit.framework",
                            @"/System/Library/CoreServices/Archive Utility.app",
                            @"/System/Library/Frameworks/CoreServices.framework",
                            @"/System/Library/Frameworks/AVFoundation.framework",
                            @"/System/Library/Frameworks/AddressBook.framework",
                            @"/System/Library/Frameworks/AppleScriptKit.framework",
                            @"/System/Library/Frameworks/AppleShareClientCore.framework",
                            @"/System/Library/Frameworks/ApplicationServices.framework",
                            @"/System/Library/Frameworks/AudioToolbox.framework",
                            @"/System/Library/Frameworks/Automator.framework",
                            @"/System/Library/Frameworks/CFNetwork.framework",
                            @"/System/Library/Frameworks/CalendarStore.framework",
                            @"/System/Library/Frameworks/Carbon.framework",
                            @"/System/Library/Frameworks/Collaboration.framework",
                            @"/System/Library/Frameworks/CoreData.framework",
                            @"/System/Library/Frameworks/CoreFoundation.framework",
                            @"/System/Library/Frameworks/CoreLocation.framework",
                            @"/System/Library/Frameworks/CoreMIDI.framework",
                            @"/System/Library/Frameworks/CoreMediaIO.framework",
                            @"/System/Library/Frameworks/CoreWLAN.framework",
                            @"/System/Library/Frameworks/DiscRecording.framework",
                            @"/System/Library/Frameworks/DiscRecordingUI.framework",
                            @"/System/Library/Frameworks/DiskArbitration.framework",
                            @"/System/Library/Frameworks/EventKit.framework",
                            @"/System/Library/Frameworks/Foundation.framework",
                            @"/System/Library/Frameworks/GLUT.framework",
                            @"/System/Library/Frameworks/GameKit.framework",
                            @"/System/Library/Frameworks/ICADevices.framework",
                            @"/System/Library/Frameworks/IOBluetooth.framework",
                            @"/System/Library/Frameworks/IOKit.framework",
                            @"/System/Library/Frameworks/ImageCaptureCore.framework",
                            @"/System/Library/Frameworks/ImageIO.framework",
                            @"/System/Library/Frameworks/InstallerPlugins.framework",
                            @"/System/Library/Frameworks/InstantMessage.framework",
                            @"/System/Library/Frameworks/JavaVM.framework",
                            @"/System/Library/Frameworks/MediaToolbox.framework",
                            @"/System/Library/Frameworks/Message.framework",
                            @"/System/Library/Frameworks/OSAKit.framework",
                            @"/System/Library/Frameworks/OpenDirectory.framework",
                            @"/System/Library/Frameworks/PreferencePanes.framework",
                            @"/System/Library/Frameworks/PubSub.framework",
                            @"/System/Library/Frameworks/QTKit.framework",
                            @"/System/Library/Frameworks/Quartz.framework",
                            @"/System/Library/Frameworks/QuickLook.framework",
                            @"/System/Library/Frameworks/QuickTime.framework",
                            @"/System/Library/Frameworks/ScreenSaver.framework",
                            @"/System/Library/Frameworks/Security.framework",
                            //                         @"/System/Library/Frameworks/SecurityFoundation.framework",
                            @"/System/Library/Frameworks/SecurityInterface.framework",
                            @"/System/Library/Frameworks/Social.framework",
                            @"/System/Library/Frameworks/SyncServices.framework",
                            @"/System/Library/Frameworks/SystemConfiguration.framework",
                            @"/System/Library/Frameworks/TWAIN.framework",
                            @"/System/Library/Frameworks/WebKit.framework",
                            @"/System/Library/Frameworks/AppKit.framework",
                            @"/System/Library/Frameworks/Carbon.framework",
                            @"/System/Library/Frameworks/Carbon.framework/Versions/A/Frameworks/HIToolbox.framework"
                            ];
    for (NSString* fw in frameworks) {
        //        NSBundle* systemBundle = [NSBundle bundleForClass:[NSButton class]];
        int count = 0;
        NSBundle* systemBundle = [NSBundle bundleWithPath: fw];
        NSArray* localizations = systemBundle.localizations;
        NSLog(@"%@ localizations=%@", fw, localizations);
        //  for (NSString *language in [NSUserDefaults.standardUserDefaults objectForKey:@"AppleLanguages"]) {
        for (NSString *language in @[@"ru"] /*localizations*/) {
            NSBundle *bundle = [NSBundle bundleWithPath:[systemBundle pathForResource:language ofType:@"lproj"]];
            NSFileManager* fm = NSFileManager.defaultManager;
            NSArray* array = [fm contentsOfDirectoryAtPath: bundle.resourcePath error: null];
            for (NSString* file in array) {
                if ([file hasSuffix:@".strings"]) {
                    NSString* name = [file stringByDeletingPathExtension];
                    NSString* stringsPath = [bundle pathForResource: name ofType:@"strings"];
                    NSDictionary *dict = [NSDictionary dictionaryWithContentsOfFile:stringsPath];
                    for (NSString* key in dict.allKeys) {
#if 1
                        if (!isEqual(dict[key], NSLocalizedStringFromTableInBundle(key, name, bundle, nil))) {
                            NSLog(@"%@ %@ %@: %@=[%@] [%@]", fw, language, name, key, NSLocalizedStringFromTableInBundle(key, name, bundle, nil), dict[key]);
                        } else {
                            NSLog(@"%@ %@ %@: %@=%@", fw, language, name, key, dict[key]);
                        }
#endif
                        count++;
                    }
                }
            }
        }
        NSLog(@"%@ count=%d", fw, count);
    }
}

@end
