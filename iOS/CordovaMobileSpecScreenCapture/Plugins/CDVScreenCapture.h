//
//  CDVScreenCapture.h
//  CordovaMobileSpecScreenCapture
//
//  Created by Aaron on 3/19/13.
//
//

#ifndef CordovaMobileSpecScreenCapture_CDVScreenCapture_h
#define CordovaMobileSpecScreenCapture_CDVScreenCapture_h
#import <Cordova/CDV.h>
@interface CDVScreenCapture : CDVPlugin
@property (nonatomic, retain) NSString * mFileName;
@property (nonatomic, assign) int mCaptureCount;
- (void)capture:(CDVInvokedUrlCommand*)command;

@end


#endif
