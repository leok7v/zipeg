#import "ZGBasePreferencesViewController.h"

@implementation ZGKeyButton
- (BOOL) acceptsFirstResponder { return true; }
- (BOOL) canBecomeKeyView { return true; }
- (BOOL) needsPanelToBecomeKey { return false; }
- (void) mouseDown: (NSEvent*) e { [super mouseDown: e]; [self.window makeFirstResponder: self]; }
@end

@implementation ZGKeyPopUpButton
- (BOOL) acceptsFirstResponder { return true; }
- (BOOL) canBecomeKeyView { return true; }
- (BOOL) needsPanelToBecomeKey { return false; }
- (void) mouseDown: (NSEvent*) e { [super mouseDown: e]; [self.window makeFirstResponder: self]; }
@end

@implementation ZGKeyComboBox
- (BOOL) acceptsFirstResponder { return true; }
- (BOOL) canBecomeKeyView { return true; }
- (BOOL) needsPanelToBecomeKey { return false; }
- (void) mouseDown: (NSEvent*) e { [super mouseDown: e]; [self.window makeFirstResponder: self]; }
@end

@implementation ZGKeyMatrix
- (BOOL) acceptsFirstResponder { return true; }
- (BOOL) canBecomeKeyView { return true; }
- (BOOL) needsPanelToBecomeKey { return false; }
- (void) mouseDown: (NSEvent*) e { [super mouseDown: e]; [self.window makeFirstResponder: self]; }
@end

@implementation ZGBasePreferencesViewController

static NSFont* _font;
static NSString* _enameUTF8;

+ (void) initialize {
    _font = [NSFont systemFontOfSize: NSFont.systemFontSize - 1];
    CFStringRef cfenameUTF8 =  CFStringGetNameOfEncoding(kCFStringEncodingUTF8);
    _enameUTF8 = (__bridge NSString*)cfenameUTF8;
    CFRelease(cfenameUTF8);
}

+ (NSDictionary*) defaultPreferences {
    return @{
             @"com.zipeg.preferences.showWelcome": @true,
             @"com.zipeg.preferences.showInFinder": @true,
             @"com.zipeg.preferences.closeAfterUnpack": @false,
             @"com.zipeg.preferences.openNested": @true,
             @"com.zipeg.preferences.allAlerts": @true,
             @"com.zipeg.preferences.encoding.detect": @true,
             @"com.zipeg.preferences.playSounds": @true,
             @"com.zipeg.preferences.unpackSelection": @0,
             @"com.zipeg.preferences.showPasswords": @false,
             @"com.zipeg.preferences.sortCaseSensitive": @false,
             @"com.zipeg.preferences.sortFoldersFirst": @true,
             @"com.zipeg.preferences.useTrashBin": @true,
             @"com.zipeg.preferences.encoding": _enameUTF8,
             @"com.zipeg.preferences.simplifiedToolbar": @false,
             @"com.zipeg.preferences.outline.view.style": @0,
             @"com.zipeg.preferences.ask.to.unpack": @true
             };
}

- (NSString*) ident {
    @throw @"abstract";
}

- (NSImage*) image {
    @throw @"abstract";
}

- (NSString*) label {
    @throw @"abstract";
}

+ (NSFont*) font {
    return _font;
}

static void addChild(NSView* parent, NSView* child) {
    NSView* last = parent.subviews != null ? parent.subviews[parent.subviews.count - 1] : null;
    [parent addSubview: child];
    if (child.canBecomeKeyView && last != null) {
        last.nextKeyView = child;
    }
}

static NSTextView* createLabel(NSView* parent, CGFloat y, NSString* labelText) {
    NSTextView* label = NSTextView.new;
    label.autoresizingMask = NSViewMinYMargin;
    label.font = _font;
    CGFloat h = _font.boundingRectForFont.size.height;
    label.string = labelText;
    label.editable = false;
    label.selectable = false;
    label.drawsBackground = false;
    label.alignment = NSRightTextAlignment;
    label.frame = NSMakeRect(margin, y, middle - margin, h);
    [parent addSubview: label];
    return label;
}

static CGFloat extraText(NSView* parent, CGFloat y, CGFloat h, NSString* extra) {
    NSTextView* tv = NSTextView.new;
    tv.autoresizingMask = NSViewMinYMargin;
    tv.string = extra;
    tv.editable = false;
    tv.selectable = false;
    tv.drawsBackground = false;
    tv.alignment = NSNaturalTextAlignment;
    tv.drawsBackground = false;
    tv.verticallyResizable = true;
    tv.horizontallyResizable = false;
    int w = parent.frame.size.width - middle - margin * 2;
    tv.maxSize = NSMakeSize(w, parent.frame.size.height);
    tv.minSize = NSMakeSize(w, h);
    tv.constrainedFrameSize = NSMakeSize(w, parent.frame.size.height);
    [parent addSubview: tv];
    tv.frame = NSMakeRect(middle + 20, y - tv.frame.size.height - h * 0.125, w, tv.frame.size.height);
    y = tv.frame.origin.y - h * 1.5;
    return y;
}

CGFloat labelNoteAndExtra(NSView* parent, CGFloat y, NSString* label, NSString* note, NSString* extra) {
    NSText* lb = createLabel(parent, y, label);
    CGFloat h = lb.frame.size.height;
    NSText* nt = createLabel(parent, y, note);
    nt.alignment = NSLeftTextAlignment;
    nt.font = _font;
    nt.frame = NSMakeRect(middle + 20, y - 1, parent.frame.size.width - middle - margin, h);
    addChild(parent, nt);
    y -= h * 2.0;
    if (extra != null && extra.length > 0) {
        y = extraText(parent, lb.frame.origin.y, h, extra);
    }
    return y;
}

NSButton* createCheckBox(NSView* parent, CGFloat* Y, NSString* label, NSString* checkbox, NSString* extra, NSString* prefs) {
    CGFloat y = *Y;
    NSText* lb = createLabel(parent, y, label);
    CGFloat h = lb.frame.size.height;
    NSButton* cbx = ZGKeyButton.new;
    cbx.autoresizingMask = NSViewMinYMargin;
    cbx.font = _font;
    cbx.buttonType = NSSwitchButton;
    cbx.title = checkbox;
    NSButtonCell* c = cbx.cell;
    c.lineBreakMode = NSLineBreakByClipping;
    cbx.frame = NSMakeRect(middle, y - 1, parent.frame.size.width - middle - margin, h);
    addChild(parent, cbx);
    [cbx bind: @"value" toObject: NSUserDefaultsController.sharedUserDefaultsController
                     withKeyPath: [NSString stringWithFormat: @"values.%@", prefs]
                         options: @{@"NSContinuouslyUpdatesValue": @true}];
    y -= h * 1.5;
    if (extra != null && extra.length > 0) {
        y = extraText(parent, cbx.frame.origin.y, h, extra);
    }
    *Y = y;
    return cbx;
}

CGFloat checkBox(NSView* parent, CGFloat y, NSString* label, NSString* checkbox, NSString* extra, NSString* prefs) {
    createCheckBox(parent, &y, label, checkbox, extra, prefs);
    return y;
}

CGFloat button(NSView* parent, CGFloat y, NSString* label, NSString* title, NSString* after, NSString* extra, id target, SEL sel) {
    NSText* lb = createLabel(parent, y, label);
    CGFloat h = lb.frame.size.height;
    NSButton* btn = NSButton.new;
    btn.autoresizingMask = NSViewMinYMargin;
    btn.font = _font;
    btn.buttonType = NSMomentaryPushInButton;
    btn.title = title;
    btn.frame = NSMakeRect(middle + 24, y - 6, width, h);
    NSButtonCell* c = btn.cell;
    c.wraps = false;
    c.scrollable = false;
    c.bezelStyle = NSTexturedRoundedBezelStyle;
    addChild(parent, btn);
    [btn sizeToFit];
    btn.target = target;
    btn.action = sel;
    if (after != null && after.length > 0) {
        NSTextView* trailing = createLabel(parent, y, after);
        trailing.alignment = NSLeftTextAlignment;
        CGFloat x = btn.frame.origin.x + btn.frame.size.width;
        CGFloat w = width - margin - x;
        trailing.frame = NSMakeRect(x, y, w, h);
    }
    y -= btn.frame.size.height * 1.5;
    if (extra != null && extra.length > 0) {
        y = extraText(parent, btn.frame.origin.y, h, extra);
    }
    return y;
}

CGFloat radioButtons(NSView* parent, CGFloat y, NSString* label, NSArray* texts, NSString* prefs) {
    NSText* lb = createLabel(parent, y, label);
    NSButtonCell *cell = NSButtonCell.new;
    cell.buttonType = NSRadioButton;
    cell.font = _font;
    CGFloat max = 0;
    for (int i = 0; i < texts.count; i++) {
        cell.title = texts[i];
        max = MAX(max, cell.cellSize.width);
    }
    CGFloat lh = lb.frame.size.height;
    CGFloat h = MAX(cell.cellSize.height + 2, lb.frame.size.height);
    int w = parent.frame.size.width - middle - margin * 2;
    NSRect r = NSMakeRect(middle, y - h * (texts.count - 1) - 2, w, h * texts.count);
    NSMatrix* mx = [ZGKeyMatrix.alloc initWithFrame: r
                                             mode: NSRadioModeMatrix
                                        prototype: cell
                                     numberOfRows: texts.count
                                  numberOfColumns: 1];
    mx.cellSize = NSMakeSize(max, cell.cellSize.height);
    mx.autoresizingMask = NSViewMinYMargin;
    addChild(parent, mx);
    for (int i = 0; i < texts.count; i++) {
        NSCell* c = mx.cells[i];
        c.title = texts[i];
    }
    [mx bind: @"selectedIndex" toObject: NSUserDefaultsController.sharedUserDefaultsController
                    withKeyPath: [NSString stringWithFormat: @"values.%@", prefs]
                        options: @{@"NSContinuouslyUpdatesValue": @true}];
    return y - h * (int)texts.count - lh * 0.25;
}

CGFloat comboBox(NSView* parent, CGFloat y, NSString* label, NSArray* texts, CGFloat width, NSString* prefs) {
    NSText* lb = createLabel(parent, y, label);
    NSComboBox* cbx = ZGKeyComboBox.new;
    cbx.autoresizingMask = NSViewMinYMargin;
    NSRect r = NSMakeRect(middle, y - 4, width, cbx.itemHeight + 4);
    cbx.frame = r;
    cbx.completes = true;
    NSComboBoxCell* c = cbx.cell;
    c.editable = false;
    addChild(parent, cbx);
    for (int i = 0; i < texts.count; i++) {
        [cbx addItemWithObjectValue: texts[i]];
    }
    [cbx bind: @"value" toObject: NSUserDefaultsController.sharedUserDefaultsController
                     withKeyPath: [NSString stringWithFormat: @"values.%@", prefs]
                         options: @{@"NSContinuouslyUpdatesValue": @true}];
    return cbx.frame.origin.y - lb.frame.size.height * 1.75;
}

static void insertMenuItem(NSMenu* m, NSString* title, NSNumber* n) {
    NSMenuItem *it = [NSMenuItem.alloc initWithTitle: NSLocalizedString(title, @"") action: null keyEquivalent:@""];
    if (n != null) {
        it.tag = n.intValue;
    }
    [m insertItem: it atIndex: m.itemArray.count];
}

CGFloat popUpButton(NSView* parent, CGFloat y, NSString* label, NSArray* texts, NSArray* tags, NSString* prefs) {
    NSText* lb = createLabel(parent, y, label);
    int w = parent.frame.size.width - middle - margin * 2;
    NSPopUpButton* btn = ZGKeyPopUpButton.new;
    btn.autoresizingMask = NSViewMinYMargin;
    btn.title = texts[0];
    NSButtonCell* bc = btn.cell;
    NSRect r = NSMakeRect(middle, y - 4, w / 2, bc.cellSize.height);
    btn.frame = r;
    bc.bezelStyle = NSTexturedRoundedBezelStyle;
    bc.highlightsBy = NSPushInCellMask;
    bc.controlTint = NSBlueControlTint;
    bc.font = _font;
    btn.menu = [NSMenu new];
    addChild(parent, btn);
    for (int i = 0; i < texts.count; i++) {
        NSNumber* tag = tags != null && i < tags.count ? tags[i] : null;
        insertMenuItem(btn.menu, texts[i], tag);
    }
    return btn.frame.origin.y - lb.frame.size.height * 1.75;
}

@end
