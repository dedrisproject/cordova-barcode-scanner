#import "BarcodeScannerViewController.h"
#import <AVFoundation/AVFoundation.h>
#import <AudioToolbox/AudioToolbox.h>

@interface BarcodeScannerViewController () <AVCaptureMetadataOutputObjectsDelegate>

@property (nonatomic, strong) AVCaptureSession *captureSession;
@property (nonatomic, strong) AVCaptureVideoPreviewLayer *previewLayer;
@property (nonatomic, strong) dispatch_queue_t sessionQueue;
@property (nonatomic, assign) BOOL didFinish;

@end

@implementation BarcodeScannerViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor blackColor];
    self.sessionQueue = dispatch_queue_create("com.dedris.barcodescanner.session", DISPATCH_QUEUE_SERIAL);

    [self setupUI];
    [self checkPermissionAndConfigure];
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    self.previewLayer.frame = self.view.layer.bounds;
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [self stopSession];
}

#pragma mark - UI

- (void)setupUI {
    UILabel *promptLabel = [[UILabel alloc] init];
    promptLabel.translatesAutoresizingMaskIntoConstraints = NO;
    promptLabel.text = self.promptText != nil ? self.promptText : @"Point your camera at a barcode";
    promptLabel.textColor = [UIColor whiteColor];
    promptLabel.font = [UIFont systemFontOfSize:16];
    promptLabel.numberOfLines = 0;
    promptLabel.textAlignment = NSTextAlignmentCenter;
    promptLabel.backgroundColor = [UIColor colorWithWhite:0 alpha:0.6];
    [self.view addSubview:promptLabel];

    // Centered aiming line — barcodes are wide, so a horizontal guide helps.
    UIView *laser = [[UIView alloc] init];
    laser.translatesAutoresizingMaskIntoConstraints = NO;
    laser.backgroundColor = [[UIColor redColor] colorWithAlphaComponent:0.8];
    [self.view addSubview:laser];

    UIButton *cancelButton = [UIButton buttonWithType:UIButtonTypeSystem];
    cancelButton.translatesAutoresizingMaskIntoConstraints = NO;
    [cancelButton setTitle:@"Cancel" forState:UIControlStateNormal];
    [cancelButton setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    cancelButton.titleLabel.font = [UIFont systemFontOfSize:18 weight:UIFontWeightSemibold];
    cancelButton.backgroundColor = [UIColor colorWithWhite:0 alpha:0.6];
    cancelButton.layer.cornerRadius = 8;
    cancelButton.contentEdgeInsets = UIEdgeInsetsMake(10, 24, 10, 24);
    [cancelButton addTarget:self action:@selector(cancelTapped) forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:cancelButton];

    UILayoutGuide *guide = self.view.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [promptLabel.topAnchor constraintEqualToAnchor:guide.topAnchor constant:24],
        [promptLabel.leadingAnchor constraintGreaterThanOrEqualToAnchor:self.view.leadingAnchor constant:24],
        [promptLabel.trailingAnchor constraintLessThanOrEqualToAnchor:self.view.trailingAnchor constant:-24],
        [promptLabel.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],

        [laser.centerYAnchor constraintEqualToAnchor:self.view.centerYAnchor],
        [laser.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:48],
        [laser.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-48],
        [laser.heightAnchor constraintEqualToConstant:2],

        [cancelButton.bottomAnchor constraintEqualToAnchor:guide.bottomAnchor constant:-32],
        [cancelButton.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor]
    ]];
}

- (void)cancelTapped {
    [self finishWithText:nil format:nil cancelled:YES error:nil];
}

#pragma mark - Permission & session setup

- (void)checkPermissionAndConfigure {
    AVAuthorizationStatus status = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];
    switch (status) {
        case AVAuthorizationStatusAuthorized:
            [self configureSession];
            break;
        case AVAuthorizationStatusNotDetermined:
            [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:^(BOOL granted) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    if (granted) {
                        [self configureSession];
                    } else {
                        [self finishWithText:nil format:nil cancelled:NO error:@"Camera permission was denied"];
                    }
                });
            }];
            break;
        default:
            [self finishWithText:nil format:nil cancelled:NO error:@"Camera permission was denied"];
            break;
    }
}

- (void)configureSession {
    dispatch_async(self.sessionQueue, ^{
        self.captureSession = [[AVCaptureSession alloc] init];
        [self.captureSession beginConfiguration];

        AVCaptureDevice *device = [AVCaptureDevice defaultDeviceWithDeviceType:AVCaptureDeviceTypeBuiltInWideAngleCamera
                                                                    mediaType:AVMediaTypeVideo
                                                                     position:AVCaptureDevicePositionBack];
        if (device == nil) {
            device = [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
        }

        NSError *inputError = nil;
        AVCaptureDeviceInput *input = [AVCaptureDeviceInput deviceInputWithDevice:device error:&inputError];
        if (input == nil || ![self.captureSession canAddInput:input]) {
            [self.captureSession commitConfiguration];
            [self finishOnMainWithError:@"Unable to access the camera"];
            return;
        }
        [self.captureSession addInput:input];

        AVCaptureMetadataOutput *output = [[AVCaptureMetadataOutput alloc] init];
        if (![self.captureSession canAddOutput:output]) {
            [self.captureSession commitConfiguration];
            [self finishOnMainWithError:@"Unable to configure the barcode scanner"];
            return;
        }
        [self.captureSession addOutput:output];
        [output setMetadataObjectsDelegate:self queue:dispatch_get_main_queue()];

        // availableMetadataObjectTypes is only populated once the output is added.
        output.metadataObjectTypes = [self resolveMetadataTypesFromAvailable:output.availableMetadataObjectTypes];

        [self.captureSession commitConfiguration];
        [self.captureSession startRunning];

        dispatch_async(dispatch_get_main_queue(), ^{
            [self setupPreviewLayer];
        });
    });
}

- (void)setupPreviewLayer {
    if (self.captureSession == nil) {
        return;
    }
    self.previewLayer = [AVCaptureVideoPreviewLayer layerWithSession:self.captureSession];
    self.previewLayer.frame = self.view.layer.bounds;
    self.previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill;
    // Insert below the prompt/cancel/laser views that were added in setupUI.
    [self.view.layer insertSublayer:self.previewLayer atIndex:0];
}

#pragma mark - Format resolution (1D only)

/**
 * Builds the set of 1D metadata object types to scan for. QR codes and other
 * 2D codes are never included. The desired list is intersected with the
 * device-supported types to avoid the runtime exception AVFoundation raises
 * when an unsupported type is set.
 */
- (NSArray<AVMetadataObjectType> *)resolveMetadataTypesFromAvailable:(NSArray<AVMetadataObjectType> *)available {
    NSMutableArray<AVMetadataObjectType> *desired = [NSMutableArray array];

    if (self.requestedFormats.count > 0) {
        for (NSString *name in self.requestedFormats) {
            if (![name isKindOfClass:[NSString class]]) {
                continue;
            }
            NSArray<AVMetadataObjectType> *mapped = [self typesForName:name];
            for (AVMetadataObjectType type in mapped) {
                if (![desired containsObject:type]) {
                    [desired addObject:type];
                }
            }
        }
    }

    if (desired.count == 0) {
        [desired addObjectsFromArray:[self allOneDimensionalTypes]];
    }

    NSMutableArray<AVMetadataObjectType> *finalTypes = [NSMutableArray array];
    for (AVMetadataObjectType type in desired) {
        if ([available containsObject:type]) {
            [finalTypes addObject:type];
        }
    }
    return finalTypes;
}

- (NSArray<AVMetadataObjectType> *)allOneDimensionalTypes {
    NSMutableArray<AVMetadataObjectType> *types = [@[
        AVMetadataObjectTypeEAN13Code,
        AVMetadataObjectTypeEAN8Code,
        AVMetadataObjectTypeUPCECode,
        AVMetadataObjectTypeCode39Code,
        AVMetadataObjectTypeCode39Mod43Code,
        AVMetadataObjectTypeCode93Code,
        AVMetadataObjectTypeCode128Code,
        AVMetadataObjectTypeInterleaved2of5Code,
        AVMetadataObjectTypeITF14Code
    ] mutableCopy];

    if (@available(iOS 15.4, *)) {
        [types addObject:AVMetadataObjectTypeCodabarCode];
    }
    return types;
}

/** Maps a public format name to the matching AVFoundation 1D type(s). */
- (NSArray<AVMetadataObjectType> *)typesForName:(NSString *)name {
    NSString *key = [[name stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceCharacterSet]] uppercaseString];

    if ([key isEqualToString:@"EAN_13"]) return @[AVMetadataObjectTypeEAN13Code];
    if ([key isEqualToString:@"EAN_8"]) return @[AVMetadataObjectTypeEAN8Code];
    if ([key isEqualToString:@"UPC_E"]) return @[AVMetadataObjectTypeUPCECode];
    // AVFoundation has no dedicated UPC-A type; UPC-A is reported as EAN-13.
    if ([key isEqualToString:@"UPC_A"]) return @[AVMetadataObjectTypeEAN13Code];
    if ([key isEqualToString:@"CODE_39"]) return @[AVMetadataObjectTypeCode39Code, AVMetadataObjectTypeCode39Mod43Code];
    if ([key isEqualToString:@"CODE_93"]) return @[AVMetadataObjectTypeCode93Code];
    if ([key isEqualToString:@"CODE_128"]) return @[AVMetadataObjectTypeCode128Code];
    if ([key isEqualToString:@"ITF"]) return @[AVMetadataObjectTypeInterleaved2of5Code, AVMetadataObjectTypeITF14Code];
    if ([key isEqualToString:@"CODABAR"]) {
        if (@available(iOS 15.4, *)) {
            return @[AVMetadataObjectTypeCodabarCode];
        }
        return @[];
    }
    return @[]; // QR_CODE / unknown -> ignored
}

- (NSString *)nameForType:(AVMetadataObjectType)type {
    if ([type isEqualToString:AVMetadataObjectTypeEAN13Code]) return @"EAN_13";
    if ([type isEqualToString:AVMetadataObjectTypeEAN8Code]) return @"EAN_8";
    if ([type isEqualToString:AVMetadataObjectTypeUPCECode]) return @"UPC_E";
    if ([type isEqualToString:AVMetadataObjectTypeCode39Code]) return @"CODE_39";
    if ([type isEqualToString:AVMetadataObjectTypeCode39Mod43Code]) return @"CODE_39_MOD_43";
    if ([type isEqualToString:AVMetadataObjectTypeCode93Code]) return @"CODE_93";
    if ([type isEqualToString:AVMetadataObjectTypeCode128Code]) return @"CODE_128";
    if ([type isEqualToString:AVMetadataObjectTypeInterleaved2of5Code]) return @"ITF";
    if ([type isEqualToString:AVMetadataObjectTypeITF14Code]) return @"ITF_14";
    if (@available(iOS 15.4, *)) {
        if ([type isEqualToString:AVMetadataObjectTypeCodabarCode]) return @"CODABAR";
    }
    return @"UNKNOWN";
}

#pragma mark - AVCaptureMetadataOutputObjectsDelegate

- (void)captureOutput:(AVCaptureOutput *)output
didOutputMetadataObjects:(NSArray<__kindof AVMetadataObject *> *)metadataObjects
       fromConnection:(AVCaptureConnection *)connection {
    if (self.didFinish || metadataObjects.count == 0) {
        return;
    }

    AVMetadataObject *first = metadataObjects.firstObject;
    if (![first isKindOfClass:[AVMetadataMachineReadableCodeObject class]]) {
        return;
    }

    AVMetadataMachineReadableCodeObject *code = (AVMetadataMachineReadableCodeObject *)first;

    // Defensive: never report a QR code even if one somehow slips through.
    if ([code.type isEqualToString:AVMetadataObjectTypeQRCode]) {
        return;
    }

    NSString *value = code.stringValue;
    if (value == nil || value.length == 0) {
        return;
    }

    if (self.beepEnabled) {
        AudioServicesPlaySystemSound((SystemSoundID)1057);
    }

    [self finishWithText:value format:[self nameForType:code.type] cancelled:NO error:nil];
}

#pragma mark - Finishing

- (void)finishWithText:(NSString *)text
                format:(NSString *)format
             cancelled:(BOOL)cancelled
                 error:(NSString *)error {
    if (self.didFinish) {
        return;
    }
    self.didFinish = YES;

    [self stopSession];

    dispatch_async(dispatch_get_main_queue(), ^{
        BarcodeScanCompletion completion = self.completion;
        [self dismissViewControllerAnimated:YES completion:^{
            if (completion != nil) {
                completion(text, format, cancelled, error);
            }
        }];
    });
}

- (void)finishOnMainWithError:(NSString *)error {
    dispatch_async(dispatch_get_main_queue(), ^{
        [self finishWithText:nil format:nil cancelled:NO error:error];
    });
}

- (void)stopSession {
    AVCaptureSession *session = self.captureSession;
    if (session == nil) {
        return;
    }
    dispatch_async(self.sessionQueue, ^{
        if (session.isRunning) {
            [session stopRunning];
        }
    });
}

@end
