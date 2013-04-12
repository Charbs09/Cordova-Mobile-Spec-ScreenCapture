package org.apache.cordova.plugin;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.CordovaInterface;
import org.apache.cordova.api.PluginResult;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.Bitmap.CompressFormat;
import android.os.Environment;

public class ScreenCaptureIOAndCompare implements Runnable {
	private CallbackContext mCallback;
	private JSONObject mCaptureOptions;
	private JSONObject mCompareOptions;
	private CordovaInterface mCordova;
	private Picture mPicture;
	private String mFileName;
	public ScreenCaptureIOAndCompare(CordovaInterface cordova, CallbackContext callback, JSONObject captureOptions, JSONObject compareOptions, Picture picture, String fileName) {
		mCordova = cordova;
		mCallback = callback;
		mCaptureOptions = captureOptions;
		mCompareOptions = compareOptions;
		mPicture = picture;
		mFileName = fileName;
	}
	@Override
	public void run() {
		// TODO Auto-generated method stub
		 int x = mCaptureOptions.optInt("x");
		 int y = mCaptureOptions.optInt("y");
		 int width = mCaptureOptions.optInt("width");
		 int height = mCaptureOptions.optInt("height");
		
		 //need to know if we want to write the actual file, option for compare, automatically true for capture
		 boolean createActual = (mCompareOptions != null) ? mCompareOptions.optBoolean("writeActualToFile") : true; 
				 
		 String fileLocation = "";
		 
		 int[] internalPixels = new int[0];
		 
		 /**** Write Capture File Portion ****/
		//copy whole picture into a bitmap
		Bitmap bm = Bitmap.createBitmap(mPicture.getWidth(), mPicture.getHeight(), Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(bm);
		mPicture.draw(c);
		
		if(width > 0 && height > 0) {
			//width and height > 0 means we want to sub rect
			internalPixels = new int[width*height];
			bm.getPixels(internalPixels, 0, width, x, y, width, height);
			bm = Bitmap.createBitmap(internalPixels,width,height, Bitmap.Config.ARGB_8888);
		}
		else if(mCompareOptions != null) {
			int w = mPicture.getWidth();
			int h = mPicture.getHeight();
			//no sub rect requested, but we want to do a compare so create the pixels
			internalPixels = new int[w*h];
			bm.getPixels(internalPixels, 0, w, x, y, w, h);
		}
		//else no subrect requested and no pixels back
		
		if(mCompareOptions == null || createActual == true) {
			//write only if we are in a pure capture function call, or our mCompareOptions wants an actual output
			fileLocation = writeBitmapToFile(bm, mFileName);
		}
		bm = null;
		//bm.recycle();
		
		
		/**** Compare portion ****/
		//we have a comparison url so we want to do a compare now
		if(mCompareOptions != null) {
			//set the options
			String compareURL = mCompareOptions.optString("compareURL");
			boolean createDiff = mCompareOptions.optBoolean("writeDiffToFile");
			double colorTolerance = mCompareOptions.optDouble("colorTolerance");
			double pixelTolerance = mCompareOptions.optDouble("pixelTolerance");
			boolean binaryDiff = mCompareOptions.optBoolean("binaryDiff");
			 
			boolean fileNotFound = false;
			int[] comparePixels;
			int[] diffPixels = new int[0];
			int numPixelsDifferent = 0;
			int compareWidth, compareHeight;
			String diffFileLocation = "";
			InputStream is =null;
			try {
			    is=mCordova.getActivity().getAssets().open(compareURL);
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
					mCallback.error("Error: Could not open compare image:"+err.getLocalizedMessage());
					 //return err.getLocalizedMessage();
				}
			}
			
			//decode the png into a bitmap
			bm = BitmapFactory.decodeStream(is);
			compareWidth = bm.getWidth();
			compareHeight = bm.getHeight();
			//create the correct size int[]
			comparePixels = new int[compareWidth * compareHeight];
			//setup our diff array if the user specified they want one
			if(createDiff) {
				diffPixels = new int[comparePixels.length];
			}
			//get the pixels for comparison
			bm.getPixels(comparePixels, 0, compareWidth, 0, 0, compareWidth, compareHeight);
			//compare the images
			numPixelsDifferent = compareImageData(internalPixels, comparePixels,colorTolerance, pixelTolerance, diffPixels, binaryDiff);
			//we have the diff, now create a diff file if requested and there is a difference
			if(createDiff && numPixelsDifferent > 0) {
				//create our bitmap
				Bitmap diffBitmap = Bitmap.createBitmap(compareWidth, compareHeight, Bitmap.Config.ARGB_8888);
	
				// Set the pixels
				diffBitmap.setPixels(diffPixels, 0, compareWidth, 0, 0, compareWidth, compareHeight);
				diffFileLocation = writeBitmapToFile(diffBitmap, (mFileName+"_Diff"));
				diffBitmap.recycle();
			}
			bm.recycle();
			//even if async was true (ie we called the callback already), compare was also requested so use the callback to return the results
			PluginResult result = new PluginResult(PluginResult.Status.OK, numPixelsDifferent+" "+fileLocation+" "+diffFileLocation);
			result.setKeepCallback(false);
			mCallback.sendPluginResult(result);
			//cleanup
			comparePixels = null;
			diffPixels = null;
			
		}
		//no compare url was given, and we did not callback upon picture grab so now we return with just the fileLocation
		else  {
			PluginResult result = new PluginResult(PluginResult.Status.OK, fileLocation);
			result.setKeepCallback(false);
			mCallback.success(fileLocation);
		}
		
		
	}
	
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
	
	private String writeBitmapToFile(Bitmap bm, String fileName) {
		String fileLocation;
		OutputStream stream = null;
		try {
			fileLocation = Environment.getExternalStorageDirectory() +"/"+fileName+".png";
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
