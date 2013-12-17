package com.kayac.bitmaputils.lib;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;
import android.widget.Toast;

import com.kayac.bitmaputils.BuildConfig;
import com.kayac.bitmaputils.R;
import com.kayac.bitmaputils.test.Utils;

public class ImageUtils {

	
	private static final String TAG = ImageUtils.class.getSimpleName();

	/**
     * Decode and sample down a bitmap from resources to the requested width and height.
     *
     * @param res The resources object containing the image data
     * @param resId The resource id of the image data
     * @param reqWidth The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @param cache The ImageCache used to find candidate bitmaps for use with inBitmap
     * @return A bitmap sampled down from the original with the same aspect ratio and dimensions
     *         that are equal to or greater than the requested width and height
     */
    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId,
            int reqWidth, int reqHeight, ImageCache cache) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // If we're running on Honeycomb or newer, try to use inBitmap
        if (Utils.hasHoneycomb()&&cache!=null) {
            addInBitmapOptions(options, cache);
        }

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    /**
     * Decode and sample down a bitmap from a file to the requested width and height.
     *
     * @param filename The full path of the file to decode
     * @param reqWidth The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @param cache The ImageCache used to find candidate bitmaps for use with inBitmap
     * @return A bitmap sampled down from the original with the same aspect ratio and dimensions
     *         that are equal to or greater than the requested width and height
     */
    public static Bitmap decodeSampledBitmapFromFile(String filename,
            int reqWidth, int reqHeight, ImageCache cache) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filename, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // If we're running on Honeycomb or newer, try to use inBitmap
        if (Utils.hasHoneycomb()) {
            addInBitmapOptions(options, cache);
        }

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filename, options);
    }

    /**
     * Decode and sample down a bitmap from a file input stream to the requested width and height.
     *
     * @param fileDescriptor The file descriptor to read from
     * @param reqWidth The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @param cache The ImageCache used to find candidate bitmaps for use with inBitmap
     * @return A bitmap sampled down from the original with the same aspect ratio and dimensions
     *         that are equal to or greater than the requested width and height
     */
    public static Bitmap decodeSampledBitmapFromDescriptor(
            FileDescriptor fileDescriptor, int reqWidth, int reqHeight, ImageCache cache) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        // If we're running on Honeycomb or newer, try to use inBitmap
        if (Utils.hasHoneycomb()) {
            addInBitmapOptions(options, cache);
        }

        return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void addInBitmapOptions(BitmapFactory.Options options, ImageCache cache) {
        // inBitmap only works with mutable bitmaps so force the decoder to
        // return mutable bitmaps.
    		options.inMutable = true;

            if (cache != null) {
                // Try and find a bitmap to use for inBitmap
                Bitmap inBitmap = cache.getBitmapFromReusableSet(options);

                if (inBitmap != null) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Found bitmap to use for inBitmap");
                    }
                    options.inBitmap = inBitmap;
                }
            }
    }

    /**
     * Calculate an inSampleSize for use in a {@link BitmapFactory.Options} object when decoding
     * bitmaps using the decode* methods from {@link BitmapFactory}. This implementation calculates
     * the closest inSampleSize that will result in the final decoded bitmap having a width and
     * height equal to or larger than the requested width and height. This implementation does not
     * ensure a power of 2 is returned for inSampleSize which can be faster when decoding but
     * results in a larger bitmap which isn't as useful for caching purposes.
     *
     * @param options An options object with out* params already populated (run through a decode*
     *            method with inJustDecodeBounds==true
     * @param reqWidth The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return The value to be used for inSampleSize
     */
    public static int calculateInSampleSize(BitmapFactory.Options options,
            int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if ((height > reqHeight || width > reqWidth)&&(reqWidth>0&&reqHeight>0)) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee a final image
            // with both dimensions larger than or equal to the requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).

            final float totalPixels = width * height;

            // Anything more than 2x the requested pixels we'll sample down further
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++;
            }
        }
        return inSampleSize;
    }
    public static Bitmap rotateBitmap(String fileName,Bitmap in){
		if(in==null) return null;
		//Rotate bitmap based on orientation
		Matrix matrix = new Matrix();
		matrix.postRotate(getBitmapOrientation(fileName));
		in = Bitmap.createBitmap(in, 0, 0, in.getWidth(), in.getHeight(), matrix, true);
		return in;
	}
    
    /**
     *  Rotates the bitmap. If a new bitmap is created, the
     * @param b: bitmap
     * @param degrees
     * @return
     */
    // original bitmap is recycled.
    public static Bitmap rotateBitmap(Bitmap b, int degrees) {
        if (degrees != 0  && b != null) {
            Matrix m = new Matrix();
            if (degrees != 0) {
                // clockwise
                m.postRotate(degrees,
                        (float) b.getWidth() / 2, (float) b.getHeight() / 2);
            }
            try {
                Bitmap b2 = Bitmap.createBitmap(
                        b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (b != b2) {
                    b.recycle();
                    b = b2;
                }
            } catch (OutOfMemoryError ex) {
            	ex.printStackTrace();
            }
        }
        return b;
    }
	
	/**
	 * For getting bitmap orientation by calling ExifInterface
	 * @param fileName
	 * @return
	 */
	public static int getBitmapOrientation(String fileName){
		int ori=0;
		try{
			ExifInterface exifReader = new ExifInterface(fileName);
			int orientation = exifReader.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1);
			if (orientation == ExifInterface.ORIENTATION_NORMAL) {
			} else if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
				ori=90;
			} else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
				ori=180;
			} else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
				ori=270;
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return ori;
	}

	/**
 	 * Getting bitmap orientation from gallery
 	 * @param imgId
 	 * @return
 	 */
 	public static int getBitmapOrientation(Context c, Long imgId) {
 		ContentResolver cr = c.getContentResolver();
 		String[] projection = { BaseColumns._ID, ImageColumns.ORIENTATION };
 		String[] vals = { "" + imgId };
 		Cursor cursor = null;
 		try {
 			cursor = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, BaseColumns._ID + "=?", vals, null);
 			if (cursor.moveToFirst()) {
 				int orientation = cursor.getInt(cursor.getColumnIndexOrThrow(ImageColumns.ORIENTATION));
 				return orientation;
 			}
 		} finally {
 			if (null != cursor) {
 				cursor.close();
 			}
 		}
 		return 0;
 	}

    /**
     * Download a bitmap from a URL and write the content to an output stream.
     *
     * @param urlString The URL to fetch
     * @return true if successful, false otherwise
     */
    public static boolean downloadUrlToStream(String urlString, OutputStream outputStream,int ioBufferSize) {
        disableConnectionReuseIfNecessary();
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;

        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), ioBufferSize);
            out = new BufferedOutputStream(outputStream, ioBufferSize);

            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (final IOException e) {
            Log.e(TAG, "Error in downloadBitmap - " + e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {}
        }
        return false;
    }

    /**
     * Workaround for bug pre-Froyo, see here for more info:
     * http://android-developers.blogspot.com/2011/09/androids-http-clients.html
     */
    public static void disableConnectionReuseIfNecessary() {
        // HTTP connection reuse which was buggy pre-froyo
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
            System.setProperty("http.keepAlive", "false");
        }
    }
    
    /**
     * Simple network connection check.
     *
     * @param context
     */
     public static void checkConnection(Context context) {
         final ConnectivityManager cm =
                 (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
         final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
         if (networkInfo == null || !networkInfo.isConnectedOrConnecting()) {
             Toast.makeText(context, R.string.no_network_connection_toast, Toast.LENGTH_LONG).show();
             Log.e(TAG, "checkConnection - no connection found");
         }
     }
     
     //------------------------------Gallery
     /**
      * Get thumbnail
      * @param imageId :must have
      * @param size_kind:optional(default is MINI_KIND)
      * @param prefersize: optional(-1)
      * @param preferOrientation:optional(-1)
      * @return
      */
     public static Bitmap decodeSampleGalleryThumbnail(Context c,long imageId,int size_kind,int preferSize,int preferOrientation){
    	BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inPreferredConfig = Bitmap.Config.RGB_565;
		opts.inInputShareable = true;
		opts.inPurgeable = true;
		opts.inSampleSize =(preferSize==-1)?1: (384 / preferSize);	//MINI_KIND=512x384
		Bitmap bmp= MediaStore.Images.Thumbnails.getThumbnail(c.getContentResolver(), imageId,
				(size_kind==-1)?MediaStore.Images.Thumbnails.MINI_KIND:size_kind, opts);
		if (null != bmp) {
			bmp = rotateBitmap(bmp, (preferOrientation==-1)?0:preferOrientation);
		}
		return bmp;
     }
     
	/**
	 * Get size of image specified by image request.
	 * 
	 * @param req
	 *            image request.
	 * @return image dimensions.
	 */
	public static Point getImageSize(Resources res,final LoadRequest req) {
		final Options opts = new Options();
		opts.inJustDecodeBounds = true;
		if (req.type ==LoadRequest.TYPE_LOCAL_PATH) {
			BitmapFactory.decodeFile(req.key, opts);
		}
		if (req.type ==LoadRequest.TYPE_LOCAL_RES) {
			BitmapFactory.decodeResource(res, Integer.parseInt(req.key), opts);
		}
		if (req.type == LoadRequest.TYPE_REMOTE_PATH) {
			try {
				final InputStream is = new URL(req.key).openStream();
				BitmapFactory.decodeStream(is, null, opts);
				is.close();
			} catch (final Exception e) {
				// nothing
			}
		}
		return new Point(opts.outWidth, opts.outHeight);
	}
 	
     
}
