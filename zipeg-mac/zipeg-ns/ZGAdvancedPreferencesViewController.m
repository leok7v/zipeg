#import "ZGAdvancedPreferencesViewController.h"

@interface ZGAdvancedPreferencesViewController() {
}
@end

@implementation ZGAdvancedPreferencesViewController

static NSDictionary* _n2e; // NSString->NSNumber (CFStringEncoding)
static NSDictionary* _e2n; // reverse, use CFStringConvertEncodingToNSStringEncoding for NSEncoding

+ (void) initialize {
    [ZGAdvancedPreferencesViewController initEncodings];
}

- (id) init {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        NSView* v = NSView.new;
        v.autoresizesSubviews = true;
        v.frameSize = NSMakeSize(width, 120);
        NSFont* font = ZGBasePreferencesViewController.font;
        CGFloat y = v.frame.size.height - font.boundingRectForFont.size.height;
        NSArray* keys = [_n2e.allKeys sortedArrayUsingSelector:@selector(localizedCaseInsensitiveCompare:)];
        NSMutableArray* values = [NSMutableArray arrayWithCapacity: keys.count];
        for (int i = 0; i < keys.count; i++) {
            values[i] = _n2e[keys[i]];
        }
        y = checkBox(v, y, @"Detect encoding:", @" automatically",
                     @"Zipeg will attempt to detect national alphabet for item names encoding. "
                     @"If detection fails - Zipeg will use default encoding specified below. "
                     @"Keep it Unicode (UTF-8) if in doubt.",
                     @"com.zipeg.preferences.encoding.detect");
        int w = width - middle - margin * 2;
        y = comboBox(v, y, @"Deafault Encoding:", keys, w * 3 / 4, @"com.zipeg.preferences.encoding");
        self.view = v;
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
}

- (NSString*) ident {
    return @"AdvancedPreferences";
}

- (NSImage *) image {
    return [NSImage imageNamed: NSImageNameAdvanced];
}

- (NSString *) label {
    return NSLocalizedString(@"Advanced", @"Zipeg Advanced Preferences");
}

+ (void) initEncodings {
    // at the time of writing 105 encoding are reported by OSX 10.8
    NSMutableDictionary* n2e = [NSMutableDictionary dictionaryWithCapacity: 150];
    NSMutableDictionary* e2n = [NSMutableDictionary dictionaryWithCapacity: 150];
    const CFStringEncoding* encs = CFStringGetListOfAvailableEncodings();
    while (*encs != kCFStringEncodingInvalidId) {
        CFStringEncoding enc = *encs;
        CFStringRef cfename =  CFStringGetNameOfEncoding(enc);
        NSString* ename = (__bridge NSString*)cfename;
        CFRelease(cfename);
        NSNumber* e = @(enc);
        //      trace("[%@]=%@ [ns=0x%08lx]", ename, e, CFStringConvertEncodingToNSStringEncoding(enc));
        n2e[ename] = e;
        e2n[e] = ename;
        encs++;
    }
    _n2e = [NSDictionary dictionaryWithDictionary: n2e];
    _e2n = [NSDictionary dictionaryWithDictionary: e2n];
}


@end
