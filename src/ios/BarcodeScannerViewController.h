#import <UIKit/UIKit.h>

/**
 * Completion block for a scan.
 *
 * Exactly one outcome is reported:
 *   - success:   text + format are set, cancelled == NO, error == nil
 *   - cancelled: cancelled == YES, text/format empty, error == nil
 *   - error:     error holds a message; other values are nil/NO
 */
typedef void (^BarcodeScanCompletion)(NSString *text, NSString *format, BOOL cancelled, NSString *error);

@interface BarcodeScannerViewController : UIViewController

@property (nonatomic, copy) NSString *promptText;
@property (nonatomic, strong) NSArray<NSString *> *requestedFormats;
@property (nonatomic, assign) BOOL beepEnabled;
@property (nonatomic, copy) BarcodeScanCompletion completion;

@end
