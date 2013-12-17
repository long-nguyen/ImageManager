/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kayac.bitmaputils.lib;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;

import com.kayac.bitmaputils.BuildConfig;
import com.kayac.bitmaputils.test.Utils;

/**
 * This class wraps up completing some arbitrary long running work when loading
 * a bitmap to an ImageView. It handles things like using a memory and disk
 * cache, running the work in a background thread and setting a placeholder
 * image.
 */
public  class ImageWorker {
	public static final String TAG = "ImageWorker";
	public static final boolean DEBUG=true;
	
	private static final int FADE_IN_TIME = 200;
	private static final int HTTP_CACHE_SIZE = 10 * 1024 * 1024; // 10MB
	private static final String HTTP_CACHE_DIR = "http";
	private static final int IO_BUFFER_SIZE = 8 * 1024;
	private DiskLruCache mHttpDiskCache;
	private File mHttpCacheDir;
	private boolean mHttpDiskCacheStarting = true;
	private final Object mHttpDiskCacheLock = new Object();
	private static final int DISK_CACHE_INDEX = 0;

	private ImageCache mImageCache;
	private ImageCache.ImageCacheParams mImageCacheParams;
	private Bitmap mLoadingBitmap;
	private boolean mFadeInBitmap = true;
	private boolean mExitTasksEarly = false;
	protected boolean mPauseWork = false;
	private final Object mPauseWorkLock = new Object();

	protected Resources mResources;

	private static final int MESSAGE_CLEAR = 0;
	private static final int MESSAGE_INIT_DISK_CACHE = 1;
	private static final int MESSAGE_FLUSH = 2;
	private static final int MESSAGE_CLOSE = 3;

	public ImageWorker(Context context) {
		mResources = context.getResources();
		ImageUtils.checkConnection(context);
		mHttpCacheDir = ImageCache.getDiskCacheDir(context, HTTP_CACHE_DIR);
	}

	

	/**
	 * Load an image specified by the data parameter into an ImageView (override
	 * {@link ImageWorker#decodeSampledBitmapFromNetwork(Object)} to define the processing
	 * logic). A memory and disk cache will be used if an {@link ImageCache} has
	 * been added using
	 * {@link ImageWorker#addImageCache(FragmentManager, ImageCache.ImageCacheParams)}
	 * . If the image is found in the memory cache, it is set immediately,
	 * otherwise an {@link AsyncTask} will be created to asynchronously load the
	 * bitmap.
	 * 
	 * @param data
	 *            The LoadRequest of the image to download.
	 * @param imageView
	 *            The ImageView to bind the downloaded image to.
	 */
	public void loadImage(LoadRequest data, ImageView imageView) {
		if (data == null) {
			return;
		}

		BitmapDrawable value = null;
		if(ImageWorker.DEBUG) Log.d(ImageWorker.TAG,"Loading bitmap for:"+data.key);
		if (mImageCache != null) {
			value = mImageCache.getBitmapFromMemCache(String.valueOf(data.key));
		}

		if (value != null) {
			// Bitmap found in memory cache
			if(ImageWorker.DEBUG) Log.d(ImageWorker.TAG,"Having bitmap in mem ");
			imageView.setImageDrawable(value);
		} else if (cancelPotentialWork(data, imageView)) {
			final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
			final AsyncDrawable asyncDrawable = new AsyncDrawable(mResources, mLoadingBitmap, task);
			imageView.setImageDrawable(asyncDrawable);

			// NOTE: This uses a custom version of AsyncTask that has been
			// pulled from the
			// framework and slightly modified. Refer to the docs at the top of
			// the class
			// for more info on what was changed.
			task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR, data);
		}
	}

	
	
	/**
	 * Set placeholder bitmap that shows when the the background thread is
	 * running.
	 * 
	 * @param bitmap
	 */
	public void setLoadingImage(Bitmap bitmap) {
		mLoadingBitmap = bitmap;
	}

	/**
	 * Set placeholder bitmap that shows when the the background thread is
	 * running.
	 * 
	 * @param resId
	 */
	public void setLoadingImage(int resId) {
		mLoadingBitmap = BitmapFactory.decodeResource(mResources, resId);
	}

	/**
	 * Adds an {@link ImageCache} to this {@link ImageWorker} to handle disk and
	 * memory bitmap caching.
	 * 
	 * @param fragmentManager
	 * @param cacheParams
	 *            The cache parameters to use for the image cache.
	 */
	public void addImageCache(FragmentManager fragmentManager, ImageCache.ImageCacheParams cacheParams) {
		mImageCacheParams = cacheParams;
		mImageCache = ImageCache.getInstance(fragmentManager, mImageCacheParams);
		new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
	}

	/**
	 * Adds an {@link ImageCache} to this {@link ImageWorker} to handle disk and
	 * memory bitmap caching.
	 * 
	 * @param activity
	 * @param diskCacheDirectoryName
	 *            See
	 *            {@link ImageCache.ImageCacheParams#ImageCacheParams(Context, String)}
	 *            .
	 */
	public void addImageCache(FragmentActivity activity, String diskCacheDirectoryName) {
		mImageCacheParams = new ImageCache.ImageCacheParams(activity, diskCacheDirectoryName);
		mImageCache = ImageCache.getInstance(activity.getSupportFragmentManager(), mImageCacheParams);
		new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
	}

	/**
	 * If set to true, the image will fade-in once it has been loaded by the
	 * background thread.
	 */
	public void setImageFadeIn(boolean fadeIn) {
		mFadeInBitmap = fadeIn;
	}

	public void setExitTasksEarly(boolean exitTasksEarly) {
		mExitTasksEarly = exitTasksEarly;
		setPauseWork(false);
	}


	/**
	 * @return The {@link ImageCache} object currently being used by this
	 *         ImageWorker.
	 */
	protected ImageCache getImageCache() {
		return mImageCache;
	}
	
	/**
	 * Return Memory cache for custom data load, shared
	 * @return
	 */
	public LruCache<String, BitmapDrawable> getSharedLruCache(){
		if(mImageCache!=null&&mImageCache.getMemoryCache()!=null) return mImageCache.getMemoryCache();
		return null;
	}
	/**
	 * Cancels any pending work attached to the provided ImageView.
	 * 
	 * @param imageView
	 */
	public static void cancelWork(ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
		if (bitmapWorkerTask != null) {
			bitmapWorkerTask.cancel(true);
			if (BuildConfig.DEBUG) {
				final Object bitmapData = bitmapWorkerTask.data;
				Log.d(TAG, "cancelWork - cancelled work for " + bitmapData);
			}
		}
	}

	/**
	 * Returns true if the current work has been canceled or if there was no
	 * work in progress on this image view. Returns false if the work in
	 * progress deals with the same data. The work is not stopped in that case.
	 */
	public static boolean cancelPotentialWork(LoadRequest data, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final Object bitmapData = bitmapWorkerTask.data;
			if (bitmapData == null || !bitmapData.equals(data)) {
				bitmapWorkerTask.cancel(true);
				if (BuildConfig.DEBUG) {
					Log.d(TAG, "cancelPotentialWork - cancelled work for " + data);
				}
			} else {
				// The same work is already in progress.
				return false;
			}
		}
		return true;
	}

	/**
	 * @param imageView
	 *            Any imageView
	 * @return Retrieve the currently active work task (if any) associated with
	 *         this imageView. null if there is no such task.
	 */
	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	/**
	 * The actual AsyncTask that will asynchronously process the image.
	 */
	private class BitmapWorkerTask extends AsyncTask<LoadRequest, Void, BitmapDrawable> {
		private LoadRequest data;
		private final WeakReference<ImageView> imageViewReference;

		public BitmapWorkerTask(ImageView imageView) {
			imageViewReference = new WeakReference<ImageView>(imageView);
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			ImageView imageView = getAttachedImageView();
			if (imageView != null && imageView instanceof RecyclingImageView) {
				((RecyclingImageView) imageView).onLoadStarted();
			}
		}

		/**
		 * Background processing.
		 */
		@Override
		protected BitmapDrawable doInBackground(LoadRequest... params) {
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "doInBackground - starting work");
			}

			data = params[0];
			final int loadType = data.type;
			String key = data.key;
			Bitmap bitmap = null;
			BitmapDrawable drawable = null;

			// Wait here if work is paused and the task is not cancelled
			synchronized (mPauseWorkLock) {
				while (mPauseWork && !isCancelled()) {
					try {
						mPauseWorkLock.wait();
					} catch (InterruptedException e) {
					}
				}
			}

			// If the image cache is available and this task has not been
			// cancelled by another
			// thread and the ImageView that was originally bound to this task
			// is still bound back
			// to this task and our "exit early" flag is not set then try and
			// fetch the bitmap from
			// the cache
			// Long: Because we already search for mem cache, it is unavaiable,
			// so we search for diskcache here
			if (mImageCache != null && !isCancelled() && getAttachedImageView() != null && !mExitTasksEarly
					&& loadType == LoadRequest.TYPE_REMOTE_PATH) {
				
				bitmap = mImageCache.getBitmapFromDiskCache(key);
				if (DEBUG) {
					Log.d(TAG, "doInBackground - Load from disk cache, result is :"+bitmap!=null?"ok":"null");
				}
			}

			// If the bitmap was not found in the cache and this task has not
			// been cancelled by
			// another thread and the ImageView that was originally bound to
			// this task is still
			// bound back to this task and our "exit early" flag is not set,
			// then call the main
			// process method (as implemented by a subclass)
			if (bitmap == null && !isCancelled() && getAttachedImageView() != null && !mExitTasksEarly) {
				if(ImageWorker.DEBUG) Log.d(ImageWorker.TAG,"Processing bitmap: "+data.key);
				bitmap = processBitmap(data);
				if(bitmap!=null){
					if(ImageWorker.DEBUG) Log.d(ImageWorker.TAG,"Size after: "+bitmap.getWidth()+":"+bitmap.getHeight());
				}
			}

			// If the bitmap was processed and the image cache is available,
			// then add the processed
			// bitmap to the cache for future use. Note we don't check if the
			// task was cancelled
			// here, if it was, and the thread is still running, we may as well
			// add the processed
			// bitmap to our cache as it might be used again in the future
			if (bitmap != null) {
				if (Utils.hasHoneycomb()) {
					// Running on Honeycomb or newer, so wrap in a standard
					// BitmapDrawable
					drawable = new BitmapDrawable(mResources, bitmap);
				} else {
					// Running on Gingerbread or older, so wrap in a
					// RecyclingBitmapDrawable
					// which will recycle automagically
					drawable = new RecyclingBitmapDrawable(mResources, bitmap);
				}

				if (mImageCache != null) {
					mImageCache.addBitmapToCache(data, drawable);
				}
			}

			if (BuildConfig.DEBUG) {
				Log.d(TAG, "doInBackground - finished work");
			}

			return drawable;
		}

		/**
		 * Once the image is processed, associates it to the imageView
		 */
		@Override
		protected void onPostExecute(BitmapDrawable value) {
			// if cancel was called on this task or the "exit early" flag is set
			// then we're done
			if (isCancelled() || mExitTasksEarly) {
				value = null;
			}

			final ImageView imageView = getAttachedImageView();
			if (imageView != null) {
				if (imageView instanceof RecyclingImageView) {
					((RecyclingImageView) imageView).onLoadFinished();
				}
				if (value == null) {
					if (imageView instanceof RecyclingImageView) {
						((RecyclingImageView) imageView).onFailingLoadBitmap();
					}
				} else {
					if (BuildConfig.DEBUG) {
						Log.d(TAG, "onPostExecute - setting bitmap");
					}
					setImageDrawable(imageView, value);
				}
			}
		}

		@Override
		protected void onCancelled(BitmapDrawable value) {
			super.onCancelled(value);
			synchronized (mPauseWorkLock) {
				mPauseWorkLock.notifyAll();
			}
		}

		/**
		 * Returns the ImageView associated with this task as long as the
		 * ImageView's task still points to this task as well. Returns null
		 * otherwise.
		 */
		private ImageView getAttachedImageView() {
			final ImageView imageView = imageViewReference.get();
			final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

			if (this == bitmapWorkerTask) {
				return imageView;
			}

			return null;
		}
	}
	
	
	/**
	 * Depends on request type, choose the right bitmap process methods
	 * @param loadRequest
	 * @return
	 */
	private Bitmap processBitmap(LoadRequest request) {
		switch (request.type) {
		case LoadRequest.TYPE_LOCAL_PATH:
			return ImageUtils.decodeSampledBitmapFromFile(request.key, request.imgW, request.imgH, mImageCache);
		case LoadRequest.TYPE_REMOTE_PATH:
			return decodeSampledBitmapFromNetwork(request.key,request.imgW,request.imgH);
		case LoadRequest.TYPE_LOCAL_RES:
			//TODO:Why inbitmap does not work here
			return ImageUtils.decodeSampledBitmapFromResource(mResources, Integer.parseInt(request.key), request.imgW, request.imgH,null);
		default:
			break;
		}
		return null;
	}



	
	/**
     * The main network process method, which will be called by the ImageWorker in the AsyncTask background
     * thread.
     *
     * @param data The data to load the bitmap, in this case, a regular http URL
     * @return The downloaded and resized bitmap
     */
    private Bitmap decodeSampledBitmapFromNetwork(String url,int reqW,int reqH) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "processBitmap - " + url);
        }

        final String key = ImageCache.hashKeyForDisk(url);
        FileDescriptor fileDescriptor = null;
        FileInputStream fileInputStream = null;
        DiskLruCache.Snapshot snapshot;
        synchronized (mHttpDiskCacheLock) {
            // Wait for disk cache to initialize
            while (mHttpDiskCacheStarting) {
                try {
                    mHttpDiskCacheLock.wait();
                } catch (InterruptedException e) {}
            }

            if (mHttpDiskCache != null) {
                try {
                    snapshot = mHttpDiskCache.get(key);
                    if (snapshot == null) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "processBitmap, not found in http cache, downloading...");
                        }
                        DiskLruCache.Editor editor = mHttpDiskCache.edit(key);
                        if (editor != null) {
                            if (ImageUtils.downloadUrlToStream(url,
                                    editor.newOutputStream(DISK_CACHE_INDEX),IO_BUFFER_SIZE)) {
                                editor.commit();
                            } else {
                                editor.abort();
                            }
                        }
                        snapshot = mHttpDiskCache.get(key);
                    }
                    if (snapshot != null) {
                        fileInputStream =
                                (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
                        fileDescriptor = fileInputStream.getFD();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "processBitmap - " + e);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "processBitmap - " + e);
                } finally {
                    if (fileDescriptor == null && fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (IOException e) {}
                    }
                }
            }
        }

        Bitmap bitmap = null;
        if (fileDescriptor != null) {
            bitmap = ImageUtils.decodeSampledBitmapFromDescriptor(fileDescriptor, reqW,
                    reqH, getImageCache());
        }
        if (fileInputStream != null) {
            try {
                fileInputStream.close();
            } catch (IOException e) {}
        }
        return bitmap;
    }

	/**
	 * A custom Drawable that will be attached to the imageView while the work
	 * is in progress. Contains a reference to the actual worker task, so that
	 * it can be stopped if a new binding is required, and makes sure that only
	 * the last started worker process can bind its result, independently of the
	 * finish order.
	 */
	private static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

		public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
			super(res, bitmap);
			bitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
		}

		public BitmapWorkerTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}

	/**
	 * Called when the processing is complete and the final drawable should be
	 * set on the ImageView.
	 * 
	 * @param imageView
	 * @param drawable
	 */
	private void setImageDrawable(ImageView imageView, Drawable drawable) {
		if (mFadeInBitmap) {
			// Transition drawable with a transparent drawable and the final
			// drawable
			final TransitionDrawable td = new TransitionDrawable(new Drawable[] {
					new BitmapDrawable(mResources,mLoadingBitmap), drawable });
			imageView.setImageDrawable(td);
			td.startTransition(FADE_IN_TIME);
		} else {
			imageView.setImageDrawable(drawable);
		}
	}

	

	/**
	 * Pause any ongoing background work. This can be used as a temporary
	 * measure to improve performance. For example background work could be
	 * paused when a ListView or GridView is being scrolled using a
	 * {@link android.widget.AbsListView.OnScrollListener} to keep scrolling
	 * smooth.
	 * <p>
	 * If work is paused, be sure setPauseWork(false) is called again before
	 * your fragment or activity is destroyed (for example during
	 * {@link android.app.Activity#onPause()}), or there is a risk the
	 * background thread will never finish.
	 */
	public void setPauseWork(boolean pauseWork) {
		synchronized (mPauseWorkLock) {
			mPauseWork = pauseWork;
			if (!mPauseWork) {
				mPauseWorkLock.notifyAll();
			}
		}
	}

	protected class CacheAsyncTask extends AsyncTask<Object, Void, Void> {

		@Override
		protected Void doInBackground(Object... params) {
			switch ((Integer) params[0]) {
			case MESSAGE_CLEAR:
				clearCacheInternal();
				break;
			case MESSAGE_INIT_DISK_CACHE:
				initDiskCacheInternal();
				break;
			case MESSAGE_FLUSH:
				flushCacheInternal();
				break;
			case MESSAGE_CLOSE:
				closeCacheInternal();
				break;
			}
			return null;
		}
	}

	protected void initDiskCacheInternal() {
		if (mImageCache != null) {
			mImageCache.initDiskCache();
		}
		initHttpDiskCache();
	}

	private void initHttpDiskCache() {
		if (!mHttpCacheDir.exists()) {
			mHttpCacheDir.mkdirs();
		}
		synchronized (mHttpDiskCacheLock) {
			if (ImageCache.getUsableSpace(mHttpCacheDir) > HTTP_CACHE_SIZE) {
				try {
					mHttpDiskCache = DiskLruCache.open(mHttpCacheDir, 1, 1, HTTP_CACHE_SIZE);
					if (BuildConfig.DEBUG) {
						Log.d(TAG, "HTTP cache initialized");
					}
				} catch (IOException e) {
					mHttpDiskCache = null;
				}
			}
			mHttpDiskCacheStarting = false;
			mHttpDiskCacheLock.notifyAll();
		}
	}
	protected void clearCacheInternal() {
		if (mImageCache != null) {
			mImageCache.clearCache();
		}
		synchronized (mHttpDiskCacheLock) {
			if (mHttpDiskCache != null && !mHttpDiskCache.isClosed()) {
				try {
					mHttpDiskCache.delete();
					if (BuildConfig.DEBUG) {
						Log.d(TAG, "HTTP cache cleared");
					}
				} catch (IOException e) {
					Log.e(TAG, "clearCacheInternal - " + e);
				}
				mHttpDiskCache = null;
				mHttpDiskCacheStarting = true;
				initHttpDiskCache();
			}
		}
	}

	protected void flushCacheInternal() {
		if (mImageCache != null) {
			mImageCache.flush();
		}
		synchronized (mHttpDiskCacheLock) {
			if (mHttpDiskCache != null) {
				try {
					mHttpDiskCache.flush();
					if (BuildConfig.DEBUG) {
						Log.d(TAG, "HTTP cache flushed");
					}
				} catch (IOException e) {
					Log.e(TAG, "flush - " + e);
				}
			}
		}
	}

	protected void closeCacheInternal() {
		if (mImageCache != null) {
			mImageCache.close();
			mImageCache = null;
		}
		synchronized (mHttpDiskCacheLock) {
			if (mHttpDiskCache != null) {
				try {
					if (!mHttpDiskCache.isClosed()) {
						mHttpDiskCache.close();
						mHttpDiskCache = null;
						if (BuildConfig.DEBUG) {
							Log.d(TAG, "HTTP cache closed");
						}
					}
				} catch (IOException e) {
					Log.e(TAG, "closeCacheInternal - " + e);
				}
			}
		}
	}

	public void clearCache() {
		new CacheAsyncTask().execute(MESSAGE_CLEAR);
	}

	public void flushCache() {
		new CacheAsyncTask().execute(MESSAGE_FLUSH);
	}

	public void closeCache() {
		new CacheAsyncTask().execute(MESSAGE_CLOSE);
	}
	
}
