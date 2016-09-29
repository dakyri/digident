package com.mayaswell.digident;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ViewAnimator;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mayaswell.digident.CatalogAPI.CatalogItem;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

	private static final int MAX_RECENT_ITEMS = 10;

	protected CatalogAPI api = null;
	private ViewAnimator viewAnimator;
	private RecyclerView itemListView;
	private LinearLayoutManager layoutManager;
	private ItemSetAdapter itemSetAdapter;
	protected ItemCache itemCache = new ItemCache(MAX_RECENT_ITEMS);
	protected String[] apiCerts = { /** certs to be pinned for our api access */
			"sha256/rCCCPxtKvFVDrKOPDSfirp4bQOYw4mIVKn8fZxgQcs4=",
			"sha256/klO23nT2ehFDXCfx3eHTDRESMz3asj1muO+4aIdjiuY=",
			"sha256/grX4Ta9HpZx6tSHkmCrvpApTQGo67CYDnvprLg5yRME="
	};
	private RelativeLayout uploadPanel;
	protected File imageFile;
	private float ocrConfidence = 1.0f;
	private ImageView uploadImageView;
	private TextView uploadContentText;
	private TextView uploadConfidenceText;
	private Button uploadButton;
	private Bitmap ocrBitmap = null;
	private String ocrText = "";
	private boolean encryptCache = false;
	private ProgressBar progressBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_main);

		viewAnimator = (ViewAnimator) findViewById(R.id.viewAnimator);
		AlphaAnimation animation = new AlphaAnimation(0.0f, 1.0f);
		animation.setDuration(2000);
		viewAnimator.setInAnimation(animation);
		animation = new AlphaAnimation(1.0f, 0.0f);
		animation.setDuration(2000);
		viewAnimator.setOutAnimation(animation);

		itemSetAdapter = new ItemSetAdapter();
		itemListView = (RecyclerView) findViewById(R.id.itemListView);
		layoutManager = new LinearLayoutManager(this);
		itemListView.setLayoutManager(layoutManager);
		itemListView.setAdapter(itemSetAdapter);
		itemListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);
				int nItems = layoutManager.getItemCount();
				int nVisible = layoutManager.getChildCount();
				int firstItem = layoutManager.findFirstVisibleItemPosition();
				int lastItem = layoutManager.findLastCompletelyVisibleItemPosition();
				if (lastItem == nItems-1 && dy > 0) {
					checkNextItems();
				} else if (firstItem == 0 && dy < 0) {
					checkPrevItems();
				}
			}
		});

		progressBar = (ProgressBar) findViewById(R.id.progressBar);

		uploadPanel = (RelativeLayout) findViewById(R.id.uploadPanel);
		uploadImageView = (ImageView) findViewById(R.id.imageView);
		uploadContentText = (TextView) findViewById(R.id.contentText);
		uploadConfidenceText = (TextView) findViewById(R.id.confidenceText);
		uploadButton = (Button) findViewById(R.id.uploadItemButton);
		uploadButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.d("main", "upload clicked");
				setProgressBarVisibility(true);
				api.putItem(ocrText, ocrConfidence, ocrBitmap).subscribe(new Subscriber<Boolean>() {
					@Override
					public void onCompleted() {
						setProgressBarVisibility(false);
						Log.d("onCompleted()", "put item done");
						showMainPanel();;
					}

					@Override
					public void onError(Throwable e) {
						showProgressBar(false);
						showError("Error uploading data", e.getMessage());
						showMainPanel();;

					}

					@Override public void onNext(Boolean aBoolean) { }
				});
				;

			}
		});

		File imageBase = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
		imageFile = new File(imageBase, "upload.jpg");
		Log.d("main", "got image file at "+imageFile.toString());

		if (api == null) {
			String apiURL = getResources().getString(R.string.apiUrl);
			String apiKey = getResources().getString(R.string.apiKey);
			api = new CatalogAPI(apiURL, apiKey, apiCerts);
		}
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		if (itemCache.size() == 0) { // we don't have any existing cache items
			if (!restorItemCache()) { // if can't fill from shared state
				firstLoad();
			}
		}
	}

	@Override protected void onResume()
	{
		super.onResume();
	}

	/**
	 * mainly catching this to save the item cache, which is out main permanent object of interest
	 */
	@Override
	protected void onPause()
	{
		super.onPause();
		saveItemCache();
	}

	@Override
	protected void onStop()
	{
		super.onStop();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
	}

	@Override
	public void onBackPressed()
	{
		if (viewAnimator.getDisplayedChild() != 0) {
			viewAnimator.setDisplayedChild(0);
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {

			case R.id.option_menu_new_item: {
				startImageProcessing();
				return true;
			}
		}
		return false;
	}
	/**
	 * we'll save out cache here also, as there are situations where the state will be cleared and we'd like to avoid hitting
	 * the catalog ... we're only restoring this cache state when we definitely need ... in onCreate()
	 * @param outState
	 */
	@Override protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		saveItemCache();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) { // standard call back with image data
			Bundle extras = data.getExtras();
			Bitmap thumbBitmap = null;
			if (extras != null) {
				thumbBitmap = (Bitmap) extras.get("data"); // the thumbnail
			}
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			Bitmap mainBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options );
			if (thumbBitmap == null) {
				thumbBitmap = mainBitmap;
			}
			showUploadPanel(thumbBitmap, mainBitmap);
		}
	}

	/**
	 * standard callout to an external camera app
	 */
	static final int REQUEST_IMAGE_CAPTURE = 1;
	private void startImageProcessing() {
		Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
			try {
				imageFile.createNewFile(); // we just want one temporary image for upload and clear it for use each time
			} catch (IOException e) {
				showError("Error", "Can't create "+imageFile.toString());
				return;
			}
			Uri photoURI = FileProvider.getUriForFile(this, "com.example.android.fileprovider", imageFile);
			takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
			startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
		}
	}

	/**
	 * wrapper to show a subpanel relating to the image processing
	 */
	private void showUploadPanel(Bitmap thumbBitmap, Bitmap mainBitmap) {
		ocrText = getTextFrom(mainBitmap);
		ocrBitmap = thumbBitmap;
		uploadConfidenceText.setText(Float.toString(ocrConfidence));
		uploadContentText.setText(ocrText);
		uploadImageView.setImageBitmap(ocrBitmap);
		viewAnimator.setDisplayedChild(1);
	}

	private String getTextFrom(Bitmap mainBitmap) {
		ocrConfidence = 1.0f;
		return "A slew of gradle load issues with Tesseract";
	}

	/**
	 * wrapper to get the main view animator to show our main list item
	 */
	private void showMainPanel() {
		viewAnimator.setDisplayedChild(0);
	}

	/**
	 * stores the cache of recently retreived items int the shared preferences
	 */
	private void saveItemCache() {
		Collection<CatalogItem> recent = itemCache.getRecentList();
		Gson gson = new GsonBuilder().registerTypeAdapter(Bitmap.class, new Base64ImageSerializer()).create();
		String json = gson.toJson(recent);
		SharedPreferences.Editor prefs = getPreferences(MODE_PRIVATE).edit();
		if (encryptCache) {
			try {
				prefs.putString("encryptedCache", new String(encrypt(getKey(), json.getBytes()), "UTF-8"));
			} catch (NoSuchAlgorithmException e) {
				prefs.putString("itemCache", json);
			} catch (Exception e) {
				prefs.putString("itemCache", json);
			}
		} else {
			prefs.putString("itemCache", json);
		}
		prefs.commit();
	}


	/**
	 * restores an item cache from the shared preferences
	 * @return false if there is not usable data in the shared prefs
	 */
	private boolean restorItemCache() {
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		if (prefs == null) {
			return false;
		}
		String json = null;
		if (encryptCache) {
			String jsone = prefs.getString("encryptedCache", null);
			if (jsone != null) {
				try {
					json = new String(decrypt(getKey(), jsone.getBytes()), "UTF-8");
				} catch (Exception e) {
					json = prefs.getString("itemCache", null);
				}
			} else {
				json = prefs.getString("itemCache", null);
			}
		} else {
			json = prefs.getString("itemCache", null);
		}
		if (json == null) {
			return false;
		}
		Gson gson = new GsonBuilder().
				registerTypeAdapter(Bitmap.class, new Base64ImageDeserializer()).
				create();
		Type catalogItemListType = new TypeToken<ArrayList<CatalogItem>>(){}.getType();
		Collection<CatalogItem> items;
		try {
			items = gson.fromJson(json, catalogItemListType);
		} catch (Exception e) {
			Log.d("MainActivity", "Exception on restoreItemCache() "+e.getMessage());
			return false;
		}
		if (items == null || items.size() == 0) {
			return false;
		}
		itemCache.clear();
		itemCache.add(items);
		return true;
	}

	protected void firstLoad() {
		fetchNextCatalogItems().subscribe(new Subscriber<Boolean>() {
			@Override
			public void onCompleted() {
				Log.d("onStart()", "initial load done");
				itemSetAdapter.clear();
				itemSetAdapter.addAll(itemCache.getList());
				showProgressBar(false);
			}

			@Override
			public void onError(Throwable e) {
				showError("Error making initial web requests", e.getMessage());
				showProgressBar(false);
			}

			@Override public void onNext(Boolean aBoolean) { }
		});
	}

	/**
	 * fetch the next page of data if available and set the adapter, if that is the type still being displayed
	 */
	protected void checkNextItems() {
		final int mCurrent = itemCache.getList().size();
//		if (!cacheHasNewerItems()) return;
		fetchNextCatalogItems().subscribe(new Action1<Boolean>() {
			@Override
			public void call(Boolean aBoolean) {
				itemSetAdapter.updateFrom(mCurrent, itemCache.getList());
				showProgressBar(false);
			}
		}, onNetworkError);
	}

	/**
	 * fetch the next page of data if available and set the adapter, if that is the type still being displayed
	 */
	protected void checkPrevItems() {
		final int mCurrent = itemCache.getList().size();
//		if (!cacheHasNewerItems()) return;
		fetchPrevCatalogItems().subscribe(new Action1<Boolean>() {
			@Override
			public void call(Boolean aBoolean) {
				itemSetAdapter.updateFrom(mCurrent, itemCache.getList());
				showProgressBar(false);
			}
		}, onNetworkError);
	}

	/**
	 * build an observable to return the next page of uncached popular movie data, mapping the return result into our
	 * cache after adjusting images to have valid paths
	 * @return
	 */
	protected Observable<Boolean> fetchNextCatalogItems() {
		String nextPage = itemCache.highestId;
		showProgressBar(true);
		return api.getItems(nextPage).map(new Func1<ArrayList<CatalogItem>, Boolean>() {
			@Override
			public Boolean call(ArrayList<CatalogItem> itemSet) {
				Log.d("main", "fetch from "+itemCache.highestId);
				for (CatalogItem m: itemSet) {
					Log.d("main", "fetch "+m.id+", "+m.text);
				}
				itemCache.add(itemSet);
				return true;
			}
		});
	}

	/**
	 * build an observable to return the previous page of uncached popular movie data, mapping the return result into our
	 * cache after adjusting images to have valid paths
	 * @return
	 */
	protected Observable<Boolean> fetchPrevCatalogItems() {
		String prevPage = itemCache.lowestId;
		return api.getItems(null, prevPage).map(new Func1<ArrayList<CatalogItem>, Boolean>() {
			@Override
			public Boolean call(ArrayList<CatalogItem> itemSet) {
				Log.d("main", "fetch from "+itemCache.highestId);
				for (CatalogItem m: itemSet) {
					Log.d("main", "fetch "+m.id+", "+m.text);
				}
				itemCache.add(itemSet);
				return true;
			}
		});
	}

	protected Action1<Throwable> onNetworkError = new Action1<Throwable>() {
		@Override
		public void call(Throwable e) {
			showProgressBar(false);
			showError("Error fetching next from server, ", e.getMessage());
		}
	};

	private void showProgressBar(boolean b) {
		this.setProgressBarIndeterminateVisibility(b);
		if (progressBar != null) {
			progressBar.setVisibility(b? View.VISIBLE: View.GONE);
		}
	}

	/**
	 * show an alert with the given title and message
	 * @param title
	 * @param message
	 */
	protected void showError(String title, String message) {
		AlertDialog.Builder d = new AlertDialog.Builder(this);
		d.setTitle(title);
		d.setMessage(message);
		d.setPositiveButton("ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				dialog.dismiss();
			}
		});
		d.show();
	}

	/**
	 * gets the key for encryption from a fixed seed
	 * @return
	 * @throws NoSuchAlgorithmException
	 */
	private static byte[] getKey() throws NoSuchAlgorithmException {
		byte[] keyStart = "some key see or other".getBytes();
		KeyGenerator kgen = KeyGenerator.getInstance("AES");
		SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
		sr.setSeed(keyStart);
		kgen.init(128, sr); // 192 and 256 bits may not be available
		SecretKey skey = kgen.generateKey();
		return skey.getEncoded();
	}

	/**
	 * aes256 encryption
	 * @param key
	 * @param clear
	 * @return
	 * @throws Exception
	 */
	private static byte[] encrypt(byte[] key, byte[] clear) throws Exception {
		SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
		byte[] encrypted = cipher.doFinal(clear);
		return encrypted;
	}

	/**
	 * aes256 decryption
	 * @param key
	 * @param encrypted
	 * @return
	 * @throws Exception
	 */
	private static byte[] decrypt(byte[] key, byte[] encrypted) throws Exception {
		SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.DECRYPT_MODE, skeySpec);
		byte[] decrypted = cipher.doFinal(encrypted);
		return decrypted;
	}
}
