/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

/**
 * Sub-class of ImageView which automatically notifies the drawable when it is
 * being displayed.
 */
public class RecyclingImageView extends ImageView {

	private LoadRequest mPendingRequest;
	private ImageWorker mImageWorker;
	
	public RecyclingImageView(Context context) {
		super(context);
	}

	public RecyclingImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	/**
	 * @see android.widget.ImageView#onDetachedFromWindow()
	 */
	@Override
	protected void onDetachedFromWindow() {
		// This has been detached from Window, so clear the drawable
		setImageDrawable(null);
		super.onDetachedFromWindow();
	}

	/**
	 * @see android.widget.ImageView#setImageDrawable(android.graphics.drawable.Drawable)
	 */
	@Override
	public void setImageDrawable(Drawable drawable) {
		// Keep hold of previous Drawable
		final Drawable previousDrawable = getDrawable();

		// Call super to set new Drawable
		super.setImageDrawable(drawable);

		// Notify new Drawable that it is being displayed
		notifyDrawable(drawable, true);

		// Notify old Drawable so it is no longer being displayed
		notifyDrawable(previousDrawable, false);
	}

	/**
	 * Notifies the drawable that it's displayed state has changed.
	 * 
	 * @param drawable
	 * @param isDisplayed
	 */
	private static void notifyDrawable(Drawable drawable, final boolean isDisplayed) {
		if (drawable instanceof RecyclingBitmapDrawable) {
			// The drawable is a CountingBitmapDrawable, so notify it
			((RecyclingBitmapDrawable) drawable).setIsDisplayed(isDisplayed);
		} else if (drawable instanceof LayerDrawable) {
			// The drawable is a LayerDrawable, so recurse on each layer
			LayerDrawable layerDrawable = (LayerDrawable) drawable;
			for (int i = 0, z = layerDrawable.getNumberOfLayers(); i < z; i++) {
				notifyDrawable(layerDrawable.getDrawable(i), isDisplayed);
			}
		}
	}
	//Load image functions no size will wait for onLayout to get correct size
	
	/**
	 * Load Image from remote url, size depends on imageView 
	 * @param worker
	 * @param remoteFilePath
	 */
	public void loadImageRemote(ImageWorker worker, String remoteFilePath) {
		loadImageRemote(worker, remoteFilePath,0,0);
	}
	
	/**
	 * Load image from remote url, with size
	 * @param worker
	 * @param remoteFilePath
	 * @param preferW
	 * @param preferH
	 */
	public void loadImageRemote(ImageWorker worker, String remoteFilePath,int preferW,int preferH) {
		if (worker == null || remoteFilePath == null)
			return;
		initAsyncLoad(worker, LoadRequest.makeRemoteFileRequest(remoteFilePath, preferW>0?preferW:getWidth(), preferH>0?preferH:getHeight()));
	}
	
	/**
	 * Load Image from files, size depends on imageView 
	 * @param worker
	 * @param remoteFilePath
	 */
	public void loadImageLocal(ImageWorker worker, String localFilePath) {
		loadImageLocal(worker, localFilePath,0,0);
	} 
	
	/**
	 * Load image from local files, with specified size
	 * @param worker
	 * @param localFilePath
	 * @param preferW
	 * @param preferH
	 */
	public void loadImageLocal(ImageWorker worker, String localFilePath,int preferW,int preferH) {
		if (worker == null || localFilePath == null)
			return;
		initAsyncLoad(worker, LoadRequest.makeLocalFileRequest(localFilePath,preferW>0?preferW:getWidth(), preferH>0?preferH:getHeight()));
	}
	
	/**
	 * Load Image from resources by Id, size depends on imageView 
	 * @param worker
	 * @param remoteFilePath
	 */
	public void loadImageResource(ImageWorker worker, int res) {
		loadImageResource(worker, res, 0, 0);
	}
	
	/**
	 * Load image from resources id, with size
	 * @param worker
	 * @param res
	 * @param preferW
	 * @param preferH
	 */
	public void loadImageResource(ImageWorker worker, int res,int preferW,int preferH) {
		if (worker == null || res <0)
			return;
		initAsyncLoad(worker, LoadRequest.makeLocalResourceRequest(res, preferW>0?preferW:getWidth(), preferH>0?preferH:getHeight()));
	}
	
	private void initAsyncLoad(ImageWorker worker,LoadRequest request){
		if(request.imgW>0&&request.imgH>0){
			if(ImageWorker.DEBUG) Log.d(ImageWorker.TAG,"load img with size:"+request.imgW+":"+request.imgH);
			worker.loadImage(request, this);
		}else{
			if(ImageWorker.DEBUG) Log.d(ImageWorker.TAG,"load img no size,request layout");
			mPendingRequest=request;
			mImageWorker=worker;
			requestLayout();
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (w > 0 && h > 0 && null != mPendingRequest && null != mImageWorker) {
			LoadRequest.setImageSize(mPendingRequest, w, h);
			if(ImageWorker.DEBUG) Log.d(ImageWorker.TAG,"Size changed:"+getWidth()+":"+getHeight());
			mImageWorker.loadImage(mPendingRequest, this);
		}
	}

	public void onFailingLoadBitmap() {
	};

	public void onLoadStarted() {
	};

	public void onLoadFinished() {
	};

}
