// https://github.com/jerrykrinock/CategoriesObjC/blob/master/NSDate%2BMicrosoft1601Epoch.h
// https://github.com/jerrykrinock/CategoriesObjC/blob/master/NSDate%2BMicrosoft1601Epoch.m
// https://github.com/jerrykrinock/CategoriesObjC/blob/master/License.txt (Apache)

extern NSTimeInterval const constIntMSWindowsTicksPerSecond;

@interface NSDate (Microsoft1601Epoch)

+ (NSDate*)dateWithTicksSince1601:(long long)ticks; // "tick" is 1/10 microsecond == 100 nanoseconds
+ (NSDate*)dateWithMicrosecondsSince1601:(long long)microseconds;

- (long long)microsecondsSince1601;

@end
