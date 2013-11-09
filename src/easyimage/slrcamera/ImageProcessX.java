package easyimage.slrcamera;
import android.graphics.Bitmap;

public class ImageProcessX {

static {
        System.loadLibrary("image_processX");
    }
    public static native void AutoDehaze(Bitmap  bitmap, float ratio);
    public static native float GetHazeValue(Bitmap  bitmap);
}
