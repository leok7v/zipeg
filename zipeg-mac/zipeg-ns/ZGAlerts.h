@class ZGDocument;

@interface ZGAlerts : NSWindow

@property NSString* topText;
@property NSString* bottomText;

- (id) initWithDocument: (ZGDocument*) d;
- (void) begin;
- (BOOL) isOpen;
- (void) end;
- (void) alert: (NSAlert*) a done: (void(^)(NSInteger rc)) block;
- (void) progress: (int64_t) pos of: (int64_t) total;
@end


/*
 
 Alerting business is rather complicated.
 
 I do not want to present user with multiple stacked on top of each other alerts.
 Though the events that need alerting do come from at least 2 sources for each individual
 archive window.
 1. The UI itself. E.g. on the long operation the user may press [STOP] button to cancel the
    operation and be present with confirmation messaging.
 2. The background thread(s). E.g. the background open or extract operation needs a password 
    for the archive or encountered an error. It needs to show the alert to the user who may
    be in the middle of the scenario 1 above
 3. The background operation may succesfully complete while user is still contemplating 1 above
    and I need to dismiss the STOP alert message all together because now it does not make sense.
 4. The background thread may need a definite answer from the user e.g. for overwriting a file
    Keep Both | Yes | No | Cancel [x] Apply to All
    it cannot proceed before the question is answered one way or another.
 5. User may want to close the application completely (Log Out? Reboot? Updates need to be installed?)
    while the user is in scenario 1 or 2 on top of 1 or 4 on top of 1.
    User needs to be asked if s/he is OK with canceling all togethr outstanding operations 
    that are still in progress (some may end while the question itself if presented) and 
    if this is the case scenarios 2 and 4 need to receive "cancel" return code from alerting 
    system without any further user intervention.
---
 Some simplification of the flow:
 I've added ZGOperation that has requestCancel state (can be set to true or false).
 The stop button in the ZGProgress control requests cancelation of current operation from
 ZGDocument. The background thread detects the request asks user's confirmation and blocks
 till it recieves the answer. If application termination request comes - different question
 is posed to the user asking for cancelation of all operations in progress and if user
 agrees they are cancelled without further one-by-one per-operation confirmations.

 TODO:
    Suppressed Alerts: if I even need them should be implemented through automatically localized
    button NSAlert.showsSuppressionButton and with clear 
    NSAlert.informativeText stating - "(You can switch this alert back on in the Preferences)"
    Preferences: [Show Suppressed Alerts] action | alternative the small icon on status bar with popup menu

 */