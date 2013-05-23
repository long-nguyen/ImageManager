package com.example.android.bitmapfun.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.example.android.bitmapfun.BuildConfig;
import com.example.android.bitmapfun.R;
import com.example.android.bitmapfun.Utils;
import com.example.android.bitmapfun.bitmaputils.ImageCache;
import com.example.android.bitmapfun.bitmaputils.ImageCache.ImageCacheParams;
import com.example.android.bitmapfun.bitmaputils.ImageWorker;
import com.example.android.bitmapfun.bitmaputils.ImageWorker.LoadRequest;
import com.example.android.bitmapfun.provider.Images;

public class ImageListActivity extends FragmentActivity{
    private static final String IMAGE_CACHE_DIR = "images";

	private ImageWorker mImageFetcher;
	private ListView mList;
	private CustomListAdapter mAdapter;
	
	
	@Override public void onCreate(Bundle savedInstanceState){
		if(BuildConfig.DEBUG){
			Utils.enableStrictMode();
		}
		super.onCreate(savedInstanceState);
		setContentView(R.layout.image_list_activity);
		
		//Find preferable bitmap size(square) here
//		int size=400;
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;

//		
		//Set parameters for loading bitmap:Disk source and memory size
		ImageCache.ImageCacheParams cacheParams=new ImageCacheParams(this, IMAGE_CACHE_DIR);
		cacheParams.setMemCacheSizePercent(0.25f);
		//Image fetcher
		mImageFetcher=new ImageWorker(this, width);
		mImageFetcher.addImageCache(getSupportFragmentManager(), cacheParams);
		/**Special: Animation is here :*/
		mImageFetcher.setImageFadeIn(true);
		
		mAdapter=new CustomListAdapter(this);
		mList=(ListView) findViewById(R.id.list);
		mList.setAdapter(mAdapter);
	}
	
	@Override public void onResume(){
		super.onResume();
		mImageFetcher.setExitTasksEarly(false);
	}
	
	@Override public void onPause(){
		super.onPause();
		//Pause loading 
		mImageFetcher.setPauseWork(true);
		mImageFetcher.setExitTasksEarly(true);
		mImageFetcher.flushCache();
	}
	
	@Override public void onDestroy(){
		super.onDestroy();
		//Close every thing related to DiskCache(note that it is flushed onPause)
		mImageFetcher.closeCache();
	}
	
	
	private class CustomListAdapter extends BaseAdapter{
		private  int mSize;
        private LayoutInflater mInflater;
		public CustomListAdapter(Context context){
			super();
			mSize=Images.imageUrls.length;
			mInflater=  (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		}
		
		@Override
		public View getView(int pos, View convertView, ViewGroup arg2) {
			CustomAsyncImageView imageView;
            if (convertView == null) { 
            	convertView=mInflater.inflate(R.layout.image_list_item, null);
            } 
            imageView=(CustomAsyncImageView) convertView.findViewById(R.id.img);
			mImageFetcher.loadImage(LoadRequest.makeRemoteFileRequest(Images.imageUrls[pos]), imageView);
			return convertView;
		}
		
		@Override
		public int getCount() {
			return mSize;
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int arg0) {
			return 0;
		}

		
		
	}
}
