package com.oldenweb.ChristmasPuzzle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.oldenweb.ChristmasPuzzle.R;

public class Main extends Activity {
	final Handler h = new Handler();
	List<ImageView> items;
	List<Integer> items_rotations;
	SharedPreferences sp;
	Editor ed;
	MediaPlayer mp = new MediaPlayer();
	SoundPool sndpool;
	int snd_move;
	int snd_result;
	int snd_info;
	float start_x;
	float start_y;
	int item_size;
	boolean items_enabled;
	AnimatorSet anim;
	int screen_width;
	int screen_height;
	int t;
	int current_section;
	ViewPager pager;
	int num_cols;
	int num_rows;
	final int spacing = 2; // spacing between blocks
	final int num_photos = 7; // number of photos

	// AdMob
	AdView adMob_smart;
	InterstitialAd adMob_interstitial;
	final boolean show_admob_smart = true; // show AdMob Smart banner
	final boolean show_admob_interstitial = true; // show AdMob Interstitial
	final String adMob_key_smart = "ca-app-pub-3310939034790481/3513231854";
	final String adMob_key_interstitial = "ca-app-pub-3310939034790481/3206249053";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// fullscreen
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// preferences
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		ed = sp.edit();

		show_section(R.id.main);

		// AdMob smart
		add_admob_smart();

		// bg sound
		try {
			mp = new MediaPlayer();
			AssetFileDescriptor descriptor = getAssets().openFd("snd_bg.mp3");
			mp.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
			descriptor.close();
			mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mp.setLooping(true);
			mp.setVolume(0, 0);
			mp.prepare();
			mp.start();
		} catch (Exception e) {
		}

		// mute
		if (sp.getBoolean("mute", false)) {
			((Switch) findViewById(R.id.config_mute)).setChecked(true);
		} else {
			mp.setVolume(0.2f, 0.2f);
		}

		// volume switch listener
		((Switch) findViewById(R.id.config_mute)).setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				ed.putBoolean("mute", isChecked);
				ed.commit();

				if (isChecked) {
					mp.setVolume(0, 0);
				} else {
					mp.setVolume(0.2f, 0.2f);
				}
			}
		});

		// mode
		if (sp.getInt("mode", 0) == 1) {
			((Switch) findViewById(R.id.config_mode)).setChecked(true);
			num_cols = 8;
			num_rows = 10;
		} else {
			num_cols = 4;
			num_rows = 5;
		}

		// mode switch listener
		((Switch) findViewById(R.id.config_mode)).setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					ed.putInt("mode", 1);
					num_cols = 8;
					num_rows = 10;
				} else {
					ed.putInt("mode", 0);
					num_cols = 4;
					num_rows = 5;
				}

				ed.commit();
			}
		});

		// SoundPool
		sndpool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
		try {
			snd_move = sndpool.load(getAssets().openFd("snd_move.mp3"), 1);
			snd_result = sndpool.load(getAssets().openFd("snd_result.mp3"), 1);
			snd_info = sndpool.load(getAssets().openFd("snd_info.mp3"), 1);
		} catch (IOException e) {
		}

		// custom font
		Typeface font = Typeface.createFromAsset(getAssets(), "CooperBlack.otf");
		((TextView) findViewById(R.id.txt_result)).setTypeface(font);
		((TextView) findViewById(R.id.txt_high_result)).setTypeface(font);
		((TextView) findViewById(R.id.txt_faq)).setTypeface(font);
		font = Typeface.createFromAsset(getAssets(), "BankGothic.ttf");
		((TextView) findViewById(R.id.txt_tap)).setTypeface(font);
		((TextView) findViewById(R.id.txt_description)).setTypeface(font);

		// photos list
		pager = new ViewPager(this);
		pager.setOffscreenPageLimit(1);
		final List<View> photos = new ArrayList<View>();
		for (int i = 0; i < num_photos; i++) {
			ImageView item = new ImageView(this);
			item.setImageResource(getResources().getIdentifier("item" + i, "drawable", getPackageName()));
			photos.add(item);
		}

		// add pager
		pager.setAdapter(new SwipeAdapter(photos));
		((ViewGroup) findViewById(R.id.photos)).addView(pager);
	}

	// SwipeAdapter
	public class SwipeAdapter extends PagerAdapter {
		List<View> list;

		public SwipeAdapter(List<View> list) {
			this.list = list;
		}

		@Override
		public Object instantiateItem(View collection, int position) {
			View view = list.get(position);
			((ViewPager) collection).addView(view, 0);
			return view;
		}

		@Override
		public void destroyItem(View collection, int position, Object view) {
			((ViewPager) collection).removeView((View) view);
		}

		@Override
		public int getCount() {
			return list.size();
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view.equals(object);
		}
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			// fullscreen mode
			if (android.os.Build.VERSION.SDK_INT >= 19) {
				getWindow().getDecorView().setSystemUiVisibility(
						View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
								| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
								| View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
			}
		}
	}

	// START
	void START() {
		items_enabled = true;
		t = 0;
		items = new ArrayList<ImageView>();
		items_rotations = new ArrayList<Integer>();
		((ViewGroup) findViewById(R.id.game)).removeAllViews();

		// screen size
		screen_width = Math.min(findViewById(R.id.all).getWidth(), findViewById(R.id.all).getHeight());
		screen_height = Math.max(findViewById(R.id.all).getWidth(), findViewById(R.id.all).getHeight());

		// item size
		item_size = (int) Math.floor(Math.min((screen_width - (num_cols - 1) * spacing) / num_cols,
				(screen_height - (num_rows - 1) * spacing) / num_rows));

		// start position
		start_x = (screen_width - item_size * num_cols - (num_cols - 1) * spacing) / 2;
		start_y = (screen_height - item_size * num_rows - (num_rows - 1) * spacing) / 2;

		// bitmap
		final Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
				getResources().getIdentifier("item" + pager.getCurrentItem(), "drawable", getPackageName()));

		// scale matrix
		final Matrix m = new Matrix();
		m.setScale((float) (num_cols * item_size) / bitmap.getWidth(), (float) (num_rows * item_size) / bitmap.getHeight());

		// bitmap scaled
		final Bitmap bitmap_scaled = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);

		// add items
		float x_pos = 0;
		float y_pos = 0;
		for (int i = 0; i < num_rows * num_cols; i++) {
			ImageView item = new ImageView(this);
			item.setClickable(true);
			item.setLayoutParams(new LayoutParams(item_size, item_size));

			// image piece
			item.setImageBitmap(Bitmap.createBitmap(bitmap_scaled, (int) x_pos * item_size, (int) y_pos * item_size, item_size,
					item_size));

			// position
			item.setX(start_x + x_pos * item_size + x_pos * spacing);
			item.setY(start_y + y_pos * item_size + y_pos * spacing);

			// random rotation
			items_rotations.add((int) (Math.round(Math.random() * 3) * 90));
			item.setRotation(items_rotations.get(i));

			((ViewGroup) findViewById(R.id.game)).addView(item);
			items.add(item);

			x_pos++;
			if (x_pos == num_cols) {
				x_pos = 0;
				y_pos++;
			}

			// click listener
			item.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					if (items_enabled) {
						// animation list
						final List<Animator> anim_list = new ArrayList<Animator>();

						// scale down
						anim = new AnimatorSet();
						anim.playTogether(ObjectAnimator.ofFloat(v, "scaleX", 0.5f), ObjectAnimator.ofFloat(v, "scaleY", 0.5f));
						anim_list.add(anim);

						// rotate
						final int current_item = items.indexOf(v);
						items_rotations.set(current_item, items_rotations.get(current_item) + 90);
						anim_list.add(ObjectAnimator.ofFloat(v, "rotation", items_rotations.get(current_item)));

						// scale up
						anim = new AnimatorSet();
						anim.playTogether(ObjectAnimator.ofFloat(v, "scaleX", 1), ObjectAnimator.ofFloat(v, "scaleY", 1));
						anim_list.add(anim);

						// animation
						anim = new AnimatorSet();
						anim.playSequentially(anim_list);
						anim.setDuration(50);
						anim.addListener(new AnimatorListener() {
							@Override
							public void onAnimationEnd(Animator animation) {
								check_items();
							}

							@Override
							public void onAnimationCancel(Animator animation) {
							}

							@Override
							public void onAnimationRepeat(Animator animation) {
							}

							@Override
							public void onAnimationStart(Animator animation) {
								// sound
								if (!sp.getBoolean("mute", false)) {
									sndpool.play(snd_move, 0.2f, 0.2f, 0, 0, 1);
								}
							}
						});
						anim.start();
					}
				}
			});
		}

		h.postDelayed(TIMER, 1000);
		show_section(R.id.game);

		// AdMob Interstitial
		add_admob_interstitial();
	}

	// check_items
	void check_items() {
		for (int i = 0; i < items.size(); i++) {
			if ((float) items_rotations.get(i) / 360 != (float) Math.round(items_rotations.get(i) / 360)) {
				return;
			}
		}

		// all done
		items_enabled = false;
		h.removeCallbacks(TIMER);

		// save time
		if (!sp.contains(sp.getInt("mode", 0) + "time" + pager.getCurrentItem())
				|| (sp.contains(sp.getInt("mode", 0) + "time" + pager.getCurrentItem()) && t < sp.getInt(sp.getInt("mode", 0)
						+ "time" + pager.getCurrentItem(), 0))) {
			ed.putInt(sp.getInt("mode", 0) + "time" + pager.getCurrentItem(), t);
			ed.commit();
		}

		// show time message
		Toast toast = Toast.makeText(Main.this, getString(R.string.completed), Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.CENTER, 0, 0);
		((TextView) ((LinearLayout) toast.getView()).getChildAt(0)).setTextSize(DpToPx(20));
		toast.show();

		// sound
		if (!sp.getBoolean("mute", false)) {
			sndpool.play(snd_info, 1f, 1f, 0, 0, 1);
		}

		h.postDelayed(STOP, 3000);
	}

	// TIMER
	Runnable TIMER = new Runnable() {
		@Override
		public void run() {
			t++;
			h.postDelayed(TIMER, 1000);
		}
	};

	// STOP
	Runnable STOP = new Runnable() {
		@Override
		public void run() {
			// show result
			show_section(R.id.result);

			// show result
			((TextView) findViewById(R.id.txt_result)).setText(getString(R.string.time) + " " + timeConvert(t));
			((TextView) findViewById(R.id.txt_high_result)).setText(getString(R.string.best_time) + " "
					+ timeConvert(sp.getInt(sp.getInt("mode", 0) + "time" + pager.getCurrentItem(), 0)));

			// sound
			if (!sp.getBoolean("mute", false)) {
				sndpool.play(snd_result, 0.6f, 0.6f, 0, 0, 1);
			}
		}
	};

	// onClick
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_start:
			START();
			break;
		case R.id.btn_config:
			show_section(R.id.config);
			break;
		case R.id.result:
			show_section(R.id.main);
			break;
		case R.id.txt_faq:
			findViewById(R.id.txt_faq).setVisibility(View.GONE);
			break;
		}
	}

	@Override
	public void onBackPressed() {
		switch (current_section) {
		case R.id.main:
			super.onBackPressed();
			break;
		case R.id.config:
		case R.id.result:
			show_section(R.id.main);
			break;
		case R.id.game:
			show_section(R.id.main);
			h.removeCallbacks(TIMER);
			h.removeCallbacks(STOP);
			if (anim != null) {
				anim.cancel();
			}
			break;
		}
	}

	// show_section
	void show_section(int section) {
		current_section = section;
		findViewById(R.id.main).setVisibility(View.GONE);
		findViewById(R.id.game).setVisibility(View.GONE);
		findViewById(R.id.config).setVisibility(View.GONE);
		findViewById(R.id.result).setVisibility(View.GONE);
		findViewById(current_section).setVisibility(View.VISIBLE);

		if (current_section == R.id.game) {
			findViewById(R.id.txt_faq).setVisibility(View.VISIBLE);
		} else {
			findViewById(R.id.txt_faq).setVisibility(View.GONE);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		h.removeCallbacks(TIMER);
		h.removeCallbacks(STOP);
		mp.release();
		sndpool.release();

		// destroy AdMob
		if (adMob_smart != null) {
			adMob_smart.destroy();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		// sound
		if (!sp.getBoolean("mute", false)) {
			mp.setVolume(0.2f, 0.2f);
		}

		// timer
		if (current_section == R.id.game && items_enabled) {
			h.removeCallbacks(TIMER);
			h.postDelayed(TIMER, 1000);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		mp.setVolume(0, 0);
		h.removeCallbacks(TIMER);
	}

	// DpToPx
	float DpToPx(float dp) {
		return (dp * (getResources().getDisplayMetrics().densityDpi / 160f));
	}

	// PxToDp
	float PxToDp(float px) {
		return px / (getResources().getDisplayMetrics().densityDpi / 160f);
	}

	// timeConvert
	String timeConvert(int t) {
		String str = "";
		int d, h, m, s;

		if (t / 86400 >= 1) {// if day exist
			d = t / 86400;
			str += d + ":";
		} else {
			d = 0;
		}

		t = t - (86400 * d);

		if (t / 3600 >= 1) {// if hour exist
			h = t / 3600;
			if (h < 10 && d > 0) {
				str += "0";
			}
			str += h + ":";
		} else {
			h = 0;
		}

		if ((t - h * 3600) / 60 >= 1) {// if minute exist
			m = (t - h * 3600) / 60;
			s = (t - h * 3600) - m * 60;
			if (m < 10 && h > 0) {
				str += "0";
			}
			str += m + ":";
		} else {
			m = 0;
			s = t - h * 3600;
		}

		if (s < 10 && m > 0) {
			str += "0";
		}
		str += s;

		return str;
	}

	// add_admob_smart
	void add_admob_smart() {
		if (show_admob_smart
				&& ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null) {
			adMob_smart = new AdView(this);
			adMob_smart.setAdUnitId(adMob_key_smart);
			adMob_smart.setAdSize(AdSize.SMART_BANNER);
			((ViewGroup) findViewById(R.id.admob)).addView(adMob_smart);
			com.google.android.gms.ads.AdRequest.Builder builder = new AdRequest.Builder();
			// builder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR).addTestDevice("4d0555dfcad9b000");
			adMob_smart.loadAd(builder.build());
		}
	}

	// add_admob_interstitial
	void add_admob_interstitial() {
		if (show_admob_interstitial
				&& ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null) {
			adMob_interstitial = new InterstitialAd(this);
			adMob_interstitial.setAdUnitId(adMob_key_interstitial);
			com.google.android.gms.ads.AdRequest.Builder builder = new AdRequest.Builder();
			// builder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR).addTestDevice("4d0555dfcad9b000");
			adMob_interstitial.setAdListener(new AdListener() {
				@Override
				public void onAdLoaded() {
					super.onAdLoaded();
					adMob_interstitial.show();
				}
			});
			adMob_interstitial.loadAd(builder.build());
		}
	}
}