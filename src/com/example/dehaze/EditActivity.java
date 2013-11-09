package com.example.dehaze;

import java.io.IOException;
import java.io.InputStream;

import uk.co.senab.photoview.PhotoViewAttacher;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
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
import easyimage.slrcamera.ImageProcessX;

public class EditActivity extends Activity implements OnClickListener {

	private static final String TAG = EditActivity.class.getCanonicalName();

	private static final int REQ_PICK_IMAGE = 1;

	private Button mPickButton;
	private ImageView mImageView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_edit);

		mImageView = (ImageView) findViewById(R.id.image);
		mPickButton = (Button) findViewById(R.id.pick);

		mPickButton.setOnClickListener(this);
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

	private void pickImage() {
		Intent intent = new Intent();
		intent.setType("image/*");
		intent.setAction(Intent.ACTION_GET_CONTENT);
		startActivityForResult(Intent.createChooser(intent, "Select Picture"),
				REQ_PICK_IMAGE);
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

	private PhotoViewAttacher mAttacher;

	private void showImage(Uri uri) {
		Bitmap bmp = decodeSampledBitmapFromUri(this, uri,
				mImageView.getWidth(), mImageView.getHeight());
		int orientation = getOrientation(this, uri);
		if (orientation > 0) {
			Matrix matrix = new Matrix();
			matrix.postRotate(orientation);
			bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(),
					bmp.getHeight(), matrix, true);
		}
		ImageProcessX.AutoDehaze(bmp, ImageProcessX.GetHazeValue(bmp));
		mImageView.setImageBitmap(bmp);
		mPickButton.setVisibility(View.GONE);
		mAttacher = new PhotoViewAttacher(mImageView);
	}

	public static Bitmap decodeSampledBitmapFromUri(Context context, Uri uri,
			int reqWidth, int reqHeight) {
		Log.v(TAG, "reqWidth: " + reqWidth + " reqHeight: " + reqHeight);
		ContentResolver cr = context.getContentResolver();

		final BitmapFactory.Options options = new BitmapFactory.Options();
		// First decode with inJustDecodeBounds=true to check dimensions
		options.inJustDecodeBounds = true;
		try {
			InputStream is = cr.openInputStream(uri);
			try {
				BitmapFactory.decodeStream(is, null, options);
			} finally {
				is.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		// Calculate inSampleSize
		options.inSampleSize = calculateInSampleSize(options, reqWidth,
				reqHeight);
		Log.v(TAG, "inSampleSize: " + options.inSampleSize);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		try {
			InputStream is = cr.openInputStream(uri);
			try {
				return BitmapFactory.decodeStream(is, null, options);
			} finally {
				is.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static int calculateInSampleSize(BitmapFactory.Options options,
			int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;
		Log.v(TAG, "width: " + width + " height: " + height);
		while ((height / inSampleSize) > reqHeight
				&& (width / inSampleSize) > reqWidth) {
			inSampleSize *= 2;
		}
		Log.v(TAG, "inSampleSize: " + inSampleSize);
		return inSampleSize;
	}

	@Override
	public void onClick(View view) {
		pickImage();
	}

}
