/**
* Setup for ScreenCapture plugin use with Cordova. 
*/
//Options Class for ScreenCapture
function ScreenCaptureOptions() {
	this.x = 0;
	this.y = 0;
	this.width = -1;
	this.height = -1;
	this.fileName = "screenshot";
}
function CompareOptions(compareURL) {
	this.compareURL = compareURL;
	this.colorTolerance = 0.0;
	this.pixelTolerance = 0.0;
	this.writeActualToFile = false;
	this.writeDiffToFile = false;	
	this.binaryDiff = false;
}


//Global Variables
window.delayBeforeCapture = 200; //the number of milliseconds to wait to allow the WebView to update before calling capture
//This assigns the capture function to the window for calling within Javascript.  window.capture will call cordova.exec with a string literal to 
//indicate that we want to call "capture" function in ScreenCapture.java
window.capture = function(callback, errorCallBack, x, y, w, h, fileName) {
	cordova.exec(callback, errorCallBack, "ScreenCapture", "capture",[x,y,w,h,fileName]);
}

window.captureAndCompare = function(callback, errorCallBack,captureOptions,compareOptions) {
	cordova.exec(callback, errorCallBack, "ScreenCapture", "captureAndCompare", [captureOptions,compareOptions]);
}

/*** Capture API ***/
//call the native capture function after a set amount of time to allow the screen to update with desired changes
function callCaptureDelay(callBack, errorCallBack, captureOptions) {
	window.captureComplete = false;
	setTimeout(function(){window.capture(callBack, errorCallBack, captureOptions);}, window.delayBeforeCapture);	
}
/*** CaptureAndCompare API ***/
//call the native capture function after a set amount of time, then compare against the expected image that is specified in compareOptions
function callCaptureAndCompareDelay(callBack, errorCallBack, captureOptions, compareOptions) {
	window.captureComplete = false;
	setTimeout(function(){window.captureAndCompare(callBack, errorCallBack, captureOptions, compareOptions)},window.delayBeforeCapture);		
}

/*//Helper function to call the capture command after the WebView has updated, the parameters indicate a sub-rectangle of the whole screen that we wish to save to a .png
function callCaptureDelay(x,y,w,h) {
	window.captureComplete = false;
	setTimeout(function(){callCapture(x,y,w,h,window.defaultFileName);},window.delayBeforeCapture);	
}
//Helper function, full screenshot, with a specified name
function callCaptureDelay(fileName) {
	window.captureComplete = false;
	setTimeout(function(){callCapture(0,0,-1,-1,fileName);},window.delayBeforeCapture);	
}
//Helper function to call the capture command after the WebView has updated, the parameters indicate a sub-rectangle of the whole screen that we wish to save to a .png
//fileName indicates the optional name of the screen capture file
function callCaptureDelay(x,y,w,h,fileName) {
	window.captureComplete = false;
	setTimeout(function(){window.callCapture(x,y,w,h, fileName);},window.delayBeforeCapture);	
}*/


/*//full screenshot, with specified name
function callCaptureAndCompareDelay(compareURL,colorTolerance,pixelTolerance, writeActualToFile, writeDiffToFile, fileName) {
	window.captureComplete = false;
	setTimeout(function(){callCaptureAndCompare(0,0,-1,-1,compareURL,colorTolerance,pixelTolerance, writeActualToFile, writeDiffToFile, fileName);},window.delayBeforeCapture);		
}
//helper function that doesn't use the optional fileName, default will be used which is "screenshot"
function callCaptureAndCompareDelay(x,y,w,h,compareURL,colorTolerance,pixelTolerance, writeActualToFile, writeDiffToFile) {
	window.captureComplete = false;
	setTimeout(function(){callCaptureAndCompare(x,y,w,h,compareURL,colorTolerance,pixelTolerance, writeActualToFile, writeDiffToFile, window.defaultFileName);},window.delayBeforeCapture);		
}
//helper function that uses optional fileName
function callCaptureAndCompareDelay(x,y,w,h,compareURL,colorTolerance,pixelTolerance, writeActualToFile, writeDiffToFile, fileName) {
	window.captureComplete = false;
	setTimeout(function(){callCaptureAndCompare(x,y,w,h,compareURL,colorTolerance,pixelTolerance, writeActualToFile, writeDiffToFile, fileName);},window.delayBeforeCapture);		
}*/