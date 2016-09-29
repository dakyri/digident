package com.mayaswell.digident;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.util.ArrayList;

import okhttp3.CertificatePinner;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * handle http requests and conversions
 */
public class CatalogAPI {

	private String lastSinceId = null;
	private String lastMaxId = null;

	/**
	 * class representing an item in the catalog
	 */
	public static class CatalogItem {
		public float confidence;
	@SerializedName("img")
		public Bitmap image;
		public String text;
	@SerializedName("_id")
		public String id;
	}

	private final String key;
	private final String url;
	private final OkHttpClient client;

	/**
	 * do the basic mapping of an okhttp response into a string ... we're assuming the amount of data
	 * is reasonable (catalog api is fixed at 10), so .string() is a reasonable way to grab all data
	 */
	private Func1<Response, String> responseBodyMapper = new Func1<Response, String>() {
		@Override
		public String call(Response response) {
			int responseCode = response.code();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				throw new RuntimeException("Bad response: "+httpResonseMessage(responseCode));
			}
			String responseBody = "";
			try {
				responseBody = response.body().string();
				response.body().close();
			} catch (IOException e) {
				response.body().close();
				throw new RuntimeException("IO Exception getting response body"+e.getMessage());
			} catch (Exception e) {
				response.body().close();
				throw new RuntimeException("Unexpected Exception getting response body "+e.getClass().toString());
			}

			return responseBody;
		}

	};

	private String httpResonseMessage(int responseCode) {
		switch (responseCode) {
			case HttpURLConnection.HTTP_OK:
				return "OK";
			case HttpURLConnection.HTTP_FORBIDDEN:
				return "Forbidden";
			case HttpURLConnection.HTTP_UNAUTHORIZED:
				return "Unauthorized";
			default:
				return "code " + responseCode;
		}
	}

	/**
	 * create the basic observable of an okhttp response
	 * @param okRequest
	 * @return
	 */
	@NonNull
	private Observable<Response> createObservable(final Request okRequest) {
		return Observable.create(new Observable.OnSubscribe<Response>() {

			@Override
			public void call(Subscriber<? super Response> subscriber) {
				Log.d("catalogapi", "created observervable on "+okRequest.toString()+" on "+Thread.currentThread().getId());
				try {
					Response response = client.newCall(okRequest).execute();
					subscriber.onNext(response);
					if (!response.isSuccessful()) {
						subscriber.onError(new Exception("error"));
					} else {
						subscriber.onCompleted();
					}
				} catch (IOException e) {
					e.printStackTrace();
					subscriber.onError(e);
				}
			}
		});
	}

	/**
	 * construct an okhttp Request object for an item GET request
	 * @param sinceId first item to retreive
	 * @param maxId last to retrieve
	 * @return the request.
	 */
	public Request itemRequest(String sinceId, String maxId) {
		HttpUrl okurl = HttpUrl.parse(url);
		if (okurl == null) {
			throw new RuntimeException("request builder fails on " + url);
		}
		HttpUrl.Builder b = okurl.newBuilder();
		b.addPathSegment("items");
		if (sinceId != null && !sinceId.equals("")) {
			b.addQueryParameter("since_id", sinceId);
		}
		if (maxId != null && !maxId.equals("")) {
			b.addQueryParameter("max_id", maxId);
		}
		okurl = b.build();
//		Log.d("fetchRequest", "made url!! "+okurl.toString());
		return new Request.Builder().url(okurl).get().addHeader("Authorization", key).build();
	}

	/**
	 * construct the actual request to put an item out there
	 * @param text
	 * @param confidence
	 * @param bitmap
	 * @return
	 */
	private Request putRequest(String text, float confidence, Bitmap bitmap) {
		HttpUrl okurl = HttpUrl.parse(url);
		if (okurl == null) {
			throw new RuntimeException("request builder fails on " + url);
		}
		HttpUrl.Builder b = okurl.newBuilder();
		b.addPathSegment("item");
		okurl = b.build();
		ArrayList<CatalogItem> toadd = new ArrayList<CatalogItem>();
		CatalogItem i = new CatalogItem();
		i.id = null;
		i.text = text;
		i.confidence = confidence;
		i.image = bitmap;
		toadd.add(i);
		Gson gson = new GsonBuilder().registerTypeAdapter(Bitmap.class, new Base64ImageSerializer()).create();
		String json = gson.toJson(toadd);

		RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "");
		return new Request.Builder().url(okurl).post(requestBody).addHeader("Authorization", key).build();
	}


	/**
	 * construct an observable to put this item out there
	 * @param text ocr text
	 * @param confidence conversion confidence
	 * @param bitmap bitmap converted from
	 * @return an observable for the request for catalog items
	 */
	public Observable<Boolean> putItem(String text, float confidence, Bitmap bitmap) {
		Log.d("api", "constructing request " + text);
		Request okRequest = putRequest(text, confidence, bitmap);
		Log.d("api", "sending request " + okRequest.toString());
		Observable<Response> observable = createObservable(okRequest);
		return observable
				.subscribeOn(Schedulers.newThread())
				.map(responseBodyMapper)
				.map(new Func1<String, Boolean>() {
					@Override
					public Boolean call(String responseBody) {
						return true;
					}
				})
				.observeOn(AndroidSchedulers.mainThread());
	}

	/**
	 * @see #getItems()
	 * @return
	 */
	public Observable<ArrayList<CatalogItem>> getItems() {
		return getItems(null, null);
	}

	/**
	 * @see #getItems()
	 * @return
	 */
	public Observable<ArrayList<CatalogItem>> getItems(String sinceId) {
		return getItems(sinceId, null);
	}

	/**
	 * @param sinceId first item to retreive
	 * @param maxId last to retrieve
	 * @return an observable for the request for catalog items
	 */
	public Observable<ArrayList<CatalogItem>> getItems(String sinceId, String maxId) {
		lastSinceId = sinceId;
		lastMaxId = maxId;
		Request okRequest = itemRequest(sinceId, maxId);
		Log.d("api", "sending request " + okRequest.toString());
		Observable<Response> observable = createObservable(okRequest);
		return observable
				.subscribeOn(Schedulers.newThread())
				.map(responseBodyMapper)
				.map(new Func1<String, ArrayList<CatalogItem>>() {
					@Override
					public ArrayList<CatalogItem> call(String responseBody) {
						return parseCatalogItems(responseBody);
					}
				})
				.observeOn(AndroidSchedulers.mainThread());
	}

	/**
	 * parses the given response for a list of items, else throws an exception
	 * @param responseBody
	 * @return a list of items
	 */
	@NonNull
	protected ArrayList<CatalogItem> parseCatalogItems(String responseBody) {
		Gson gson = new GsonBuilder().
				registerTypeAdapter(Bitmap.class, new Base64ImageDeserializer()).
				create();
		Type catalogItemListType = new TypeToken<ArrayList<CatalogItem>>(){}.getType();
		ArrayList<CatalogItem> items = gson.fromJson(responseBody, catalogItemListType);
		if (items == null) {
			throw new RuntimeException("Unexpected null result processing JSON");
		}
		return items;
	}


	/**
	 * standard constructor
	 * @param u base url to load from
	 * @param k security key, added to headers as "Authorization"
	 * @param certs if non-null and non empty contains certs to pin for the host on our given url
	 */
	public CatalogAPI(String u, String k, String[] certs) {
		this.url = u;
		this.key = k;
		if (certs == null || certs.length == 0) {
			client = new OkHttpClient();
		} else {
			HttpUrl okurl = HttpUrl.parse(url);
			CertificatePinner.Builder pinnerator = new CertificatePinner.Builder();
			for (String c: certs) {
				pinnerator.add(okurl.host(), c);
			}
			client = new OkHttpClient.Builder()
					.certificatePinner(pinnerator.build())
					.build();
		}
	}


}
