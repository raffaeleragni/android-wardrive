/*
 *   wardrive - android wardriving application
 *   Copyright (C) 2009 Raffaele Ragni
 *   http://code.google.com/p/wardrive-android/
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *   
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ki.wardrive;

import java.io.File;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.text.TextPaint;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;

/**
 * Wardriving app, stores scans in sqlite db on the sdcard and displays found
 * netwokrs around in the map.
 * Automatically turns on: WiFi, GPS, Internet (for google maps). Needs a valid
 * GPS location to make scans working.
 * Green dots are open WiFis, red for non-open ones. The blue dot is the user's
 * position.
 * 
 * @author Raffaele Ragni raffaele.ragni@gmail.com
 */
public class Main extends MapActivity implements LocationListener
{
	//
	// Program related
	//

	private DateFormat df = DateFormat.getDateInstance(DateFormat.FULL);
	
	private SharedPreferences settings;

	private SharedPreferences.Editor settings_editor;

	private WakeLock wake_lock;

	private int gps_times = 0;

	public boolean service = true;

	private Intent service_intent = null;

	//
	// Interface Related
	//

	private MapView mapview;

	private Overlays overlays_closed;

	private Overlays overlays_opened;

	private Overlays overlays_wep;

	private Overlays overlays_me;
	
	private ExtraInfoOverlay overlay_extra;
	
	private ScaleOverlay overlay_scale;

	public boolean show_labels = false;

	public boolean follow_me = true;

	public boolean map_sat = false;

	public boolean show_open = true;

	public boolean show_closed = false;
	
	public boolean show_wep = false;
	
	public boolean show_scale = false;

    private boolean filter_enabled = false;

    private boolean filter_inverse = false;

    private String filter_regexp = null;
	
	private boolean exporting_kml = false;
	
	private boolean sending_wigle = false;

	private boolean notifications_enabled = false;
	
	private ProgressDialog kmlProgressDialog;
	
	private ProgressDialog wigleProgressDialog;
	
	private String wigle_username = null;
	
	private String wigle_password = null;
	
	private String kml_export_path = null;

	//
	// DB Related
	//

	private SQLiteDatabase database;

	//
	// Location Related
	//

	private LocationManager location_manager;

	private Location last_location = null;

	//
	// Service related
	//

	private IScanService service_binder = null;

	private ServiceConnection service_connection = new ServiceConnection()
	{
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			if (service instanceof IScanService)
			{
				service_binder = (IScanService) service;
				service_binder.setGpsTimes(Constants.GPS_SECONDS[gps_times], Constants.GPS_METERS[gps_times]);
				service_binder.setNotificationsEnabled(notifications_enabled);
			}
		}

		public void onServiceDisconnected(ComponentName name)
		{
		}
	};

	private void reloadSettings()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		
		String s_gps_times = prefs.getString("gpsTimes", "0");
		gps_times = Integer.parseInt(s_gps_times);
        filter_enabled = prefs.getBoolean("wifiFilterEnabled", false);
        filter_inverse = prefs.getBoolean("wifiFilterInverse", false);
        filter_regexp = prefs.getString("wifiFilter", "");
		show_labels = prefs.getBoolean("showAPLabels", true);
		follow_me = prefs.getBoolean("followMe", true);
		show_open = prefs.getBoolean("showOpenAPs", true);
		show_closed = prefs.getBoolean("showClosedAPs", false);
		show_wep = prefs.getBoolean("showWEPAPs", false);
        show_scale = prefs.getBoolean("mapScale", show_scale);
		map_sat = prefs.getBoolean("mapSat", false);
		notifications_enabled = prefs.getBoolean("notificationsEnabled", false);
        wigle_username = prefs.getString("wigleUsername", "");
        wigle_password = prefs.getString("wiglePassword", "");
        kml_export_path = prefs.getString("kmlExportPath", "");
        
        // Update overlays booleans - Labels
        overlays_closed.show_labels = show_labels;
		overlays_opened.show_labels = show_labels;
		overlays_wep.show_labels = show_labels;
		overlays_me.show_labels = show_labels;
        // Update overlays booleans - Scale
		overlay_scale.show_scale = show_scale;

		// Update service options from here
		if (service_binder != null)
		{
			service_binder.setNotificationsEnabled(notifications_enabled);
			service_binder.setGpsTimes(Constants.GPS_SECONDS[gps_times], Constants.GPS_METERS[gps_times]);
		}
		
		// Update map preferences
		mapview.setSatellite(map_sat);
		
		// Redraw map and overlays
		mapview.invalidate();
	}
	
	//
	// Status change
	//

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		try
		{
			// Saved GPS location & zoom, init MapView
			settings = getPreferences(MODE_PRIVATE);
			settings_editor = settings.edit();
			GeoPoint point = new GeoPoint(settings.getInt(Constants.LAST_LAT, Constants.DEFAULT_LAT), settings.getInt(
					Constants.LAST_LON, Constants.DEFAULT_LON));
			mapview = (MapView) findViewById(R.id.mapview);
			mapview.getController().animateTo(point);
			mapview.getController().setZoom(settings.getInt(Constants.ZOOM_LEVEL, Constants.DEFAULT_ZOOM_LEVEL));
			mapview.setBuiltInZoomControls(true);
			mapview.setClickable(true);
			mapview.setLongClickable(true);

			Drawable d = getResources().getDrawable(R.drawable.empty);
			overlays_closed = new Overlays(Constants.OTYPE_CLOSED_WIFI, d);
			overlays_opened = new Overlays(Constants.OTYPE_OPEN_WIFI, d);
			overlays_wep = new Overlays(Constants.OTYPE_WEP, d);
			overlays_me = new Overlays(Constants.OTYPE_MY_LOCATION, d);
			overlay_extra = new ExtraInfoOverlay();
			overlay_scale = new ScaleOverlay();

			mapview.getOverlays().add(overlays_closed);
			mapview.getOverlays().add(overlays_opened);
			mapview.getOverlays().add(overlays_wep);
			mapview.getOverlays().add(overlays_me);
			mapview.getOverlays().add(overlay_extra);	
			mapview.getOverlays().add(overlay_scale);			
			
			database = SQLiteDatabase.openOrCreateDatabase(DBTableNetworks.getDBFullPath(), null);
			if (database != null)
			{
				database.execSQL(DBTableNetworks.CREATE_TABLE_NETWORKS);
				database.execSQL(DBTableNetworks.CREATE_INDEX_LATLON);
				database.execSQL(DBTableNetworks.CREATE_INDEX_CAPABILITIES);
				// This may give problems: database.execSQL(DBTableNetworks.OPTIMIZATION_SQL);
			}

			service_intent = new Intent();
			service_intent.setClass(this, ScanService.class);
			bindService(service_intent, service_connection, Context.BIND_AUTO_CREATE);
			startService(service_intent);

			// Reload settings also sets variables on overlays and on the service
			reloadSettings();
			
			location_manager = (LocationManager) getSystemService(LOCATION_SERVICE);

			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wake_lock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, this.getClass().getName());
		}
		catch (Exception e)
		{
			notify_error(e);
			finish();
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		if (wake_lock != null && !wake_lock.isHeld())
			wake_lock.acquire();
	}

	@Override
	protected void onPause()
	{
		if (wake_lock != null && wake_lock.isHeld())
			wake_lock.release();

		super.onPause();
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		
		try
		{
			if (location_manager != null)
				location_manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, Constants.MAPS_GPS_EVENT_WAIT,
						Constants.MAPS_GPS_EVENT_METERS, Main.this);
		}
		catch (Exception e)
		{
			notify_error(e);
		}
	}

	@Override
	protected void onStop()
	{
		try
		{
			if (location_manager != null)
				location_manager.removeUpdates(Main.this);

			settings_editor.putInt(Constants.ZOOM_LEVEL, mapview.getZoomLevel());
			GeoPoint p = mapview.getProjection().fromPixels(mapview.getWidth() / 2, mapview.getHeight() / 2);
			settings_editor.putInt(Constants.LAST_LAT, p.getLatitudeE6());
			settings_editor.putInt(Constants.LAST_LON, p.getLongitudeE6());
			settings_editor.commit();

			save_app_tstamp();
		}
		catch (Exception e)
		{
			notify_error(e);
		}

		super.onStop();
	}

	@Override
	protected void onDestroy()
	{
		if (database != null)
		{
			database.close();
			database = null;
		}

		super.onDestroy();
	}

	public void onProviderDisabled(String provider)
	{
	}

	public void onProviderEnabled(String provider)
	{
	}

	//
	// Menu options
	//

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater mi = getMenuInflater();
		mi.inflate(R.menu.options_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		return super.onPrepareOptionsMenu(menu);
	}

	private final static int REQUEST_PREFERENCES = 1;
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
			case REQUEST_PREFERENCES:
				reloadSettings();
				break;
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.menu_id.PREFERENCES:
				startActivityForResult(new Intent(getBaseContext(), Preferences.class), REQUEST_PREFERENCES);
				break;
				
			case R.menu_id.QUIT:
                if (service_binder != null)
                    service_binder.stop_services();
                if (service_intent != null)
                    stopService(service_intent);
                save_service_tstamp();
                save_app_tstamp();
				finish();
				break;
				
			case R.menu_id.STATS:
				showDialog(Constants.DIALOG_STATS);
				break;

			case R.menu_id.ABOUT:
				showDialog(Constants.DIALOG_ABOUT);
				break;

			case R.menu_id.SERVICE:
				service = !service;
				if (service)
				{
					if (service_binder != null)
						service_binder.start_services();
					if (service_intent != null)
						startService(service_intent);
				}
				else
				{
					if (service_binder != null)
						service_binder.stop_services();
					if (service_intent != null)
						stopService(service_intent);
					save_service_tstamp();
				}
				mapview.invalidate();
				break;

			case R.menu_id.DELETE:
				showDialog(Constants.DIALOG_DELETE_ALL_WIFI);
				break;

			case R.menu_id.KML_EXPORT:
				if (!exporting_kml)
					new Thread(kml_export_proc).start();
				showDialog(Constants.DIALOG_EXPORT_KML_PROGRESS);
				
				break;

            case R.menu_id.LIST:
            	GeoPoint top_left = mapview.getProjection().fromPixels(0, 0);
				GeoPoint bottom_right = mapview.getProjection().fromPixels(mapview.getWidth(), mapview.getHeight());
				Bundle b = new Bundle();
				b.putInt("minlat", top_left.getLatitudeE6());
            	b.putInt("minlon", top_left.getLongitudeE6());
            	b.putInt("maxlat", bottom_right.getLatitudeE6());
            	b.putInt("maxlon", bottom_right.getLongitudeE6());
            	Intent i = new Intent(this, ListWifiActivity.class);
            	i.putExtras(b);
                startActivity(i);
                break;

            case R.menu_id.SEND_TO_WIGLE:
            	if (!sending_wigle)
            		new Thread(wigle_send_proc).start();
				showDialog(Constants.DIALOG_WIGLE_UPLOAD);
            	break;
		}
		
		return true;
	}

	private Runnable kml_export_proc = new Runnable()
	{
		public void run()
		{
			exporting_kml = true;
			String path = "";
			if( kml_export_path != null )
				path = kml_export_path + "/wardrive.kml";
			else
				path = "wardrive.kml";
			
			if (KMLExport.export(database, new File( Environment.getExternalStorageDirectory(), path), message_handler))
			{
				exporting_kml = false;
				message_handler.sendMessage(Message.obtain(message_handler, Constants.EVENT_KML_EXPORT_DONE));
			}
		}
	};
	
	private Runnable wigle_send_proc = new Runnable()
	{
		public void run()
		{
			sending_wigle = true;
			String path = "";
			if( kml_export_path != null )
				path = kml_export_path + "/wardrive.kml";
			else
				path = "wardrive.kml";
			
			File f = new File( Environment.getExternalStorageDirectory(), path);
			if (!f.exists())
			{
				message_handler.sendMessage(Message.obtain(message_handler, Constants.EVENT_SEND_TO_WIGLE_FILE_NOT_FOUND));
				sending_wigle = false;
				return;
			}
			
			if (WigleUploader.upload(Main.this.wigle_username, Main.this.wigle_password, f, message_handler))
			{
				sending_wigle = false;
				message_handler.sendMessage(Message.obtain(message_handler, Constants.EVENT_SEND_TO_WIGLE_OK));
			}
			else
			{
				sending_wigle = false;
				message_handler.sendMessage(Message.obtain(message_handler, Constants.EVENT_SEND_TO_WIGLE_ERROR));
			}
		}
	};

	private Handler message_handler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
				case Constants.EVENT_KML_EXPORT_DONE:
					exporting_kml = false;
					if (kmlProgressDialog.isShowing())
						dismissDialog(Constants.DIALOG_EXPORT_KML_PROGRESS);
					Toast.makeText(Main.this, R.string.MESSAGE_SUCCESFULLY_EXPORTED_KML, Toast.LENGTH_SHORT).show();
					break;
				
				case Constants.EVENT_NOTIFY_ERROR:
					notify_error((Exception) msg.getData().getSerializable("exception"));
					break;
				
				case Constants.EVENT_SEND_TO_WIGLE_FILE_NOT_FOUND:
					sending_wigle = false;
					Toast.makeText(Main.this, R.string.MESSAGE_SEND_TO_WIGLE_FILE_NOT_FOUND, Toast.LENGTH_SHORT).show();
					break;

				case Constants.EVENT_SEND_TO_WIGLE_ERROR:
					sending_wigle = false;
					Toast.makeText(Main.this, R.string.MESSAGE_SEND_TO_WIGLE_ERROR, Toast.LENGTH_SHORT).show();
					break;

				case Constants.EVENT_SEND_TO_WIGLE_OK:
					sending_wigle = false;
					if (wigleProgressDialog.isShowing())
						dismissDialog(Constants.DIALOG_WIGLE_UPLOAD);
					Toast.makeText(Main.this, R.string.MESSAGE_SEND_TO_WIGLE_OK, Toast.LENGTH_SHORT).show();
					break;

				case Constants.EVENT_KML_EXPORT_PROGRESS:
					if (kmlProgressDialog != null && kmlProgressDialog.isShowing())
					{
						int count = msg.getData().getInt(Constants.EVENT_KML_EXPORT_PROGRESS_PAR_COUNT);
						int total = msg.getData().getInt(Constants.EVENT_KML_EXPORT_PROGRESS_PAR_TOTAL);
						kmlProgressDialog.setMax(total);
						kmlProgressDialog.setProgress(count);
					}
					break;

				case Constants.EVENT_WIGLE_UPLOAD_PROGRESS:
					if (wigleProgressDialog != null && wigleProgressDialog.isShowing())
					{
						int count = msg.getData().getInt(Constants.EVENT_WIGLE_UPLOAD_PROGRESS_PAR_COUNT);
						int total = msg.getData().getInt(Constants.EVENT_WIGLE_UPLOAD_PROGRESS_PAR_TOTAL);
						wigleProgressDialog.setMax(total);
						wigleProgressDialog.setProgress(count);
					}
					break;
			}
		}
	};

	@Override
	protected Dialog onCreateDialog(int id)
	{
		AlertDialog.Builder builder;
		switch (id)
		{
			case Constants.DIALOG_STATS:
				builder = new AlertDialog.Builder(this);
				builder.setMessage(print_stats());
				return builder.create();

			case Constants.DIALOG_ABOUT:
				builder = new AlertDialog.Builder(this);
				builder.setMessage(getResources().getText(R.string.ABOUT_BOX));
				return builder.create();

			case Constants.DIALOG_DELETE_ALL_WIFI:
				builder = new AlertDialog.Builder(this);
				builder.setMessage(getResources().getText(R.string.MENU_DELETE_WARNING_LABEL));
				builder.setCancelable(false);
				builder.setPositiveButton(getResources().getString(R.string.YES), new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int id)
					{
						delete_all_wifi();
					}
				});
				builder.setNegativeButton(getResources().getString(R.string.NO), new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int id)
					{
						dialog.cancel();
					}
				});
				return builder.create();

			case Constants.DIALOG_EXPORT_KML_PROGRESS:
				kmlProgressDialog = new ProgressDialog(this);
				kmlProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				kmlProgressDialog.setMessage(getResources().getString(R.string.MESSAGE_STARTING_KML_EXPORT));
				kmlProgressDialog.setProgress(0);
				kmlProgressDialog.setCancelable(true);
				return kmlProgressDialog;
            
            case Constants.DIALOG_WIGLE_UPLOAD:
            	wigleProgressDialog = new ProgressDialog(this);
				wigleProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				wigleProgressDialog.setMessage(getResources().getString(R.string.MESSAGE_STARTING_SEND_TO_WIGLE));
				wigleProgressDialog.setProgress(0);
				wigleProgressDialog.setCancelable(true);
				return wigleProgressDialog;
		}

		return super.onCreateDialog(id);
	}

	private void delete_all_wifi()
	{
		if (database != null)
			database.execSQL(DBTableNetworks.DELETE_ALL_WIFI);
	}

	private String print_stats()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(getResources().getString(R.string.MESSAGE_STATISTICS));

		if (database != null)
		{
			Cursor c = null;
			try
			{
				int total, open, last;

				c = database.rawQuery(DBTableNetworks.SELECT_COUNT_WIFIS, null);
				c.moveToFirst();
				total = c.getInt(0);
				c.close();

				c = database.rawQuery(DBTableNetworks.SELECT_COUNT_OPEN, null);
				c.moveToFirst();
				open = c.getInt(0);
				c.close();

				c = database.rawQuery(DBTableNetworks.SELECT_COUNT_LAST, new String[] { ""
						+ settings.getLong(Constants.CONF_LASTSERVICE_TSTAMP, 0) });
				c.moveToFirst();
				last = c.getInt(0);
				
				SimpleDateFormat tf = new SimpleDateFormat("HH:mm:ss");
				Date lastapp = new Date(settings.getLong(Constants.CONF_LASTAPP_TSTAMP, 0));
				Date lastservice = new Date(settings.getLong(Constants.CONF_LASTSERVICE_TSTAMP, 0));
						
				sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_COUNT));
				sb.append(" " + total);
				sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_OPEN));
				sb.append(" " + open);
				sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_CLOSED));
				sb.append(" " + (total - open));
				sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_NEW_WIFIS));
				sb.append(" " + last);
				sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_LASTAPP_TSTAMP));
				sb.append("\n    " + df.format(lastapp) + " " + tf.format(lastapp));
				sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_LASTSERVICE_TSTAMP));
				sb.append("\n    " + df.format(lastservice) + " " + tf.format(lastservice));
			}
			finally
			{
				destroy_cursor(c);
			}
		}

		return sb.toString();
	}

	//
	// Events
	//

	public void onLocationChanged(Location location)
	{
		try
		{
			if (follow_me && location != null)
			{
				last_location = location;
				int e6lat = (int) (location.getLatitude() * 1E6);
				int e6lon = (int) (location.getLongitude() * 1E6);
				GeoPoint p = new GeoPoint(e6lat, e6lon);
				mapview.getController().animateTo(p);
				mapview.invalidate();
			}
		}
		catch (Exception e)
		{
			notify_error(e);
		}
	}

	//
	// Rendering
	//
	
	public class ExtraInfoOverlay extends Overlay
	{
		public Paint paint;
		public TextPaint paintText;
		private final int RED = Color.argb(255, 255, 0, 0);
		private final int GREEN = Color.argb(255, 0, 255, 0);
		
		public ExtraInfoOverlay()
		{
			paint = new Paint();
			paint.setAntiAlias(true);
			paintText = new TextPaint();
			paintText.setARGB(255, 0, 0, 0);
			paintText.setAntiAlias(true);
			paintText.setStrokeWidth(3);
			paintText.setTextSize(18);
		}
		
		@Override
		public void draw(Canvas canvas, MapView mapView, boolean shadow)
		{
			paint.setColor(service ? GREEN : RED);
			canvas.drawCircle(20, 25, 8, paint);
			canvas.drawText(getText(service ? R.string.OVERLAY_SERVICE_ON : R.string.OVERLAY_SERVICE_OFF).toString(), 35, 30, paintText);
		}
	}
	
	public class ScaleOverlay extends Overlay
	{
		public static final int BAR_WIDTH = 120;
		
		public static final int BAR_TEETH = 10;
		
		public boolean show_scale = true;
		
		public Paint paint;
		
		public TextPaint paintText;
		
		public ScaleOverlay()
		{
			paint = new Paint();
			paint.setAntiAlias(true);

			paintText = new TextPaint();
			paintText.setARGB(255, 0, 0, 0);
			paintText.setAntiAlias(true);
			paintText.setStrokeWidth(3);
			paintText.setTextSize(14);
		}
		
		@Override
		public void draw(Canvas canvas, MapView mapView, boolean shadow)
		{
			if (!show_scale)
				return;
			
			int x1 = 10, x2 = 10 + BAR_WIDTH;
			int y = mapview.getHeight() - 48;
			
			GeoPoint g1 = mapview.getProjection().fromPixels(x1, y);
			GeoPoint g2 = mapview.getProjection().fromPixels(x2, y);
			double lon1 = Math.toRadians((double) g1.getLongitudeE6() / 1000000);
			double lat1 = Math.toRadians((double) g1.getLatitudeE6() / 1000000);
			double lon2 = Math.toRadians((double) g2.getLongitudeE6() / 1000000);
			double lat2 = Math.toRadians((double) g2.getLatitudeE6() / 1000000);
			
			// From: http://www.movable-type.co.uk/scripts/latlong.html
			int R = 6371000;
			double dLat = lat2 - lat1;
			double dLon = lon2 - lon1;
			double a = 	Math.sin(dLat/2) * Math.sin(dLat/2) +
	        			Math.cos(lat1) * Math.cos(lat2) *
	        			Math.sin(dLon/2) * Math.sin(dLon/2);
			double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
			double d = R * c;
			
			String distance;
			if (d > 1000)
			{
				d /= 1000;
				distance = "" + new DecimalFormat("#.##").format(d) + "km";
			}
			else
				distance = "" + new DecimalFormat("#").format(d) + "m";	
			
			canvas.drawLine(x1, y, x2, y, paint);
			
			canvas.drawLine(x1, y + 2, x1, y - BAR_TEETH, paint);
			canvas.drawLine(x2, y + 2, x2, y - BAR_TEETH, paint);
			
			canvas.drawText(distance, x1 + 5, y - 5, paintText);			
		}
	}

	public class Overlays extends ItemizedOverlay<OverlayItem>
	{
		private static final int CIRCLE_RADIUS = 9;

		private static final int INFO_WINDOW_HEIGHT = 18;

		private static final int TEXT_SIZE = 12;

		private Paint paint_circle;
		
		private Paint paint_circle_stroke;

		private TextPaint paint_text;

		private Point point = new Point();

		private RectF rect = null;

		private int type;

		public boolean show_labels = false;

		private int quadrants_x = 2;

		private int quadrants_y = 2;

		private int quadrant_w = 0;

		private int quadrant_h = 0;

		private int max_radius_for_quadrant = 0;

		private int count = 0;

		private double avg_lat = 0;

		private double avg_lon = 0;

		private int zoom_divider = 1;
		
		private Double sizeRatio = null;

		public Overlays(int type, Drawable d)
		{
			super(d);
			populate();

			this.type = type;

			paint_circle = new Paint();
			paint_circle.setAntiAlias(true);
			
			paint_circle_stroke = new Paint();
			paint_circle_stroke.setStyle(Style.STROKE);
			paint_circle_stroke.setStrokeWidth(1);
			paint_circle_stroke.setAntiAlias(true);

			paint_text = new TextPaint();
			paint_text.setARGB(255, 255, 255, 255);
			paint_text.setAntiAlias(true);
			paint_text.setStrokeWidth(3);
			paint_text.setTextSize(TEXT_SIZE);
		}

		@Override
		public boolean onTouchEvent(MotionEvent event, MapView mapView)
		{
			return false;
		}

		@Override
		public boolean onTap(GeoPoint p, MapView mapView)
		{
			return false;
		}

		@Override
		public void draw(Canvas canvas, MapView mapView, boolean shadow)
		{
			// Lazy calculation for the size ratio
			if (sizeRatio == null)
			{
				int lesserMeasure = canvas.getWidth() > canvas.getHeight() ? canvas.getWidth() : canvas.getHeight();
				sizeRatio = ((double) lesserMeasure) / 460d;
				paint_text.setTextSize((int)(TEXT_SIZE * sizeRatio));
			}
			
			try
			{				
				if (type == Constants.OTYPE_OPEN_WIFI && !show_open)
					return;

				if (type == Constants.OTYPE_CLOSED_WIFI && !show_closed)
					return;
				
				if (type == Constants.OTYPE_WEP && !show_wep)
					return;

				if (database == null)
					return;

				if (type == Constants.OTYPE_MY_LOCATION)
				{
					if (last_location != null)
						draw_single(false, canvas, mapView, new GeoPoint((int) (last_location.getLatitude() * 1E6), (int) (last_location
								.getLongitude() * 1E6)), getResources().getString(R.string.GPS_LABEL_ME), 0);
					return;
				}

				GeoPoint top_left = mapView.getProjection().fromPixels(0, 0);
				GeoPoint bottom_right = mapView.getProjection().fromPixels(mapView.getWidth(), mapView.getHeight());

				String condition = "1";
				switch (type)
				{
					case Constants.OTYPE_OPEN_WIFI:
						condition = DBTableNetworks.TABLE_NETWORKS_OPEN_CONDITION;
						paint_circle.setARGB(192, 0, 200, 0);
						paint_circle_stroke.setARGB(192, 0, 200, 0);
						break;
					case Constants.OTYPE_CLOSED_WIFI:
						condition = DBTableNetworks.TABLE_NETWORKS_ONLY_CLOSED_CONDITION;
						paint_circle.setARGB(96, 255, 0, 0);
						paint_circle_stroke.setARGB(96, 255, 0, 0);
						break;
					case Constants.OTYPE_WEP:
						condition = DBTableNetworks.TABLE_NETWORKS_WEP_CONDITION;
						paint_circle.setARGB(192, 235, 160, 23);
						paint_circle_stroke.setARGB(192, 235, 160, 23);
						break;
				}
				
				Cursor c = null;
				try
				{
					c = database.query(DBTableNetworks.TABLE_NETWORKS, new String[] { DBTableNetworks.TABLE_NETWORKS_FIELD_LAT,
							DBTableNetworks.TABLE_NETWORKS_FIELD_LON, DBTableNetworks.TABLE_NETWORKS_FIELD_SSID,
							DBTableNetworks.TABLE_NETWORKS_FIELD_CAPABILITIES, DBTableNetworks.TABLE_NETWORKS_FIELD_LEVEL },
							DBTableNetworks.TABLE_NETWORKS_LOCATION_BETWEEN + " and " + condition, compose_latlon_between(top_left,
							bottom_right), null, null, null);

					if (c != null && c.moveToFirst())
					{
						if (c.getCount() <= Constants.MAX_WIFI_VISIBLE
								|| mapView.getZoomLevel() >= mapView.getMaxZoomLevel()
										- Constants.QUADRANT_ACTIVATION_AT_ZOOM_DIFFERENCE)
						{
							do
							{
                                String ssid = c.getString(2);
                                if (filter_enabled)
                                {
                                    boolean matches = ssid.matches(filter_regexp);
                                    if ((matches && filter_inverse) || (!matches && !filter_inverse))
                                        continue;
                                }

								draw_single(true, canvas, mapView, new GeoPoint((int) (c.getDouble(0) * 1E6),
										(int) (c.getDouble(1) * 1E6)), ssid, c.getInt(4));
							}
							while (c.moveToNext());
						}
						else
						{
							quadrant_w = (mapView.getWidth() / quadrants_x);
							quadrant_h = (mapView.getHeight() / quadrants_y);

							max_radius_for_quadrant = quadrant_w > quadrant_h ? quadrant_h / 2 : quadrant_w / 2;

							zoom_divider = mapView.getZoomLevel() - Constants.QUADRANT_DOTS_SCALING_FACTOR
									+ Constants.QUADRANT_DOTS_SCALING_CONSTANT;
							max_radius_for_quadrant /= zoom_divider >= 0 ? Constants.QUADRANT_DOTS_SCALING_CONSTANT
									: -zoom_divider;

							for (int x = 0; x < quadrants_x; x++)
							{
								for (int y = 0; y < quadrants_y; y++)
								{
									destroy_cursor(c);

									top_left = mapView.getProjection().fromPixels(quadrant_w * x, quadrant_h * y);
									bottom_right = mapView.getProjection().fromPixels(quadrant_w * x + quadrant_w,
											quadrant_h * y + quadrant_h);

									count = 0;
									c = database
											.query(
													DBTableNetworks.TABLE_NETWORKS,
													new String[] { DBTableNetworks.TABLE_NETWORKS_FIELD_COUNT_ROWID,
															DBTableNetworks.TABLE_NETWORKS_FIELD_SUM_LAT,
															DBTableNetworks.TABLE_NETWORKS_FIELD_SUM_LON },
													DBTableNetworks.TABLE_NETWORKS_LOCATION_BETWEEN + " and " + condition,
													compose_latlon_between(top_left, bottom_right), null, null, null);

									if (c != null && c.moveToFirst())
									{
										count = c.getInt(0);
										avg_lat = count > 0 ? c.getDouble(1) / count : 0;
										avg_lon = count > 0 ? c.getDouble(2) / count : 0;
									}

									if (count > 0)
										draw_sized_item(canvas, mapView, new GeoPoint((int) (avg_lat * 1E6),
												(int) (avg_lon * 1E6)), count > max_radius_for_quadrant ? max_radius_for_quadrant
												: count);
								}
							}
						}
					}
				}
				finally
				{
					destroy_cursor(c);
				}
			}
			catch (Exception e)
			{
				notify_error(e);
			}
		}

		private void draw_single(boolean iswifi, Canvas canvas, MapView mapView, GeoPoint geo_point, String title, int level)
		{
			point = mapView.getProjection().toPixels(geo_point, point);
			int bigness = (int) (CIRCLE_RADIUS) - (-level)/12;
			bigness = iswifi ? bigness : bigness / 2;
			bigness = bigness < 1 ? 0 : bigness;
			
			canvas.drawCircle(point.x, point.y, (int) (CIRCLE_RADIUS*sizeRatio), paint_circle_stroke);
			canvas.drawCircle(point.x, point.y, (int)(bigness*sizeRatio), paint_circle);

			if (show_labels && title != null && title.length() > 0)
			{
				rect = new RectF(0, 0, getTextWidth(title) + 4 * 2, (int)(INFO_WINDOW_HEIGHT * sizeRatio));
				rect.offset(point.x + (int)(5*sizeRatio), point.y + (int)(5*sizeRatio));
				canvas.drawRect(rect, paint_circle);
				canvas.drawText(title, point.x + 9, point.y + (int)(19*sizeRatio), paint_text);
			}
		}

		private void draw_sized_item(Canvas canvas, MapView mapView, GeoPoint geo_point, int radius)
		{
			point = mapView.getProjection().toPixels(geo_point, point);
			canvas.drawCircle(point.x, point.y, radius < CIRCLE_RADIUS ? CIRCLE_RADIUS : radius, paint_circle);
		}

		private String[] compose_latlon_between(GeoPoint top_left, GeoPoint bottom_right)
		{
			double lat_from = ((double) top_left.getLatitudeE6()) / 1E6;
			double lat_to = ((double) bottom_right.getLatitudeE6()) / 1E6;
			double lon_from = ((double) top_left.getLongitudeE6()) / 1E6;
			double lon_to = ((double) bottom_right.getLongitudeE6()) / 1E6;
			if (lat_from > lat_to)
			{
				double x = lat_to;
				lat_to = lat_from;
				lat_from = x;
			}
			if (lon_from > lon_to)
			{
				double x = lon_to;
				lon_to = lon_from;
				lon_from = x;
			}
			return new String[] { String.valueOf(lat_from), String.valueOf(lat_to), String.valueOf(lon_from), String.valueOf(lon_to) };
		}

		private int getTextWidth(String text)
		{
			int _count = text.length();
			float[] widths = new float[_count];
			paint_text.getTextWidths(text, widths);
			int textWidth = 0;
			for (int i = 0; i < _count; i++)
			{
				textWidth += widths[i];
			}
			return textWidth;
		}

		@Override
		protected OverlayItem createItem(int i)
		{
			return null;
		}

		@Override
		public int size()
		{
			return 0;
		}
	}

	//
	// Miscellaneous
	//

	public void save_app_tstamp()
	{
		settings_editor.putLong(Constants.CONF_LASTAPP_TSTAMP, System.currentTimeMillis());
		settings_editor.commit();
	}

	public void save_service_tstamp()
	{
		settings_editor.putLong(Constants.CONF_LASTSERVICE_TSTAMP, System.currentTimeMillis());
		settings_editor.commit();
	}

	private void notify_error(Exception e)
	{
		Log.e(this.getClass().getName(), e.getMessage(), e);
		Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
	}

	private static void destroy_cursor(Cursor c)
	{
		if (c != null)
		{
			if (!c.isClosed())
				c.close();
			c = null;
		}
	}

	public void onStatusChanged(String provider, int status, Bundle extras)
	{
	}

	@Override
	protected boolean isRouteDisplayed()
	{
		return false;
	}
}