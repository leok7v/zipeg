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
//- (void) mouseDown: (NSEvent*) e { [super mouseDown: e]; [self.window makeFirstResponder: self]; }
@end

@implementation ZGKeyMatrix
- (BOOL) acceptsFirstResponder { return true; }
- (BOOL) canBecomeKeyView { return true; }
- (BOOL) needsPanelToBecomeKey { return false; }
- (void) mouseDown: (NSEvent*) e { [super mouseDown: e]; [self.window makeFirstResponder: self]; }
@end


@interface ZGComboBoxDataSource : NSObject<NSComboBoxCellDataSource> {
    NSArray* _content;
}
- (id) initWithTexts: (NSArray*) texts;
@end

@implementation ZGComboBoxDataSource

- (id) initWithTexts: (NSArray*) texts {
    self = [super init];
    if (self != null) {
        _content = texts;
    }
    return self;
}


- (NSInteger) numberOfItemsInComboBoxCell: (NSComboBoxCell*) c {
    return _content.count;
}

- (id) comboBoxCell: (NSComboBoxCell*) c objectValueForItemAtIndex: (NSInteger) ix {
    return _content[ix];
}

- (NSUInteger)comboBoxCell:(NSComboBoxCell*) c indexOfItemWithStringValue:(NSString *)string {
    return [_content indexOfObject: string];
}

- (NSString *)comboBoxCell:(NSComboBoxCell*) c completedString: (NSString*) partialString {
    trace("completedString %@", partialString);
    /*
     for (NSString* dataString in dataSourceArray) {
     if ([[dataString commonPrefixWithString:partialString options:NSCaseInsensitiveSearch] length] == [commonPrefixWithString:partialString length]) {
     return testItem;
     }
     }
     */
    return @"";
}

@end

@implementation ZGBasePreferencesViewController

static NSFont* _font;

+ (void) initialize {
    _font = [NSFont systemFontOfSize: NSFont.systemFontSize - 1];
}

- (id) init {
    self = [super init];
    if (self != null) {
        alloc_count(self);
    }
    return self;
}

- (void) dealloc {
    dealloc_count(self);
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

void addChild(NSView* parent, NSView* child) {
    NSView* last = parent.subviews != null ? parent.subviews[parent.subviews.count - 1] : null;
    [parent addSubview: child];
    if (child.canBecomeKeyView && last != null) {
        last.nextKeyView = child;
    }
}

NSTextView* createLabel(NSView* parent, CGFloat y, NSString* labelText) {
    NSTextView* label = NSTextView.new;
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
    // TODO: generalize with button
    NSTextView* tv = NSTextView.new;
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

CGFloat checkBox(NSView* parent, CGFloat y, NSString* label, NSString* checkbox, NSString* extra, NSString* prefs) {
    NSText* lb = createLabel(parent, y, label);
    CGFloat h = lb.frame.size.height;
    NSButton* cbx = ZGKeyButton.new;
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
    return y;
}

CGFloat button(NSView* parent, CGFloat y, NSString* label, NSString* checkbox, NSString* extra) {
    NSText* lb = createLabel(parent, y, label);
    CGFloat h = lb.frame.size.height;
    NSButton* btn = NSButton.new;
    btn.font = _font;
    btn.buttonType = NSMomentaryPushInButton;
    btn.title = checkbox;
    btn.frame = NSMakeRect(middle, y - 6, width, h);
    NSButtonCell* c = btn.cell;
    c.wraps = false;
    c.scrollable = false;
    c.bezelStyle = NSTexturedRoundedBezelStyle;
    addChild(parent, btn);
    [btn sizeToFit];
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
    cell.title = @"MeasureMe";
    cell.font = _font;
    CGFloat lh = lb.frame.size.height;
    CGFloat h = MAX(cell.cellSize.height + 2, lb.frame.size.height);
    int w = parent.frame.size.width - middle - margin * 2;
    NSRect r = NSMakeRect(middle, y - h * (texts.count - 1) - 2, w, h * texts.count);
    NSMatrix* mx = [ZGKeyMatrix.alloc initWithFrame: r
                                             mode: NSRadioModeMatrix
                                        prototype: cell
                                     numberOfRows: texts.count
                                  numberOfColumns: 1];
    addChild(parent, mx);
    for (int i = 0; i < texts.count; i++) {
        NSCell* c = mx.cells[i];
        c.title = texts[i];
    }
    // trace("c.height=%f y=%f h=%f %f %f", cell.cellSize.height, y, h, lb.frame.origin.y + lb.frame.size.height, mx.frame.origin.y + mx.frame.size.height);
    return y - h * (int)texts.count - lh * 0.25;
}

CGFloat comboBox(NSView* parent, CGFloat y, NSString* label, NSArray* texts, CGFloat width, NSString* prefs) {
    NSText* lb = createLabel(parent, y, label);
    NSComboBox* cbx = ZGKeyComboBox.new;
    NSRect r = NSMakeRect(middle, y - 4, width, cbx.itemHeight + 4);
    cbx.frame = r;
    cbx.completes = true;
    NSComboBoxCell* c = cbx.cell;
//    c.allowsTypeSelect = true;
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
