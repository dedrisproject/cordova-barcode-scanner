#import "BarcodeScannerPlugin.h"
#import "BarcodeScannerViewController.h"

@implementation BarcodeScannerPlugin

- (void)scan:(CDVInvokedUrlCommand *)command {
    NSDictionary *options = nil;
    if (command.arguments.count > 0 && [command.arguments[0] isKindOfClass:[NSDictionary class]]) {
        options = command.arguments[0];
    }

    NSString *prompt = [options[@"prompt"] isKindOfClass:[NSString class]]
        ? options[@"prompt"]
        : @"Point your camera at a barcode";

    NSArray *formats = [options[@"formats"] isKindOfClass:[NSArray class]]
        ? options[@"formats"]
        : nil;

    BOOL beep = options[@"beep"] != nil ? [options[@"beep"] boolValue] : YES;

    BarcodeScannerViewController *scanner = [[BarcodeScannerViewController alloc] init];
    scanner.promptText = prompt;
    scanner.requestedFormats = formats;
    scanner.beepEnabled = beep;
    scanner.modalPresentationStyle = UIModalPresentationFullScreen;

    __weak BarcodeScannerPlugin *weakSelf = self;
    NSString *callbackId = command.callbackId;

    scanner.completion = ^(NSString *text, NSString *format, BOOL cancelled, NSString *error) {
        __strong BarcodeScannerPlugin *strongSelf = weakSelf;
        if (strongSelf == nil) {
            return;
        }

        CDVPluginResult *result;
        if (error != nil) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error];
        } else {
            NSDictionary *payload = @{
                @"text": text != nil ? text : @"",
                @"format": format != nil ? format : @"",
                @"cancelled": @(cancelled)
            };
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:payload];
        }
        [strongSelf.commandDelegate sendPluginResult:result callbackId:callbackId];
    };

    dispatch_async(dispatch_get_main_queue(), ^{
        [self.viewController presentViewController:scanner animated:YES completion:nil];
    });
}

@end
