/*
 *      Copyright (C) 2005-2009 Team XBMC
 *      http://xbmc.org
 *
 *  This Program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2, or (at your option)
 *  any later version.
 *
 *  This Program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with XBMC Remote; see the file license.  If not, write to
 *  the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.
 *  http://www.gnu.org/copyleft/gpl.html
 *
 */

package org.xbmc.android.remote.presentation.controller;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xbmc.android.remote.R.drawable;
import org.xbmc.android.remote.business.ManagerFactory;
import org.xbmc.android.remote.presentation.activity.UrlIntentActivity;
import org.xbmc.api.business.DataResponse;
import org.xbmc.api.business.IControlManager;
import org.xbmc.api.business.IInfoManager;
import org.xbmc.api.info.SystemInfo;
import org.xbmc.api.presentation.INotifiableController;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Class that is called when XBMC locally tries to open any media content. The
 * user then has an additional option "Play in XBMC".
 * 
 * @author Team XBMC
 */
public class UrlIntentController extends AbstractController implements IController, INotifiableController {
	
	private IControlManager mControlManager;
	private IInfoManager mInfoManager;
	private DataResponse<String> mXbmcStatusHandler;
	private static final String CONFIRM_PLAY_ON_XBMC = "setting_confirm_play_on_xbmc";
	
	public UrlIntentController(Activity activity, Handler handler) {
		super.onCreate(activity, handler);
		mInfoManager = ManagerFactory.getInfoManager(this);
		mControlManager = ManagerFactory.getControlManager(this);
	}

	/* (non-Javadoc)
	 * @see org.xbmc.android.remote.presentation.controller.IController#onActivityPause()
	 */
	public void onActivityPause() {
		mInfoManager.setController(null);
		mControlManager.setController(null);
		super.onActivityPause();
	}

	/* (non-Javadoc)
	 * @see org.xbmc.android.remote.presentation.controller.IController#onActivityResume(android.app.Activity)
	 */
	public void onActivityResume(Activity activity) {
		super.onActivityResume(activity);
		mInfoManager.setController(this);
		mControlManager.setController(this);
		mInfoManager.getSystemInfo(mXbmcStatusHandler, SystemInfo.SYSTEM_BUILD_VERSION, mActivity.getApplicationContext());
	}

	public void playUrl(String url) {
		mControlManager.playUrl(new DataResponse<Boolean>(), url, mActivity.getApplicationContext());
	}
	
	public void setupStatusHandler() {
		mXbmcStatusHandler = new DataResponse<String>() {
			public void run() {
				if (!value.equals("")) {
					checkIntent();
				}
			}
		};
	}

	@Override
	public void onError(Exception exception) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
		try {
			throw exception;
		} catch (Exception e) {
			builder.setTitle("Unable to send URL to XBMC!");
			builder.setMessage(e.getMessage());
			builder.setNeutralButton("Ok", new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					mActivity.finish();
				}
			});
			final AlertDialog alert = builder.create();
			try {
				alert.show();
			} catch (Exception ee) {
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * Checks the intent that created/resumed this activity. Used to see if we are being handed
	 * an URL that should be passed to XBMC.
	 */
	private void checkIntent(){
		Intent intent = mActivity.getIntent();
		final String action = intent.getAction();
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity.getApplicationContext());

		if(action != null) {
			Log.i("CHECKINTENT", action);
			if ( action.equals(UrlIntentActivity.ACTION) ){
				//final String path = intent.getData().toString();
				final String path = intent.getStringExtra(Intent.EXTRA_TEXT);
				if(path == null || path.equals(""))
					return;
				try{
					new URL(path);
				} catch(MalformedURLException e) {
					return;
				}
				
				// If it is a youtube URL, we have to parse the video id from it and send it to the youtube plugin.
				// The syntax for that is plugin://plugin.video.youtube/?path=/root/search%26action=play_video%26videoid=VIDEOID
				Uri playuri = Uri.parse(path);
				final String url;
				String message = null;
				if (playuri.getHost().endsWith("youtube.com") || playuri.getHost().endsWith("youtu.be")) {
					// We'll need to get the v= parameter from the URL and use
					// that to send to XBMC
					final Pattern pattern = Pattern.compile(".*v=([a-z0-9_\\-]+)(?:&.)*", Pattern.CASE_INSENSITIVE);
					final Matcher matcher = pattern.matcher(path);
					if (matcher.matches()) {
						url = "plugin://plugin.video.youtube/?path=/root/search&action=play_video&videoid=" + matcher.group(1);
						message = "Do you want to play\nYoutube video " + matcher.group(1) + " on XBMC? Youtube addon required!";
					} else {
						url = playuri.toString();
					}
				} else {
					// Not a youtube URL so just pass it on to XBMC as-is
					url = playuri.toString();
				}
				if (message == null) {
					message = "Do you want to play\n" + path + "\n on XBMC?";
				}
				
				if (prefs.getBoolean(CONFIRM_PLAY_ON_XBMC, true)) {
					final Builder builder = new Builder(mActivity);
					builder.setTitle("Play URL on XBMC?");
					builder.setMessage(message);
					builder.setCancelable(true);
					builder.setIcon(drawable.icon);
					builder.setNeutralButton("Yes", new android.content.DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							new Thread(){
								public void run(){
									Looper.prepare();
									playUrl(url);
									Looper.loop();
								}
							}.start();
							mActivity.finish();
						}
					});
					builder.setCancelable(true);
					builder.setNegativeButton("No", new android.content.DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dialog.cancel();
							mActivity.finish();
						}
					});
					
					final AlertDialog alert = builder.create();
					try {
						alert.show();
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					new Thread(){
						public void run(){
							Looper.prepare();
							playUrl(url);
							Looper.loop();
						}
					}.start();
					mActivity.finish();
				}
				//cleanup so we won't trigger again.
				intent.setAction(null);
				intent.setData(null);
			}
		}
	}
}
