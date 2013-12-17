package com.kayac.bitmaputils.test.ui;

import java.util.ArrayList;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.kayac.bitmaputils.BuildConfig;
import com.kayac.bitmaputils.R;
import com.kayac.bitmaputils.lib.AsyncTask;
import com.kayac.bitmaputils.lib.ImageCache;
import com.kayac.bitmaputils.lib.ImageCache.ImageCacheParams;
import com.kayac.bitmaputils.lib.ImageWorker;
import com.kayac.bitmaputils.lib.RecyclingImageView;
import com.kayac.bitmaputils.test.Utils;

public class TestFileLoadActivity extends FragmentActivity{
    private static final String IMAGE_CACHE_DIR = "images";

	private ImageWorker mImageFetcher;
	private ListView mList;
	private CustomListAdapter mAdapter;
	private ArrayList<String> filepaths=new ArrayList<String>();
	private MediaFileLoad fileLoad;
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
		mImageFetcher.setImageFadeIn(true);
		mImageFetcher.setLoadingImage(R.drawable.empty_photo);
		
		mAdapter=new CustomListAdapter(this);
		mList=(ListView) findViewById(R.id.list);
		mList.setAdapter(mAdapter);
	}
	
	@Override public void onResume(){
		super.onResume();
		mImageFetcher.setExitTasksEarly(false);
		if(fileLoad!=null&&!fileLoad.isCancelled()) {
			fileLoad.cancel(true);
		}
		fileLoad=new MediaFileLoad();
		fileLoad.execute();
	}
	
	@Override public void onPause(){
		super.onPause();
		//Pause loading 
		mImageFetcher.setPauseWork(true);
		mImageFetcher.setExitTasksEarly(true);
		mImageFetcher.flushCache();
		if(fileLoad!=null) {
			fileLoad.cancel(true);
			fileLoad=null;
		}
		
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
            imageView.loadImageLocal(mImageFetcher, filepaths.get(pos));
//			mImageFetcher.loadImage(LoadRequest.makeLocalFileRequest(filepaths.get(pos),640,640), imageView);
			return convertView;
		}
		
		@Override
		public int getCount() {
			return filepaths.size();
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
	
	
	class MediaFileLoad extends  AsyncTask<Void, Void, Void> {

		@Override
		protected void onPostExecute(Void hasResults) {
			mAdapter.notifyDataSetInvalidated();
		}

		@Override
		protected Void doInBackground(Void... params) {
			
			Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
			String[] projection = { ImageColumns.DATA, ImageColumns.ORIENTATION };
			String[] acceptable_image_types = new String[] { "image/jpeg", "image/png", "image/gif" };
			Cursor cc = null;
			try {
				cc = getApplicationContext().getContentResolver().query(uri, projection,
						MediaColumns.MIME_TYPE + " IN (?,?,?)", acceptable_image_types,
						MediaColumns._ID + " ASC" );
				if(cc==null) return null;
				while (cc.moveToNext()) {
					filepaths.add(cc.getString(cc.getColumnIndexOrThrow(ImageColumns.DATA)));
				}
			}
			finally {
				if (null != cc) {
					cc.close();
				}
			}
			return null;
		}
	};
}
