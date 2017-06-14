/**
 * @author Denis Apparicio
 *
 */


#import <Cordova/CDV.h>

@interface Mobilevision : CDVPlugin

- (void)getBrightness:(CDVInvokedUrlCommand*)command;
- (void)setBrightness:(CDVInvokedUrlCommand*)command;
- (void)setKeepScreenOn:(CDVInvokedUrlCommand*)command;

@end
