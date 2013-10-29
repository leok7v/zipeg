#import "ZGTask.h"

@interface ZGTask() {
    NSTask *task;
    NSString *path;
    NSArray *args;
    NSData *stdinData;
    NSMutableString *output;
    NSMutableString *error;
    BOOL terminated;
    NSFileHandle *outFile;
    NSFileHandle *errFile;
    NSFileHandle *stdinHandle;
}
@end

static int empty_data_count;
static int task_exit_code;

@implementation ZGTask

- (id) initWithPath: (NSString*) pPath {
    self = [super init];
    if (self != null) {
        task = [[NSTask alloc] init];
        args = [NSArray array];
        path = pPath;
        stdinData = null;  // ADDED
        error = null;
        output = null;
        terminated = NO;
    }
    return self;

}

-(void) dealloc {
}


// ADDED
// For "availableDataOrError:" see:
// - "NSTasks, NSPipes, and deadlocks when reading...",
//    http://dev.notoptimal.net/2007/04/nstasks-nspipes-and-deadlocks-when.html
// - "NSTask stealth bug in readDataOfLength!! :(",
//    http://www.cocoabuilder.com/archive/cocoa/173348-nstask-stealth-bug-in-readdataoflength.html#173647

- (NSData*) availableDataOrError: (NSFileHandle*) file {
    for (;;) {
        @try {
            return [file availableData];
        } @catch (NSException *e) {
            if ([e.name isEqualToString: NSFileHandleOperationException]) {
                if ([e.reason isEqualToString: @"*** -[NSConcreteFileHandle availableData]: Interrupted system call"]) {
                    continue;
                }
                return null;
            }
            @throw;
        }
    }  // for
}

- (void) setArgs: (NSArray*) a {
    args = a;
}

- (void) setInput: (NSData*) in {
    stdinData = in;
}

- (void) setError: (NSMutableString*) e {
    error = e;
}

- (void) setOutput: (NSMutableString*) o {
    output = o;
}

-(void) appendDataFrom: (NSFileHandle*) fileHandle to: (NSMutableString*) string {
    NSData *data = [self availableDataOrError: fileHandle];
    //NSData *data = [fileHandle availableData];
    if ([data length]) {
        NSString *s = [[NSString alloc] initWithBytes:[data bytes] length:[data length] encoding: NSUTF8StringEncoding];
        //NSString *s = [[NSString alloc] initWithBytes:[data bytes] length:[data length] encoding: NSASCIIStringEncoding];
        [output appendString:s];
        //NSLog(@"| %@", s);
        //[fileHandle waitForDataInBackgroundAndNotify];
    }else{
        empty_data_count += 1;
        if (empty_data_count > 10)
        {
            //[task interrupt];   // failed to abort infinite NSRunLoop
            //[task terminate];   // same
            [NSNotificationCenter.defaultCenter removeObserver:self];  // only way to abort infinite NSRunLoop ???
        }
    }
    [fileHandle waitForDataInBackgroundAndNotify];
}

-(void) outData: (NSNotification *) n {
    NSFileHandle *fileHandle = (NSFileHandle*)n.object;
    [self appendDataFrom: fileHandle to: output];
    [fileHandle waitForDataInBackgroundAndNotify];
}

-(void) errData: (NSNotification *) n {
    NSFileHandle *fileHandle = (NSFileHandle*)n.object;
    [self appendDataFrom:fileHandle to:output];
    [fileHandle waitForDataInBackgroundAndNotify];
}

- (void) terminated: (NSNotification *) n {
    trace(@"Task terminated");
    [NSNotificationCenter.defaultCenter removeObserver: self];
    terminated = true;
}

- (int) execute {
    // this must be called on main thread otherwise waitForDataInBackgroundAndNotify won't work
    assert([NSThread isMainThread]);
    empty_data_count = 0;
    [task setLaunchPath:path];
    [task setArguments:args];
    NSPipe *outPipe = [NSPipe pipe];
    NSPipe *errPipe = [NSPipe pipe];
    [task setStandardOutput: outPipe];
    [task setStandardError: errPipe];
    [task setCurrentDirectoryPath: @"."];
    //NSFileHandle *outFile = [outPipe fileHandleForReading];   // TROUBLEMAKER
    //NSFileHandle *errFile = [errPipe fileHandleForReading];
    outFile = [outPipe fileHandleForReading];
    errFile = [errPipe fileHandleForReading];

    // create inPipe after outPipe & errPipe
    NSPipe *inPipe = [NSPipe pipe];
    stdinHandle = [inPipe fileHandleForWriting];

    [task setStandardInput:inPipe];

    [NSNotificationCenter.defaultCenter addObserver:self
                                             selector:@selector(terminated:)
                                                 name:NSTaskDidTerminateNotification
                                               object:task];

    [NSNotificationCenter.defaultCenter addObserver:self
                                             selector:@selector(outData:)
                                                 name:NSFileHandleDataAvailableNotification
                                               object:outFile];

    [NSNotificationCenter.defaultCenter addObserver:self
                                             selector:@selector(errData:)
                                                 name:NSFileHandleDataAvailableNotification
                                               object:errFile];


    [outFile waitForDataInBackgroundAndNotify];
    [errFile waitForDataInBackgroundAndNotify];

    [task launch];

    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_LOW, 0), ^{
        [stdinHandle writeData: stdinData];
        [stdinHandle closeFile];
    });
    // TODO: this is bad and unnecessary. Need to return here and have notification on completion
    while (!terminated) {
        //if (![[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate dateWithTimeIntervalSinceNow:100000]])
        if (![[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode beforeDate:[NSDate distantFuture]])
        {
            break;
        }
    }
    [self appendDataFrom: outFile to: output];
    [self appendDataFrom: errFile to: error];
    int result = [task terminationStatus];
    task_exit_code = result;
    return result;
    
}

static int test(int argc, const char *argv[]) {
    // Test 1: write data to stdin of NSTask for subsequent cat(1) or tail(1)
    NSString *testfile = @"testfile.txt";
    NSData *data = [NSData dataWithContentsOfFile: testfile];
    ZGTask *newTask = [ZGTask.alloc initWithPath: @"/bin/cat"];
    //[newTask setArgs: [NSArray arrayWithObjects: @"-n", nil ]];
    [newTask setArgs: [NSArray arrayWithObjects: @"-u", @"-n", nil ]];
    //ZGTask *newTask = [[[FRCommand alloc] initWithPath: @"/usr/bin/tail"] autorelease];
    //[newTask setArgs: [NSArray arrayWithObjects: @"-n", @"10", nil ]];
    [newTask setInput: data];

    // Test 2: do not write to stdin
    /*
     ZGTask *newTask = [ZGTask.alloc initWithPath: @"/bin/ls"];
     [newTask setArgs: [NSArray arrayWithObjects: @"-l", nil ]];
     */
    NSMutableString *stdoutString = [NSMutableString string];
    NSMutableString *stderrString = [NSMutableString string];

    [newTask setOutput: stdoutString];
    [newTask setError: stderrString];

    [newTask execute];

    NSLog(@"stdoutString:\n%@", stdoutString);
    NSLog(@"stderrString:\n%@", stderrString);

    printf("\nempty_data_count:  %i\n", empty_data_count);
    printf("\ntask_exit_code:  %i\n\n", task_exit_code);
    return 0;
}

@end

