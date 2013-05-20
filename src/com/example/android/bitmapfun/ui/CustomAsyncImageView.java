package com.example.android.bitmapfun.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import com.example.android.bitmapfun.bitmaputils.RecyclingImageView;

public class CustomAsyncImageView extends RecyclingImageView{

	public CustomAsyncImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	public CustomAsyncImageView(Context context) {
		super(context);
	}

	@Override
	public void onFailingLoadBitmap() {
	}
	@Override
	public void onLoadStarted() {
		Log.d(getClass().getSimpleName(),"Loading started");
	}
	@Override
	public void onLoadFinished() {
		Log.d(getClass().getSimpleName(),"Loading finished");
	}

}
