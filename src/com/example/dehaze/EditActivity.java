package com.example.dehaze;

import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import easyimage.slrcamera.ImageProcessX;

public class EditActivity extends Activity implements OnClickListener {

	private static final int REQ_PICK_IMAGE = 1;

	private ImageView mImageView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_edit);

		mImageView = (ImageView) findViewById(R.id.image);

		findViewById(R.id.pick).setOnClickListener(this);
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
				Log.d("xxx", uri.toString());
				Bitmap bmp = decodeSampledBitmapFromUri(this, uri,
						mImageView.getWidth(), mImageView.getHeight());
				ImageProcessX.AutoDehaze(bmp, ImageProcessX.GetHazeValue(bmp));
				mImageView.setImageBitmap(bmp);
			}
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	public static Bitmap decodeSampledBitmapFromUri(Context context, Uri uri,
			int reqWidth, int reqHeight) {
		Log.v("xxx", "reqWidth: " + reqWidth + " reqHeight: " + reqHeight);
		ContentResolver cr = context.getContentResolver();
		final BitmapFactory.Options options = new BitmapFactory.Options();
		InputStream is = null;
		try {
			is = cr.openInputStream(uri);

			// First decode with inJustDecodeBounds=true to check dimensions
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(is, null, options);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// Calculate inSampleSize
		options.inSampleSize = calculateInSampleSize(options, reqWidth,
				reqHeight);

		Log.v("xxx", "inSampleSize: " + options.inSampleSize);
		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		try {
			is = cr.openInputStream(uri);
			return BitmapFactory.decodeStream(is, null, options);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static int calculateInSampleSize(BitmapFactory.Options options,
			int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;
		Log.v("xxx", "width: " + width + " height: " + height);
		if (height > reqHeight || width > reqWidth) {
			// Calculate the largest inSampleSize value that is a power of 2 and
			// keeps both
			// height and width larger than the requested height and width.
			while ((height / inSampleSize) > reqHeight
					&& (width / inSampleSize) > reqWidth) {
				inSampleSize *= 2;
			}
		}
		Log.v("xxx", "inSampleSize: " + inSampleSize);
		return inSampleSize;
	}

	@Override
	public void onClick(View view) {
		pickImage();
	}

}
