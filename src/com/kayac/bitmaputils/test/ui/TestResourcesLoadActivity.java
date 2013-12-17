package com.kayac.bitmaputils.test.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView.ScaleType;
import android.widget.ListView;

import com.kayac.bitmaputils.BuildConfig;
import com.kayac.bitmaputils.R;
import com.kayac.bitmaputils.lib.ImageCache;
import com.kayac.bitmaputils.lib.ImageCache.ImageCacheParams;
import com.kayac.bitmaputils.lib.ImageWorker;
import com.kayac.bitmaputils.lib.LoadRequest;
import com.kayac.bitmaputils.lib.RecyclingImageView;
import com.kayac.bitmaputils.test.Utils;

public class TestResourcesLoadActivity extends FragmentActivity{
    private static final String IMAGE_CACHE_DIR = "images";

	private ImageWorker mImageFetcher;
	private ListView mList;
	private CustomListAdapter mAdapter;
	private int[] mRes={
		R.drawable.img1,R.drawable.img2,R.drawable.img3,R.drawable.img4,R.drawable.img5,R.drawable.img6,	
		R.drawable.img7,R.drawable.img8,R.drawable.img9,R.drawable.img10,R.drawable.img11,R.drawable.img12	
	};
	
	@Override public void onCreate(Bundle savedInstanceState){
		if(BuildConfig.DEBUG){
			Utils.enableStrictMode();
		}
		super.onCreate(savedInstanceState);
		setContentView(R.layout.image_list_activity);
		
		//Set parameters for loading bitmap:Disk source and memory size
		ImageCache.ImageCacheParams cacheParams=new ImageCacheParams(this, IMAGE_CACHE_DIR);
		cacheParams.setMemCacheSizePercent(0.25f);
		//Image fetcher
		mImageFetcher=new ImageWorker(this);
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
        private LayoutInflater mInflater;
		public CustomListAdapter(Context context){
			super();
			mInflater=  (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		}
		
		@Override
		public View getView(int pos, View convertView, ViewGroup arg2) {
			RecyclingImageView imageView;
            if (convertView == null) { 
            	convertView=mInflater.inflate(R.layout.image_list_item, null);
            } 
            imageView=(RecyclingImageView) convertView.findViewById(R.id.img);
            imageView.setScaleType(ScaleType.CENTER_CROP);
			mImageFetcher.loadImage(LoadRequest.makeLocalResourceRequest(mRes[pos],640,640), imageView);
			return convertView;
		}
		
		@Override
		public int getCount() {
			return mRes.length;
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
