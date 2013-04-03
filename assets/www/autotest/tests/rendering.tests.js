/**
* Basic Rendering testing.  Renders 2 numPixelsDifferent objects in the canvas, and one image added to the dom and verifies that the rendering is correct by using the
* ScreenCapture plugin.
*/

//modify canvas to have a clear function for convenience 
CanvasRenderingContext2D.prototype.clear = 
  CanvasRenderingContext2D.prototype.clear || function (preserveTransform) {
    if (preserveTransform) {
      this.save();
      this.setTransform(1, 0, 0, 1, 0, 0);
    }

    this.clearRect(0, 0, this.canvas.width, this.canvas.height);

    if (preserveTransform) {
      this.restore();
    }           
};
//need to add a callback for when all the tests are complete to clean up
jasmine.Runner.prototype.finishCallback = function() {
	cleanUp();
	jasmine.getEnv().reporter.reportRunnerResults(this);
};

window.requestAnimFrame = (function(callback) {
        return window.requestAnimationFrame || window.webkitRequestAnimationFrame || window.mozRequestAnimationFrame || window.oRequestAnimationFrame || window.msRequestAnimationFrame ||
        function(callback) {
          window.setTimeout(callback, 1000 / 60);
        };
      })();

/*** Helper functions ***/
window.imagesForCompare = []; //holds the images that we want to compare.  Index 0 is the actual, index 1 is the expected
window.imageLoaded = false; //flag to indicate that a single image has loaded it's source
window.imagesLoaded = false; //flag to indicate that all images provided to loadImages have been loaded
window.numPixelsDifferent = 1; //track the number of pixels in the comparison that did not match the expected
window.expectedImagePath = ""; //store the path for the expected image for the comparison function 
window.actualImagePath = ""; //store the path for the actual image from the capture
window.diffImagePath = ""; //store the path for the diff image
window.captureComplete = false; //flag to indicate that our native capture command has completed
window.stressTestError = false;

//callback functions for capture and captureAndCompare plugin calls
function captureHandler (result) { 
	//set the uri of the image file from the screenCapture, if capture failed this will be an error string, validation done in compare
    //alert(result);
	window.actualImagePath = result; 
	window.captureComplete = true;
} 
function captureAndCompareHandler(result) {
    //alert(result);
	var resultsArray = result.split(' ');
	window.numPixelsDifferent = parseInt(resultsArray[0]);	
	window.actualImagePath = resultsArray[1];
	window.diffImagePath = resultsArray[2];
	window.captureComplete = true;
}
function captureErrorHandler(result) {
	alert(result);
	window.captureComplete = true;
}
function captureAnimationHandler(result) {
    if((result.toLowerCase()).indexOf("error") !== -1) {
        window.stressTestError = true;
    }
	//capture is complete but file io is not complete
	//alert("Capture");
}
/**
* Compare the actual and expected images, using the given tolerance to determine whether the rendering passes or not.
* @param images an array of the images to compare, index 0 is actual, index 1 is the expected
* @param colorTolerance the percent of a color channel can differ from the expected before the pixel is considered a mismatch.  Given in decimal form such as 0.02 (2%)
* @param pixelTolerance the percent of total pixels that mismatch before the compare as a while is considered to fail.  Given in decimal form such as 0.02 (2%)
*/
function compareImages(images, colorTolerance, pixelTolerance) {
	try {
		var img1 = images[0];
		var img2 = images[1];
		ctx1 = document.createElement('canvas').getContext('2d'),
		ctx2 = document.createElement('canvas').getContext('2d');
	
		ctx1.canvas.width = img1.width;
		ctx2.canvas.width = img2.width;
		ctx1.canvas.height = img1.height;
		ctx2.canvas.height = img2.height;
		
		ctx1.drawImage(img1, 0, 0);
		ctx2.drawImage(img2, 0, 0);
		
		var data1 = ctx1.getImageData(0, 0, img1.width, img1.height).data;
		var data2 = ctx2.getImageData(0, 0, img2.width, img2.height).data;
		
		window.numPixelsDifferent = 0;
		
		//For each pixel, compare the RGBA values versus the expected.  data1 is the actual, data2 is the expected.
		//ColorTolerance: For each color, if the actual color is within <ColorTolerance> % of expected then this color is considered passing.
		//				  If any color for that pixel is not within tolerance, then the pixel is considered failing.
		//PixelTolerance: If the total number of failing pixels is within <PixelTolerance> % then the images are considered matching. 
		var onCount = 0;
		var offCount = 0;
		for(var i = 0; i < data1.length; i+=4) {
			//compare each color with given tolerance, if any don't match fail this pixel
			if( Math.abs( (data1[i] - data2[i]) / data1[i] ) > colorTolerance ||
				Math.abs( (data1[i+1] - data2[i+1]) / data1[i+1] ) > colorTolerance ||
				Math.abs( (data1[i+2] - data2[i+2]) / data1[i+2] ) > colorTolerance ||
				Math.abs( (data1[i+3] - data2[i+3]) / data1[i+3] ) > colorTolerance) {
					offCount++;		
			}
			else {
				onCount++;	
			}
		}
		if( (offCount / (data1.length/4)) <= pixelTolerance) {
			window.numPixelsDifferent = 0;	
		}
		else {
			window.numPixelsDifferent = offCount;	
		}
	}
	catch (err) {
		alert(err.message);	
	}
	window.compareComplete = true;
}

//load the images at the given urls, then execute the callback when both all images are loaded
function loadImages(urls, callback) {
	var images = [];
	var imagesToLoad = urls.length;

	// Called each time an image finished
	// loading.
	var onImageLoad = function() {
		--imagesToLoad;
		// If all the images are loaded call the callback.
		if (imagesToLoad == 0) {
			window.imagesForCompare = images;
	  		callback();
		}
	};

	for (var ii = 0; ii < imagesToLoad; ++ii) {
		var image = loadImage(urls[ii], onImageLoad);
		images.push(image);
  }
}

//load a single image at the given url, then execute the callback
function loadImage(url, callback) {
	//alert("Load Image");
	var image = new Image();
	try {
		image.src = url;
	}
	catch(err) {
		//alert("Load Image: "+err.message);
	}
	image.onload = callback;
	return image;
}
//create the options objects for capture and captureAndCompare
var captureOptions = new ScreenCaptureOptions();
captureOptions.fileName = "Screenshots/rendering";

var compareOptions = new CompareOptions("");
compareOptions.writeDiffToFile = true;

/*** Rendering Tests ***/
describe('Image Rendering', function () {
	//cleanup before each test
	beforeEach(function() {
		//defined in screencapture.js
		window.numPixelsDifferent = 1; 
		window.imagesForCompare = []; 
		window.expectedImagePath = "";
		window.actualImagePath = "";
		window.captureComplete = false;
		window.imageLoaded = false;
		window.imagesLoaded = false;
	});
	
        it("Should render a circle", function() {
		//draw to the canvas
		var drawingCanvas = document.getElementById('canvas');
		var context = drawingCanvas.getContext('2d');
		// Check the element is in the DOM and the browser supports canvas
		if(drawingCanvas.getContext) {
			// Create the yellow face
			context.strokeStyle = "#000000";
			context.fillStyle = "#FFFF00";//"#FFFF00";
			context.beginPath();
			context.arc(100,100,50,0,Math.PI*2,true);
			context.closePath();
			context.stroke();
			context.fill();
			
			//set the options for this captureAndCompare call
           captureOptions.x = drawingCanvas.offsetLeft;
           captureOptions.y = drawingCanvas.offsetTop;
           captureOptions.width = drawingCanvas.width;
           captureOptions.height = drawingCanvas.height;
			compareOptions.compareURL = "www/autotest/images/baselines/rendering_1.png";
			compareOptions.pixelTolerance = 0.02;
           compareOptions.colorTolerance = 0.00;
			compareOptions.writeActualToFile = true;
           compareOptions.binaryDiff = false;
			
		}
		//var start = new Date().getTime();
		callCaptureAndCompareDelay(captureAndCompareHandler, captureErrorHandler, captureOptions, compareOptions);
		
		waitsFor(function() {
			return window.captureComplete;
		}, "capture never completed", 2000);
		runs(function() {
			//var end = new Date().getTime();
			//var time = end - start;
			//alert('Execution time: ' + time);
			expect(window.numPixelsDifferent).toBe(0);
		});
	});
    //test 2
    it("Should render a square", function() {
        //draw to the canvas
        var drawingCanvas = document.getElementById('canvas');
        var context = drawingCanvas.getContext('2d');
        // Check the element is in the DOM and the browser supports canvas
        if(drawingCanvas.getContext) {
        // Create the yellow square
        context.clear();
        context.strokeStyle = "#000000";
        context.fillStyle = "#FFFF00";
        context.beginPath();
        context.rect(0,0,256,256);
        context.closePath();
        context.stroke();
        context.fill();
			
        //set the expected image for this test
        compareOptions.compareURL = "www/autotest/images/baselines/rendering_1.png"
       //captureOptions.width = -1;
       //captureOptions.height = -1;
        }
        //var start = new Date().getTime();
        callCaptureAndCompareDelay(captureAndCompareHandler, captureErrorHandler, captureOptions, compareOptions);
            
        waitsFor(function() {
                 return window.captureComplete;
        }, "capture never completed", 2000);
        runs(function() {
             //var end = new Date().getTime();
            // var time = end - start;
            // alert('Execution time: ' + time);
                 window.compareComplete = true;
                 expect(window.numPixelsDifferent).toBe(0);
                 });
        });
	//test 3
	it("should render an image", function() {
		//add an image
		var image = loadImage("../images/red256x256.png", function(){window.imageLoaded = true;});
		image.id = "image";
		document.body.appendChild(image);
		
		//set expected image for this test
		
		compareOptions.compareURL = "www/autotest/images/baselines/rendering_2.png";
		//wait for the image to load, timeout if more than 1 second
		waitsFor(function() {
			return window.imageLoaded;	
		}, "image never loaded",1000);
		
		//once image is loaded, call the capture on the next render
		runs(function() {	
			//set options
			captureOptions.x = image.offsetLeft;
			captureOptions.y = image.offsetTop;
			captureOptions.width = image.width;
			captureOptions.height = image.height;
       
            window.expectedImagePath = "../images/baselines/rendering_2.png";
            //var start = new Date().getTime();
            callCaptureDelay(captureHandler, captureErrorHandler, captureOptions);
			//wait for the capture to complete
			waitsFor(function() {
				return window.captureComplete;
			});
			//once the capture is complete, load the images
			runs(function() {
				//this function takes an array containing the paths to the images you want to load
				loadImages([window.actualImagePath, window.expectedImagePath], function() {window.imagesLoaded = true;});
				//wait for the actual and expected images to load, if not complete within 2 seconds, fail
				waitsFor(function() {
					return window.imagesLoaded;
				}, "could not load one or more of the comparison images", 2000);
				
				//now that the actual and expected images are loaded, do the compare
				runs(function() {
					compareImages(window.imagesForCompare, .00, .04);
					//wait for the compare to complete
					waitsFor(function() {
						return window.compareComplete;
					});
					//now that the compare is complete we can check to see if the test passed
					runs(function() {
                         //var end = new Date().getTime();
                         //var time = end - start;
                         //alert('Execution time: ' + time);
						expect(window.numPixelsDifferent).toBe(0);
						try {
							image.parentNode.removeChild(image);
						}
						catch(err) {}
					});
				});
			});
		});
	});
	
	//test 4
	it("should render a complex image", function() {
		//add an image
		var image2 = loadImage("../images/Gamut594x916.png", function(){window.imageLoaded = true;});
		image2.id = "image2";
		document.body.appendChild(image2);
		
		//set expected image for this test
		//window.expectedImagePath = "../images/baselines/rendering_2.png";
		
		//wait for the image to load, timeout if more than 1 second
		waitsFor(function() {
			return window.imageLoaded;	
		}, "image never loaded",1000);
		
		//once image is loaded, call the capture on the next render
		runs(function() {	
			//set options
			captureOptions.x = image2.offsetLeft;
			captureOptions.y = image2.offsetTop;
			captureOptions.width = image2.width;
			captureOptions.height = image2.height;
			compareOptions.writeActualToFile = true;
			compareOptions.colorTolerance = 0.02;
            compareOptions.pixelTolerance = 0.02;
			compareOptions.compareURL = "www/autotest/images/baselines/rendering_3.png";
			compareOptions.binaryDiff = false;
			
			callCaptureAndCompareDelay(captureAndCompareHandler, captureErrorHandler, captureOptions, compareOptions);
		
			waitsFor(function() {
				return window.captureComplete;
			}, "capture never completed", 10000);
			runs(function() {
				expect(window.numPixelsDifferent).toBe(0);
				try {
					image2.parentNode.removeChild(image2);
				}
				catch(err) {}
			});
		});
	});
	
	
	//test 5 performance
	it("should capture an animation", function() {
       var drawingCanvas = document.getElementById('canvas');
       captureOptions.x = drawingCanvas.offsetLeft;
       captureOptions.y = drawingCanvas.offsetTop;
       captureOptions.width = drawingCanvas.width;
       captureOptions.height = drawingCanvas.height;
		captureOptions.fileName = "Screenshots/animation";
		captureOptions.asynchronous = true;
		animate();
		
		waits(10000);
		runs(function() {
			expect(window.stressTestError).toBe(false);
		});
	});
});
var circleX = 0;

function animate() {
	var canvas = document.getElementById('canvas');
	var context = canvas.getContext('2d');
	
	// update
	circleX = (circleX < canvas.width) ? circleX+1 : 0; 

	// clear
	context.clearRect(0, 0, canvas.width, canvas.height);
	
	// draw stuff
	context.strokeStyle = "#000000";
	context.fillStyle = "#FFFF00";//"#FFFF00";
	context.beginPath();
	context.arc(circleX,100,50,0,Math.PI*2,true);
	context.closePath();
	context.stroke();
	context.fill();
	window.capture(captureHandler, captureErrorHandler, captureOptions);
//	callCaptureDelay(captureHandler, captureErrorHandler, captureOption
	// request new frame
	requestAnimFrame(function() {
	  animate();
	});
}
//cleanup code to be run after all the tests have run
function cleanUp() {
	try {
		var image = document.getElementById('image');
		image.parentNode.removeChild(image);
	}
	catch(err) {}
	try {
		var canvas = document.getElementById('canvas');
		canvas.parentNode.removeChild(canvas);
	}
	catch(err) {}
	try {
		var image2 = document.getElementById('image2');
		image2.parentNode.removeChild(canvas);
	}
	catch(err) {}
}