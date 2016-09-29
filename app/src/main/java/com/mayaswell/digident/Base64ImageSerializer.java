package com.mayaswell.digident;

import android.graphics.Bitmap;
import android.util.Base64;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;

/**
 * Created by dak on 9/28/2016.
 */
public class Base64ImageSerializer implements JsonSerializer<Bitmap> {
	@Override
	public JsonElement serialize(Bitmap src, Type typeOfSrc, JsonSerializationContext context) {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		src.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
		byte[] byteArray = byteArrayOutputStream .toByteArray();
		String encodedImage = Base64.encodeToString(byteArray, Base64.DEFAULT);
		return new JsonPrimitive(encodedImage);
	}
}
