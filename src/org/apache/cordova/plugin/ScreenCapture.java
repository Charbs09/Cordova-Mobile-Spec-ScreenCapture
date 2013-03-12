package org.apache.cordova.plugin;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.Bitmap.CompressFormat;

import android.os.Environment;

/**
 * This Cordova plugin for Android allows taking a screenshot of the current content running on the device.  When
 * Execute is called from javascript this plugin will take a screenshot, with an optional subrect defined, and 
 * save the results to the sdcard.  The location of the file is then returned to Javascript in the callback function
 * supplied from Javascript.  This plugin also offers comparison of provided baseline images to enable automation of 
 * rendering behavior.
 */
public class ScreenCapture extends CordovaPlugin {
	private int mCaptureCount = 0; //internal counter that increments for each capture made, this is reflected in the resulting .png file
	private String mFileName = ""; //used to store what the user has specified for a filename, if it ever changes we will reset the counter
	//the url of the saved screenshot.  By default the plugin tries to save to the sdcard.  In the event there is no sdcard mounted, Android will
	//save to an emulated location at: mnt/shell/emulator/<user profile number>.  
	//You can access this location using ddms in the android SDK (android-sdk-windows\tools\lib\ddms.bat)
	private String mActualImageLocation = ""; 
	private Picture picture;
	private int mNumPixelsDifferent = 0; 
	private String mDiffImageLocation = "";
	private boolean mUIThreadDone = false;
	/**
	 * execute is called from the cordova plugin framework
	 * @param args contains the coordinates for the subrect of the screen that is to be captured.  Order of arguments is: x, y, width, height
	 * @param callbackContext the callback function provided from javascrip.  This function will be called upon completion of execute
	 */
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		boolean ret = false;
		String resultStr = "";
		//if the fileName has changed, set the global and reset the counter
		JSONObject captureOptions = args.optJSONObject(0);
		String fileName = captureOptions.optString("fileName");
		if(!(fileName.equals(mFileName))) {
			mFileName = fileName;
			mCaptureCount = 0;
		}
		if(action.equals("capture")){
			resultStr =  this.capture(
					captureOptions.optInt("x"),
					captureOptions.optInt("y"),
					captureOptions.optInt("width"),
					captureOptions.optInt("height")
					);
			if(resultStr == "success") {
				ret = true;
				callbackContext.success(mActualImageLocation);
			}
			else {
				ret = false;
				callbackContext.error(resultStr);
			}
		}
		else if(action.equals("captureAndCompare")) {
			//get the options
			JSONObject compareOptions = args.optJSONObject(1);
			resultStr = this.captureAndCompare(
					captureOptions.optInt("x"),
					captureOptions.optInt("y"),
					captureOptions.optInt("width"),
					captureOptions.optInt("height"),
					
					compareOptions.getString("compareURL"),
					compareOptions.getDouble("colorTolerance"),
					compareOptions.getDouble("pixelTolerance"),
					compareOptions.getBoolean("writeActualToFile"),
					compareOptions.getBoolean("writeDiffToFile"),
					compareOptions.getBoolean("binaryDiff")
					);
			if(resultStr == "success") {
				ret = true;
				callbackContext.success(mNumPixelsDifferent+" "+mActualImageLocation+" "+mDiffImageLocation);
			}
			else {
				ret = false;
				callbackContext.error(resultStr);
			}
		}
		
		return ret;
	}
	/**
	 * Take a capture of the current screen. Save the capture as a png file to the default root directory of the sdcard with an optional fileName. 
	 * @param x the x coordinate of the desired subrect for the screen capture
	 * @param y the y coordinate of the desired subrect for the screen capture
	 * @param width the width of the desired subrect 
	 * @param height the height of the desired subrect
	 * @return the location of the saved image file
	 */
	private String capture(int x, int y, int width, int height) {
		String ret = "";
		ret = getScreenBits(x,y,width,height,null, true, mFileName);
		if(ret.startsWith("Err: ")) {
			//something went wrong
			mActualImageLocation = "";
		}
		else {
			//should be ok, return
			mActualImageLocation = ret;
			ret = "success";
			mCaptureCount++;
		}
		
		return ret;
	}
	/**
	 * Get an int[] of the bits on the current screen, if pixels are null then don't bother returning the pixels, that can save us some copies
	 * @param x the x coordinate of the desired subrect for the screen capture
	 * @param y the y coordinate of the desired subrect for the screen capture
	 * @param width the width of the desired subrect 
	 * @param height the height of the desired subrect
	 * @param pixels the array that the screen bits should be placed into if not null, if pixels is null the bits will not be copied back from this function
	 * @param fileName the name of the file to save, not including extension.  Optionally this string can be prepended with "<SomeFolder>/" for saving into sub directories
	 */
	private String getScreenBits(int x, int y, int width, int height, int[] pixels, boolean createActual, String fileName) {
		String ret = "";
		picture = null;
		
		//need to call View.capturePicture on the UI thread since not doing so is deprecated
		mUIThreadDone = false;
		cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
            	CordovaWebView uiThreadView = webView;
            	//capturePicture writes the entire document into a picture object, this includes areas that aren't visible within the current view
            	picture = uiThreadView.capturePicture();
            	mUIThreadDone = true;
            }
        });
		//wait on this thread (core/js thread) until the ui thread  is done taking the capture
		while(!mUIThreadDone) {}
		//write the picture to a bitmap
		Bitmap bm = Bitmap.createBitmap(picture.getWidth(), picture.getHeight(), Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(bm);
		picture.draw(c);
		//get the specified subrect of the screen grab, if width or height are -1, then take a capture of the entire screen
		if(width > 0 && height > 0) {
			//if pixels are null then the caller did not want the pixels back, basically they just want to write the capture to a file
			if(pixels == null) {
				pixels = new int[width*height];
			}
			bm.getPixels(pixels, 0, width, x, y, width, height);
			bm = Bitmap.createBitmap(pixels,width,height, Bitmap.Config.ARGB_8888);
			
		}
		//we want the whole screen, and because pixels is defined we want the pixels back
		else if (pixels != null) {
			pixels = new int[picture.getWidth()*picture.getHeight()];
			bm.getPixels(pixels, 0, picture.getWidth(), x, y, picture.getWidth(), picture.getHeight());
			
		}//else: take whole screenshot and don't return the pixels
		if(createActual) {
			ret = writeBitmapToFile(bm, fileName);
		}
		bm.recycle();
		return ret;
	}
	/**
	 * Take a capture of the current screen and compare against the provided baseline image, optionally write out a diff file
	 * @param x the x coordinate of the desired subrect for the screen capture
	 * @param y the y coordinate of the desired subrect for the screen capture
	 * @param width the width of the desired subrect 
	 * @param height the height of the desired subrect
	 * @param compareURL the url of the baseline image to compare against.  Can be in the assets folder or on the sdcard.  
	 * 					 Format should be given as a relative path assuming assets if root.
	 * @param colorTolerance the percentage the actual and baseline image can differ per each color channel before being considered a fail
	 * @param pixelTolerance the percentage the actual and baseline image can differ per total pixels before being considered a fail
	 * @param createActual flag to optionally create the actual image file
	 * @param createDiff flag to optionally create a file containing the difference of the actual and the baseline
	 * @param binaryDiff flag to output any differences as a solid white pixel if true, if false the full difference in the values is written
	 * @return "success" if the operation completed without error, otherwise the error messege is returned
	 */
	private String captureAndCompare(int x, int y, int width, int height, String compareURL, 
									 double colorTolerance, double pixelTolerance, 
									 boolean createActual, boolean createDiff, boolean binaryDiff) {
		int [] pixels = new int[width*height];
		int [] comparePixels = new int[0];
		int [] diffPixels = new int[0];
		boolean fileNotFound = false;
		
		//populate pixels[] with the bits from the screen, and appropriate subrect if applicable, also write the image to disk if requested
		mActualImageLocation = getScreenBits(x,y,width,height,pixels, createActual, mFileName);
		
		//now that we have the screen capture bits, load the compare image
		//this will load from the assets folder of the application, if we want to use images on the SD card that is a different function
		InputStream is =null;
		try {
		    is=this.cordova.getActivity().getAssets().open(compareURL);
		} catch (IOException e) {
			fileNotFound = true;
		}
		if(fileNotFound) {
			//couldn't get it from assets, try the sdcard
			try {
				is = new FileInputStream(compareURL);
			}
			catch(IOException err) {
				//could not find the file in assets or the sdcard, return a failure
				 return err.getLocalizedMessage();
			}
		}
		
		//decode the png into a bitmap
		Bitmap bm = BitmapFactory.decodeStream(is);
		//create the correct size int[]
		comparePixels = new int[width*height];
		//setup our diff array if the user specified they want one
		if(createDiff) {
			diffPixels = new int[comparePixels.length];
		}
		//get the pixels for comparison
		bm.getPixels(comparePixels, 0, bm.getWidth(), 0, 0, bm.getWidth(), bm.getHeight());
		//compare the images
		mNumPixelsDifferent = compareImageData(pixels, comparePixels,colorTolerance, pixelTolerance, diffPixels, binaryDiff);
		//we have the diff, now create a diff file if requested and there is a difference
		if(createDiff && mNumPixelsDifferent > 0) {
			//create our bitmap
			Bitmap diffBitmap = Bitmap.createBitmap(bm.getWidth(), height, Bitmap.Config.ARGB_8888);

			// Set the pixels
			diffBitmap.setPixels(diffPixels, 0, width, 0, 0, width, height);
			mDiffImageLocation = writeBitmapToFile(diffBitmap, (mFileName+"_Diff"));
			diffBitmap.recycle();
		}
		bm.recycle();
		//increment so we don't overwrite our previous captures
		mCaptureCount++;
		return "success";
	}
	/**
	 * Compare the pixels provided by int[]'s within the given tolerances, write the file if desired
	 * @param data1 first image data for the compare
	 * @param data2 second image data for the compare
	 * @param colorTolerance the percentage the actual and baseline image can differ per each color channel before being considered a fail
	 * @param pixelTolerance the percentage the actual and baseline image can differ per total pixels before being considered a fail
	 * @param diffData optional return of the int[] containing the diff between data1 and data2.  If diffData is not null this will be returned
	 * @param binaryDiff flag to output any differences as a solid white pixel if true, if false the full difference in the values is written
	 * @return the number of pixels that fall outside the given tolerances
	 */
	//TODO: Account for the case that the images are not the same size.  You end up getting a 'stale reference' error when calling setPixels if they don't match
	private int compareImageData(int[] data1, int[] data2, double colorTolerance, double pixelTolerance, int[] diffData, boolean binaryDiff ) {
		int offCount = 0;
		int aDiff,rDiff,gDiff,bDiff, alpha1, alpha2, red1, red2, green1, green2, blue1, blue2;
		int wholeColorTolerance = Math.round((float)255 * (float)colorTolerance);
		boolean createDiff = (diffData.length == 0) ? false : true;
		
		//for each pixel compare each color channel versus the expected
		for(int i = 0; i < data1.length; i++) {
			//get the color value out of the packed int provided by Bitmap.getPixels
			alpha1 = (data1[i] >> 24) & 0xff;
			red1   = (data1[i] >> 16) & 0xff;
			green1 = (data1[i] >> 8) & 0xff;
			blue1  = (data1[i]) & 0xff;
			
			alpha2 = (data2[i] >> 24) & 0xff;
			red2   = (data2[i] >> 16) & 0xff;
			green2 = (data2[i] >> 8) & 0xff;
			blue2  = (data2[i]) & 0xff;
			
			//generate the difference 
			aDiff = Math.abs(alpha1 - alpha2);
			rDiff = Math.abs(red1 - red2);
			gDiff = Math.abs(green1 - green2);
			bDiff = Math.abs(blue1 - blue2);
			
            //compare each color with given tolerance, if any don't match fail this pixel
			if(  aDiff > wholeColorTolerance ||
	             rDiff > wholeColorTolerance ||	
	             gDiff > wholeColorTolerance ||
	             bDiff > wholeColorTolerance) {
            		//one or more channels are outside of tolerance, this pixel fails
            		offCount++;
            		if(createDiff) {
            			if(binaryDiff) {
            				diffData[i] = 0xffffffff;
            			}
            			else {
	            			//we want to be able to see differences, so write alpha as 255 so we don't get a blank bitmap
	            			diffData[i] = 0xff000000;
	            			//pack the rest of the colors into an int for uploading to a bitmap later
	            			diffData[i] |= (rDiff & 255) << 16;
	            			diffData[i] |= (gDiff & 255) << 8;
	            			diffData[i] |= (bDiff & 255);
            			}
            		}
            }
            else {
            	//pixel passes, so render black to indicate no difference
            	if(createDiff) {
            		diffData[i] = 0xff000000;
            	}
            }
            
		} //end pixel for loop
		
		//if our total number of failing pixels is within the tolerance, consider that no pixels failed
		if( ((double)offCount / data1.length ) <= pixelTolerance) {
			offCount = 0;
		}
		
		return offCount;
	}
	/**
	 * Helper function to write the given bitmap to a file with the specified name
	 * @param bm the bitmap to write
	 * @param fileName the name of the file to be written
	 * @return the location of the saved file, or an error message
	 */
	private String writeBitmapToFile(Bitmap bm, String fileName) {
		String fileLocation;
		OutputStream stream = null;
		try {
			fileLocation = Environment.getExternalStorageDirectory() +"/"+fileName+"_"+mCaptureCount+".png";
			stream = new FileOutputStream(fileLocation);
			bm.compress(CompressFormat.PNG, 80, stream);
			if (stream != null) stream.close();
		} catch (IOException e) {
			//imageLocation = "";
			return "Err: "+e.getLocalizedMessage();
		} 
		return fileLocation;
	}
}
