package com.example.dehaze;

import java.io.IOException;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraPreview extends SurfaceView implements
		SurfaceHolder.Callback {

	private static final String TAG = CameraPreview.class.getCanonicalName();

	private Context mContext;
	private SurfaceHolder mHolder;
	private Camera mCamera;
	private boolean mSurfaceAvailable;

	private OrientationEventListener mOrientation;
	private int mRotation;

	public CameraPreview(Context context) {
		super(context);

		mContext = context;
		mHolder = getHolder();
		mHolder.addCallback(this);
		mOrientation = new OrientationListener(context);
	}

	public Camera getCamera() {
		return mCamera;
	}

	public void onResume() {
		mOrientation.enable();
		openCamera();
	}

	public void onPause() {
		mOrientation.disable();
		stopPreview();
		closeCamera();
	}

	private void openCamera() {
		try {
			mCamera = Camera.open();
		} catch (Exception e) {
			Log.d(TAG, "Error opening camera: " + e.getMessage());
		}
	}

	private void closeCamera() {
		if (mCamera == null) {
			return;
		}
		mCamera.release();
		mCamera = null;
	}

	private void startPreview() {
		if (mCamera == null) {
			return;
		}
		try {
			mCamera.setPreviewDisplay(mHolder);
			mCamera.startPreview();
		} catch (IOException e) {
			Log.d(TAG, "Error starting camera preview: " + e.getMessage());
		}
	}

	private void stopPreview() {
		if (mCamera == null) {
			return;
		}
		mCamera.stopPreview();
	}

	private void setFocusMode(Camera.Parameters params) {
		Log.d(TAG, "setting focus mode");
		for (String focusMode : params.getSupportedFocusModes()) {
			if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
					.equals(focusMode)) {
				params.setFocusMode(focusMode);
				return;
			}
		}
		// TODO: handle manual auto focus
	}

	private void setupCamera(int width, int height) {
		if (mCamera == null) {
			return;
		}

		Camera.Parameters params = mCamera.getParameters();

		Size previewSize = params.getPreviewSize();
		Log.d(TAG, "preview size: " + previewSize.width + " "
				+ previewSize.height);

		setFocusMode(params);

		mCamera.setParameters(params);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		Log.d(TAG, "surface created");

		mSurfaceAvailable = true;
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.d(TAG, "surface destroyed");

		mSurfaceAvailable = false;
		stopPreview();
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		Log.d(TAG, "surface changed, format: " + format + " width: " + w
				+ " height: " + h);

		if (!mSurfaceAvailable) {
			return;
		}

		stopPreview();
		setupCamera(w, h);
		startPreview();
	}

	private void rotationChanged(int rotation) {
		Log.d(TAG, "rotation: " + rotation);
		Camera.Parameters params = mCamera.getParameters();
		params.setRotation(rotation);
		mCamera.setParameters(params);
	}

	private void updateRotation(int rotation) {
		if (mRotation != rotation) {
			mRotation = rotation;
			rotationChanged(rotation);
		}
	}

	private class OrientationListener extends OrientationEventListener {

		public OrientationListener(Context context) {
			super(context, SensorManager.SENSOR_DELAY_UI);
		}

		@Override
		public void onOrientationChanged(int orientation) {
			if (orientation == ORIENTATION_UNKNOWN) {
				return;
			}
			Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
			Camera.getCameraInfo(CameraInfo.CAMERA_FACING_BACK, info);
			orientation = (orientation + 45) / 90 * 90;
			int rotation = 0;
			if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
				rotation = (info.orientation - orientation + 360) % 360;
			} else { // back-facing camera
				rotation = (info.orientation + orientation) % 360;
			}
			updateRotation(rotation);
		}

	}

}
