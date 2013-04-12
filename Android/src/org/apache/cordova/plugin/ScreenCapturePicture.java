package org.apache.cordova.plugin;

import android.graphics.Picture;

public class ScreenCapturePicture {
	public Picture picture;
	public String fileName;
	ScreenCapturePicture(Picture pic, String name) {
		picture = pic;
		fileName = name;
	}
}
