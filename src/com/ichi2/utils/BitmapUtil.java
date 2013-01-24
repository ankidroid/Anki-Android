package com.ichi2.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

/**
 * @author zaur
 * 
 */
public class BitmapUtil
{

	public static Bitmap decodeFile(File f, int IMAGE_MAX_SIZE)
	{
		Bitmap b = null;
		try
		{
			// Decode image size
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;

			FileInputStream fis = new FileInputStream(f);
			BitmapFactory.decodeStream(fis, null, o);
			fis.close();

			int scale = 1;
			if (o.outHeight > IMAGE_MAX_SIZE || o.outWidth > IMAGE_MAX_SIZE)
			{
				scale = (int) Math.pow(
						2,
						(int) Math.round(Math.log(IMAGE_MAX_SIZE / (double) Math.max(o.outHeight, o.outWidth))
								/ Math.log(0.5)));
			}

			// Decode with inSampleSize
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = scale;
			fis = new FileInputStream(f);
			b = BitmapFactory.decodeStream(fis, null, o2);
			fis.close();
		}
		catch (IOException e)
		{
		}
		return b;
	}

	public static void freeImageView(ImageView imageView)
	{
		if (imageView != null)
		{
			BitmapDrawable bd = (BitmapDrawable) imageView.getDrawable();
			if (bd.getBitmap() != null)
			{
				bd.getBitmap().recycle();
				imageView.setImageBitmap(null);
			}
		}

	}

}
