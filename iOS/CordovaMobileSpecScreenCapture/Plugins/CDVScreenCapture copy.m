//
//  CDVScreenCapture.m
//  CordovaMobileSpecScreenCapture
//
//  Created by Aaron on 3/19/13.
//
//

#import "CDVScreenCapture.h"
#import <Cordova/CDV.h>

@implementation CDVScreenCapture
int mCaptureCount = 0;
NSString* mFileName = nil;
+ (void)initialize {
    if(!mFileName)
        mFileName = @"screencapture";
}
- (void)capture:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    NSObject* captureOptions = [command.arguments objectAtIndex:0];
    NSString* fileName = [captureOptions valueForKey:@"fileName"];
    if(![fileName isEqualToString:mFileName ]) {
        mFileName = fileName; //[mFileName setString:fileName];
        mCaptureCount = 0;
    }
    
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:fileName];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}
- (void)captureAndCompare:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    NSObject* captureOptions = [command.arguments objectAtIndex:0];
    NSObject* compareOptions = [command.arguments objectAtIndex:1];
    
    NSString* fileName = [captureOptions valueForKey:@"fileName"];
    mFileName = @"screencapture";
    if(![fileName isEqualToString:mFileName ]) {
        mFileName = [NSString stringWithString: fileName];
        mCaptureCount = 0;
    }
    
   // NSString* fileName = [captureOptions valueForKey:@"fileName"];
    //NSString* compareURL = [compareOptions valueForKey:@"compareURL"];
    
    //get a capture
    NSURL* actualFileURL = [self getScreenBits:captureOptions:compareOptions:mCaptureCount:command];
    NSString* actualFileString  = [actualFileURL absoluteString];
    //get the capture
    
    int diffCount = 0;
    //prepare an array containing the string we will return
    NSArray *retArray = [[NSArray alloc] initWithObjects:
                         [NSString stringWithFormat:@"%d", diffCount],
                         actualFileString,
                         [compareOptions valueForKey:@"compareURL"],
                         nil];
    //append the string with a space in between
    NSString *ret = [retArray componentsJoinedByString:@" "];
    
    /*pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:ret];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];*/
}
- (NSURL*) getScreenBits:(NSObject*)captureOptions : (NSObject*)compareOptions : (int) captureCount : (CDVInvokedUrlCommand*)command
{
    //Create original tmp bounds
    CGRect tmpFrame = self.webView.frame;
    CGRect tmpBounds = self.webView.bounds;
    CGRect aFrame = self.webView.bounds;
    aFrame.size.width = self.webView.frame.size.width;
    aFrame.size.height = self.webView.frame.size.height;
    self.webView.frame = aFrame;
    aFrame.size.height = [self.webView sizeThatFits:[[UIScreen mainScreen] bounds].size].height;
    
    NSLog(@"webpage size %f",self.webView.frame.size.height);
    
    self.webView.frame = aFrame;
    //get the capture parameters
    NSInteger width = [[captureOptions valueForKey:@"width"] intValue];
    NSInteger height = [[captureOptions valueForKey:@"height"]intValue];
    NSInteger x = [[captureOptions valueForKey:@"x"] intValue];
    NSInteger y = [[captureOptions valueForKey:@"y"]intValue];
    
    
    //set the bounds to what is provided from the capture options
    //UIGraphicsBeginImageContext([self.webView sizeThatFits:[[UIScreen mainScreen] bounds].size]);
    
    //scroll to the location we need to start from, using javascript inside the uiWebView
    //make the javascript command
    //need current scrollY/X to know how much to scroll
    int scrollYPosition = [[self.webView stringByEvaluatingJavaScriptFromString:@"window.pageYOffset"] intValue];
    int scrollXPosition = [[self.webView stringByEvaluatingJavaScriptFromString:@"window.pageXOffset"] intValue];
    x = x - scrollXPosition;
    y = y - scrollYPosition;
    //NSString* jsScroll = [NSString stringWithFormat:@"window.scrollTo(%d,%d);",x,y];
    //NSString* jsScroll = @"window.scrollTo(5, 5);";
    self.webView.scrollView.contentOffset = CGPointMake(x,y);
    UIGraphicsBeginImageContext(CGSizeMake(width, height));
    //[self.webView stringByEvaluatingJavaScriptFromString:jsScroll];
    CGContextRef resizedContext = UIGraphicsGetCurrentContext();
    
    // crash
    [self.webView.layer renderInContext:resizedContext]; // crash
    // crash
    
    UIImage *image = UIGraphicsGetImageFromCurrentImageContext();
    
    UIGraphicsEndImageContext();
    self.webView.frame = tmpFrame;
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES);
    NSString *documentsDirectory = [paths objectAtIndex:0];
    NSString *pngPath = [documentsDirectory stringByAppendingPathComponent:[NSString stringWithFormat:@"%@_%d.png",mFileName, captureCount]];
    NSError *error;
    [UIImagePNGRepresentation(image) writeToFile:pngPath options:NSDataWritingAtomic error:&error];
    //UIImageWriteToSavedPhotosAlbum(image, self, @selector(image:didFinishSavingWithError:contextInfo:), nil);
    
    NSURL *url = [NSURL fileURLWithPath:pngPath];
    
    /*** COMPARE CODE ***/
    //create the correct sized temp buffer for the image pixels
    unsigned char *actualData = malloc(height * width * 4);
    [self getRawDataFromImage:image : actualData];
      
    
    
    //do a compare if we need
    UIImage *compareImage = [UIImage imageNamed:[compareOptions valueForKey:@"compareURL"]];
    //UIImageWriteToSavedPhotosAlbum(compareImage, self, @selector(image:didFinishSavingWithError:contextInfo:), nil);
    //get acutal image data
    CFDataRef actualImageData = CGDataProviderCopyData(CGImageGetDataProvider(image.CGImage));
    CFDataRef compareImageData = CGDataProviderCopyData(CGImageGetDataProvider(compareImage.CGImage));
    //now compare the data
    //UInt8 * actualPixels = (UInt8 *) CFDataGetBytePtr(actualImageData);
    //UInt8 * comparePixels = (UInt8 *) CFDataGetBytePtr(compareImageData);
    float colorTolerance = [[compareOptions valueForKey:@"colorTolerance"] floatValue];
    float pixelTolerance = [[compareOptions valueForKey:@"pixelTolerance"] floatValue];
    bool binaryDiff = [[compareOptions valueForKey:@"binaryDiff"] boolValue];
    //create buffer for diff image
    UInt8 diffData[width*height*4];
    int offCount = [self compareImageData: actualImageData :compareImageData : colorTolerance : pixelTolerance : diffData : binaryDiff];
    //output diffFile
    if(diffData != nil) {
        // Create a color space
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        if (colorSpace == NULL)
        {
            fprintf(stderr, "Error allocating color space\n");
            return nil;
        }
        
        CGContextRef context = CGBitmapContextCreate (diffData, width, height,
                                                      8, width * 4, colorSpace,
                                                      kCGBitmapByteOrder32Big | kCGImageAlphaPremultipliedLast
                                                      );
        CGColorSpaceRelease(colorSpace );
        
        if (context == NULL)
        {
            fprintf (stderr, "Error: Context not created!");
            return nil;
        }
        
        CGImageRef ref = CGBitmapContextCreateImage(context);
        //free(CGBitmapContextGetData(context));                                      //* this appears to free bits -- probably not mine to free!
        CGContextRelease(context);
        
        UIImage * diffImage = [UIImage imageWithCGImage:ref];
        CFRelease(ref);
        
        
        //NSData * nsData = [NSData dataWithBytes:&diffData length:(width*height*4)];
        //UIImage * diffImage = [UIImage imageWithData:nsData];
        UIImageWriteToSavedPhotosAlbum(diffImage, self, @selector(image:didFinishSavingWithError:contextInfo:), nil);
        
    }
    //prepare an array containing the string we will return
    NSArray *retArray = [[NSArray alloc] initWithObjects:
                         [NSString stringWithFormat:@"%d", offCount],
                         url,
                         [compareOptions valueForKey:@"compareURL"],
                         nil];
    //append the string with a space in between
    NSString *ret = [retArray componentsJoinedByString:@" "];
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:ret];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    
    
    //reset webview
    self.webView.bounds = tmpBounds;
    self.webView.frame = tmpFrame;
    return url;
}
- (int) compareImageData: (CFDataRef) actualData : (CFDataRef) compareData : (float) colorTolerance :(float) pixelTolerance : (UInt8 *) diffPixels : (bool) binaryDiff
{
    int length = CFDataGetLength(actualData);
    bool createDiff = (diffPixels == nil) ? false : true;
    int wholeColorDifference = (255 * colorTolerance)+0.5;
    UInt8 * actualPixels = (UInt8 *) CFDataGetBytePtr(actualData);
    UInt8 * comparePixels = (UInt8 *) CFDataGetBytePtr(compareData);
    //UInt8 * diffPixels = (UInt8 *) CFDataGetBytePtr(diffData);
    int aDiff,rDiff,gDiff,bDiff;
    int offCount = 0;
    for(int i=0; i < length; i+=4) {
        //generate the difference
        rDiff = abs(actualPixels[i] - comparePixels[i]);
        gDiff = abs(actualPixels[i+1] - comparePixels[i+1]);
        bDiff = abs(actualPixels[i+2] - comparePixels[i+2]);
        aDiff = abs(actualPixels[i+3] - comparePixels[i+3]);
        
        if(aDiff > wholeColorDifference ||
           rDiff > wholeColorDifference ||
           gDiff > wholeColorDifference ||
           bDiff > wholeColorDifference ) {
            offCount++;
            int rA = actualPixels[i];
            int gA = actualPixels[i+1];
            int bA = actualPixels[i+2];
            int aA = actualPixels[i+3];
            int rC = comparePixels[i];
            int gC = comparePixels[i+1];
            int bC = comparePixels[i+2];
            int aC = comparePixels[i+3];
            if(createDiff) {
                if(binaryDiff) {
                    diffPixels[i] = 255;
                    diffPixels[i+1] = 255;
                    diffPixels[i+2] = 255;
                    diffPixels[i+3] = 255;
                }
                else {
                    diffPixels[i] = rDiff;
                    diffPixels[i+1] = gDiff;
                    diffPixels[i+2] = bDiff;
                    diffPixels[i+3] = 255;
                }
            }
        }
        else {
            diffPixels[i] = 0;
            diffPixels[i+1] = 0;
            diffPixels[i+2] = 0;
            diffPixels[i+3] = 255;
        }
    }
    if( ((float)offCount / length) <= pixelTolerance) {
        offCount = 0;
    }
    return offCount;
}
- (void)getRawDataFromImage:(UIImage *) image : (unsigned char *)rawData
{
    CGContextRef ctx;
    CGImageRef imageRef = [image CGImage];
    NSUInteger width = CGImageGetWidth(imageRef);
    NSUInteger height = CGImageGetHeight(imageRef);
    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    //unsigned char *rawData = malloc(height * width * 4);
    NSUInteger bytesPerPixel = 4;
    NSUInteger bytesPerRow = bytesPerPixel * width;
    NSUInteger bitsPerComponent = 8;
    CGContextRef context = CGBitmapContextCreate(rawData, width, height,
                                                 bitsPerComponent, bytesPerRow, colorSpace,
                                                 kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
    CGColorSpaceRelease(colorSpace);
    
    CGContextDrawImage(context, CGRectMake(0, 0, width, height), imageRef);
    CGContextRelease(ctx);
}
- (void)image:(UIImage *)image didFinishSavingWithError:(NSError *)error contextInfo:(void *)contextInfo
{
    if (error != NULL)
    {
         NSLog(@"error saving picture image");
    }
    else
    {
        // handle ok status
    }
}


@end
