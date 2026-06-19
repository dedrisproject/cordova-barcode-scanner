#import <Cordova/CDVPlugin.h>

@interface BarcodeScannerPlugin : CDVPlugin

- (void)scan:(CDVInvokedUrlCommand *)command;

@end
