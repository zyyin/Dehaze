package com.example.dehaze;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.example.dehaze.FileStore.FileStoreException;

public class MainActivity extends Activity implements OnClickListener {

	private static final String TAG = MainActivity.class.getCanonicalName();

	private static final long AUTO_FOCUS_RETRY_INTERVAL = 1000L;

	private CameraPreview mPreview;
	private Button mCaptureButton;

	private MediaScannerConnection mConnection;
	private FileStore mFileStore;

	private Handler mHandler = new Handler();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.activity_main);

		mFileStore = FileStore.getInstance(this);

		// UI
		mPreview = new CameraPreview(this);
		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mPreview);

		// Events
		mCaptureButton = (Button) findViewById(R.id.button_capture);
		mCaptureButton.setOnClickListener(this);
		findViewById(R.id.button_gallery).setOnClickListener(this);
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.button_capture:
			takePicture();
			break;
		case R.id.button_gallery:
			showGallery(null);
			break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		mPreview.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mPreview.onPause();
	}

	private void takePicture() {
		mCaptureButton.setEnabled(false);
		mAutoFocusRunnable.run();
	}

	private void showGallery(Uri uri) {
		Intent intent = new Intent();
		intent.setType("image/*");
		intent.setAction(Intent.ACTION_VIEW);
		if (uri != null) {
			intent.setData(uri);
		}
		startActivity(intent);
	}

	private void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}

	private void scanFile(final String path) {
		mConnection = new MediaScannerConnection(this,
				new MediaScannerConnectionClient() {
					@Override
					public void onMediaScannerConnected() {
						Log.d(TAG, "scanning " + path);
						try {
							mConnection.scanFile(path, null);
						} catch (Exception e) {
							Log.e(TAG, e.getMessage());
						}
					}

					@Override
					public void onScanCompleted(String path, Uri uri) {
						Log.d(TAG, "scan complete for " + path + " uri: " + uri);
						mConnection.disconnect();
					}
				});
		mConnection.connect();
	}

	private AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback() {
		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			if (success) {
				camera.takePicture(null, null, mTakePictureCallback);
				camera.cancelAutoFocus();
			} else {
				mHandler.postDelayed(mAutoFocusRunnable,
						AUTO_FOCUS_RETRY_INTERVAL);
			}
		}
	};

	private Runnable mAutoFocusRunnable = new Runnable() {
		@Override
		public void run() {
			Camera camera = mPreview.getCamera();
			if (camera != null) {
				camera.autoFocus(mAutoFocusCallback);
			}
		}
	};

	private PictureCallback mTakePictureCallback = new PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			mCaptureButton.setEnabled(true);
			mFileStore.saveMediaFile(data, mSavePictureCallback);
		}
	};

	private FileStore.Callback mSavePictureCallback = new FileStore.Callback() {
		@Override
		public void onSuccess(byte[] data, String path) {
			showToast("Saved to " + path);
			scanFile(path);
		}

		@Override
		public void onError(FileStoreException e) {
			showToast("Error: " + e.getMessage());
		}
	};

}
