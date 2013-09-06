#import "ZGGeneralPreferencesViewController.h"

@implementation ZGGeneralPreferencesViewController


enum {
    width = 540,
    height = 400,
    middle = 160,
    margin = 15
};

static NSFont* font;

+ (void) initialize {
    font = [NSFont systemFontOfSize: NSFont.systemFontSize - 1];
}


- (id) init {
    self = [super init];
    if (self != null) {
        alloc_count(self);
        NSView* v = NSView.new;
        v.autoresizesSubviews = true;
        v.frameSize = NSMakeSize(width, height);
        CGFloat y = v.frame.size.height - font.boundingRectForFont.size.height;
        y = createCheckBox(v, y, @"Opening:", @" Show Welcome window when Zipeg opens", @"");
        y = createCheckBox(v, y, @"After Unpack:", @" Show unpacked items in Finder", null);
        y = createCheckBox(v, y, @"After Unpack:", @" Close archive window", null);
        y = createCheckBox(v, y, @"Nested Archives:", @" Automatically open",
                           @"If archive contains exactly one item and it happens to be ‘nested’ "
                           @"archive unpack it to temporary folder and re-open in the same window.");
        y = createCheckBox(v, y, @"Alerts:", @" Show All",
                           @"If this box is unchecked you may have hidden some Zipeg alerts. "
                           @"Checkmark this option to restore all alerts.");
        y = radioButtons(v, y, @"Radio:", @[@" foo", @" bar", @" snafu"]);
        y = comboBox(v, y, @"Combo:", @[@" fṧṤƿo", @" bar", @" snafu"]);
        y = createCheckBox(v, y, @"WWWW:", @" WWWW-----------------------", null);
        self.view = v;
        dumpViews(v);
    }
    return self;
}

static NSText* createLabel(NSView* parent, CGFloat y, NSString* labelText) {
    NSText* label = NSTextView.new;
    label.font = font;
    CGFloat h = font.boundingRectForFont.size.height;
    label.string = labelText;
    label.editable = false;
    label.selectable = false;
    label.drawsBackground = false;
    label.alignment = NSRightTextAlignment;
    label.frame = NSMakeRect(margin, y, middle - margin, h);
    [parent addSubview: label];
    return label;
}

static int createCheckBox(NSView* parent, CGFloat y, NSString* label, NSString* checkbox, NSString* extra) {
    NSText* lb = createLabel(parent, y, label);
    CGFloat h = lb.frame.size.height;
    NSButton* cbx = NSButton.new;
    cbx.font = font;
    cbx.buttonType = NSSwitchButton;
    cbx.title = checkbox;
    NSButtonCell* c = cbx.cell;
    c.lineBreakMode = NSLineBreakByClipping;
    cbx.frame = NSMakeRect(middle, y, parent.frame.size.width - middle - margin, h);
    [parent addSubview: cbx];
    y -= h * 1.5;
    if (extra != null && extra.length > 0) {
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
        tv.frame = NSMakeRect(middle + 20, cbx.frame.origin.y - tv.frame.size.height, w, tv.frame.size.height);
        y = tv.frame.origin.y - h * 1.5;
    }
    return y;
}

static int radioButtons(NSView* parent, CGFloat y, NSString* label, NSArray* texts) {
    NSText* lb = createLabel(parent, y, label);
    NSButtonCell *cell = NSButtonCell.new;
    cell.buttonType = NSRadioButton;
    cell.title = @"MeasureMe";
    cell.font = font;
    CGFloat lh = lb.frame.size.height;
    CGFloat h = MAX(cell.cellSize.height + 2, lb.frame.size.height);
    int w = parent.frame.size.width - middle - margin * 2;
    NSRect r = NSMakeRect(middle, y - h * (texts.count - 1) - 2, w, h * texts.count);
    NSMatrix* mx = [NSMatrix.alloc initWithFrame: r
                                            mode: NSRadioModeMatrix
                                       prototype: cell
                                    numberOfRows: texts.count
                                 numberOfColumns: 1];
    [parent addSubview: mx];
    for (int i = 0; i < texts.count; i++) {
        NSCell* c = mx.cells[i];
        c.title = texts[i];
    }
    trace("c.height=%f y=%f h=%f %f %f", cell.cellSize.height, y, h, lb.frame.origin.y + lb.frame.size.height, mx.frame.origin.y + mx.frame.size.height);
    return y - h * (int)texts.count - lh * 0.25;
}

static int comboBox(NSView* parent, CGFloat y, NSString* label, NSArray* texts) {
    NSText* lb = createLabel(parent, y, label);
    int w = parent.frame.size.width - middle - margin * 2;
    NSComboBox* cbx = NSComboBox.new;
    NSRect r = NSMakeRect(middle, y - 4, w / 2, cbx.itemHeight + 4);
    cbx.frame = r;
    NSComboBoxCell* c = cbx.cell;
    c.editable = false;
    [parent addSubview: cbx];
    for (int i = 0; i < texts.count; i++) {
        [cbx addItemWithObjectValue: texts[i]];
    }
    [cbx selectItemAtIndex: 0]; // TODO: restore selection from model
    CGFloat h = lb.frame.size.height;
    return y - h * 1.5;
}


- (void) dealloc {
    dealloc_count(self);
}

- (NSString*) ident {
    return @"GeneralPreferences";
}

- (NSImage*) image {
    return [NSImage imageNamed: NSImageNamePreferencesGeneral];
}

- (NSString*) label {
    return NSLocalizedString(@"General", @"Zipeg General Preferences");
}

@end
