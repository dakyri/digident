package com.mayaswell.digident;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

/**
 * helper class for gson parsing
 */
public class Base64ImageDeserializer implements JsonDeserializer<Bitmap> {

	@Override
	public Bitmap deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
		if (json == null) return null;
		byte[] decodedBytes = Base64.decode(json.toString(), Base64.DEFAULT);
		Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
		return decodedBitmap;
	}
}
