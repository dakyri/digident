package com.mayaswell.digident;

import android.content.Context;
import android.databinding.BindingAdapter;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

/**
 * Created by dak on 9/27/2016.
 */
public final class BindingHelper {
	@BindingAdapter("imageData")
	public static void setImageData(ImageView imageView, Bitmap data) {
		Context context = imageView.getContext();
//		Glide.with(context).load(url).into(imageView);
		imageView.setImageBitmap(data);
	}

}
