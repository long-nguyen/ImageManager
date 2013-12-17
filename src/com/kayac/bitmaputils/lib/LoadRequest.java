package com.kayac.bitmaputils.lib;

import android.webkit.URLUtil;
/**
 * The loadrequest for multiple types of asyncLoad and images caches. Only
 * URL(network) types would be cached in disk cache supported type is: 
 * + URL for network resource 
 * + File path(GalleryImage id should be converted to filePath), 
 * + Resource Id
 * If imageSize is not specified, original imageSize would be used
 * @author long-nguyen
 * 
 */

public  class LoadRequest {
	public static final int TYPE_REMOTE_PATH = 0;
	public static final int TYPE_LOCAL_PATH = 1;
	public static final int TYPE_LOCAL_RES = 2;
	public String key;
	public int type;
	public int imgW=-1;
	public int imgH=-1;
	
	public static LoadRequest makeLocalFileRequest(String filePath){
		LoadRequest	lr=new LoadRequest();
		lr.key=filePath;
		lr.type=TYPE_LOCAL_PATH;
		return lr;
	}
	

	public static LoadRequest makeLocalResourceRequest(int resId){
		LoadRequest	lr=new LoadRequest();
		lr.key=String.valueOf(resId);
		lr.type=TYPE_LOCAL_RES;
		return lr;
	}
	
	public static LoadRequest makeRemoteFileRequest(String filePath){
		if(!URLUtil.isValidUrl(filePath))  return null;
		LoadRequest lr=new LoadRequest();
		lr.key=filePath;
		lr.type=TYPE_REMOTE_PATH;
		return lr;
	}
	
	public static LoadRequest setImageSize(final LoadRequest lrq,int w,int h){
		if(lrq!=null){
			lrq.imgH=h;
			lrq.imgW=w;
		}
		return lrq;
	}
	
	public static LoadRequest makeLocalFileRequest(String filePath,int width,int height){
		return setImageSize(makeLocalFileRequest(filePath), width, height);
	}
	
	public static LoadRequest makeRemoteFileRequest(String filePath,int width,int height){
		return setImageSize(makeRemoteFileRequest(filePath), width, height);
	}
	
	public static LoadRequest makeLocalResourceRequest(int res,int width,int height){
		return setImageSize(makeLocalResourceRequest(res), width, height);
	}
}