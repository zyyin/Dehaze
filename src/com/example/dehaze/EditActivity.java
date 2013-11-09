package com.example.dehaze;

import java.io.IOException;
import java.io.InputStream;

import uk.co.senab.photoview.PhotoViewAttacher;
import uk.co.senab.photoview.PhotoViewAttacher.OnPhotoTapListener;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.Toast;
import easyimage.slrcamera.ImageProcessX;

public class EditActivity extends Activity implements OnClickListener {

	private static final String TAG = EditActivity.class.getCanonicalName();

	private static final int REQ_PICK_IMAGE = 1;

	private Button mPickButton;
	private ImageView mOriginImageView, mResultImageView;
	private PhotoViewAttacher mOriginAttacher, mResultAttacher;

	private Bitmap mOriginBitmap, mResultBitmap;

	private class RetainData {
		Bitmap origin, result;

		public RetainData(Bitmap origin, Bitmap result) {
			this.origin = origin;
			this.result = result;
		}
	}

	private void setOriginImageView(Bitmap bitmap) {
		mOriginBitmap = bitmap;
		mOriginImageView.setScaleType(ScaleType.CENTER_INSIDE);
		mOriginImageView.setImageBitmap(bitmap);
		mOriginAttacher = new PhotoViewAttacher(mOriginImageView);
		mOriginAttacher.setOnPhotoTapListener(mOnPhotoTapListener);
	}

	private void setResultImageView(Bitmap bitmap) {
		mResultBitmap = bitmap;
		mResultImageView.setScaleType(ScaleType.CENTER_INSIDE);
		mResultImageView.setImageBitmap(bitmap);
		mResultAttacher = new PhotoViewAttacher(mResultImageView);
		mResultAttacher.setOnPhotoTapListener(mOnPhotoTapListener);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_edit);

		mOriginImageView = (ImageView) findViewById(R.id.origin_image);
		mResultImageView = (ImageView) findViewById(R.id.result_image);
		mPickButton = (Button) findViewById(R.id.pick);

		mPickButton.setOnClickListener(this);

		final RetainData data = (RetainData) getLastNonConfigurationInstance();
		if (data != null && data.origin != null && data.result != null) {
			setOriginImageView(data.origin);
			setResultImageView(data.result);
			mPickButton.setVisibility(View.GONE);
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return new RetainData(mOriginBitmap, mResultBitmap);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mOriginImageView.setVisibility(View.VISIBLE);
		mResultImageView.setVisibility(View.GONE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.edit_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_select:
			pickImage();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.pick:
			pickImage();
			break;
		}
	}

	private void pickImage() {
		Intent intent = new Intent();
		intent.setType("image/*");
		intent.setAction(Intent.ACTION_GET_CONTENT);
		startActivityForResult(Intent.createChooser(intent, "Select Picture"),
				REQ_PICK_IMAGE);
	}

	private void switchImage() {
		boolean originVisible = mOriginImageView.getVisibility() != View.GONE;
		mOriginImageView
				.setVisibility(originVisible ? View.GONE : View.VISIBLE);
		mResultImageView.setVisibility(!originVisible ? View.GONE
				: View.VISIBLE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQ_PICK_IMAGE) {
			if (data != null && data.getData() != null) {
				Uri uri = data.getData();
				Log.d(TAG, uri.toString());
				showImage(uri);
			}
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	public static int getOrientation(Context context, Uri photoUri) {
		Cursor cursor = context.getContentResolver().query(photoUri,
				new String[] { MediaStore.Images.ImageColumns.ORIENTATION },
				null, null, null);
		if (cursor != null) {
			try {
				if (cursor.moveToFirst()) {
					return cursor.getInt(0);
				}
			} finally {
				cursor.close();
			}
		}
		return -1;
	}

	private void showImage(Uri uri) {
		new DecodeImageTask(this, uri).execute();
	}

	private final OnPhotoTapListener mOnPhotoTapListener = new OnPhotoTapListener() {

		@Override
		public void onPhotoTap(View view, float x, float y) {
			switchImage();
		}

	};

	private class DecodeImageTask extends AsyncTask<Void, Void, Bitmap> {

		private Context mContext;
		private Uri mUri;

		public DecodeImageTask(Context context, Uri uri) {
			mContext = context;
			mUri = uri;
		}

		@Override
		protected void onPreExecute() {
			mPickButton.setEnabled(false);
			mPickButton.setText("Loading...");
			mPickButton.setVisibility(View.VISIBLE);
		}

		@Override
		protected Bitmap doInBackground(Void... args) {
			Bitmap bmp;
			try {
				bmp = decodeSampledBitmapFromUri(mContext, mUri,
						mOriginImageView.getWidth(),
						mOriginImageView.getHeight());
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			int orientation = getOrientation(mContext, mUri);
			if (orientation > 0) {
				Matrix matrix = new Matrix();
				matrix.postRotate(orientation);
				bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(),
						bmp.getHeight(), matrix, true);
			}
			return bmp;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			if (result == null) {
				Toast.makeText(getApplicationContext(),
						"Error: unable to load image", Toast.LENGTH_LONG)
						.show();
			} else {
				setOriginImageView(result);
				Bitmap copy = result.copy(result.getConfig(), true);
				new DehazeImageTask(copy).execute();
			}
		}

	}

	private class DehazeImageTask extends AsyncTask<Void, Void, Bitmap> {

		private Bitmap mBitmap;

		public DehazeImageTask(Bitmap bitmap) {
			mBitmap = bitmap;
		}

		@Override
		protected void onPreExecute() {
			mPickButton.setEnabled(false);
			mPickButton.setText("Dehazing...");
			mPickButton.setVisibility(View.VISIBLE);
		}

		@Override
		protected Bitmap doInBackground(Void... args) {
			ImageProcessX.AutoDehaze(mBitmap,
					ImageProcessX.GetHazeValue(mBitmap));
			return mBitmap;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			mPickButton.setVisibility(View.GONE);
			setResultImageView(result);
			switchImage();
		}

	}

	public static Bitmap decodeSampledBitmapFromUri(Context context, Uri uri,
			int reqWidth, int reqHeight) throws IOException {
		Log.v(TAG, "reqWidth: " + reqWidth + " reqHeight: " + reqHeight);
		ContentResolver cr = context.getContentResolver();

		final BitmapFactory.Options options = new BitmapFactory.Options();
		// First decode with inJustDecodeBounds=true to check dimensions
		options.inJustDecodeBounds = true;
		InputStream is = cr.openInputStream(uri);
		BitmapFactory.decodeStream(is, null, options);
		is.close();

		// Calculate inSampleSize
		options.inSampleSize = calculateInSampleSize(options, reqWidth,
				reqHeight);
		Log.v(TAG, "inSampleSize: " + options.inSampleSize);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		is = cr.openInputStream(uri);
		Bitmap res = BitmapFactory.decodeStream(is, null, options);
		is.close();
		return res;
	}

	public static int calculateInSampleSize(BitmapFactory.Options options,
			int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;
		Log.v(TAG, "width: " + width + " height: " + height);
		inSampleSize = Math.round(Math.max(width / (float) reqWidth, height
				/ (float) reqHeight));
		Log.v(TAG, "inSampleSize: " + inSampleSize);
		return inSampleSize;
	}
}
