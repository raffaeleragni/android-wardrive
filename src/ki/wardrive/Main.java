/*
 *   wardrive - android wardriving application
 *   Copyright (C) 2009 Raffaele Ragni
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;

public class Main extends MapActivity implements LocationListener
{
	//
	// Interface Related
	//

	private static final int MENU_QUIT = 0;

	private static final int MENU_STATS = 1;

	private static final int MENU_MAX_WIFI_VISIBLE = 3;

	private static final int MENU_GPS_QUERIES_METERS = 4;

	private static final int MENU_TOGGLE_LABELS = 5;

	private static final int MENU_TOGGLE_FOLLOW_ME = 6;

	private static final int DIALOG_STATS = 0;

	private static final int DIALOG_MAX_WIFI_VISIBLE = 1;

	private static final int DIALOG_GPS_QUERIES_METERS = 2;

	private static final String LAST_LAT = "last_lat";

	private static final String LAST_LON = "last_lon";

	private static final String ZOOM_LEVEL = "zoom_level";

	private static final int DEFAULT_LAT = 0;

	private static final int DEFAULT_LON = 0;

	private static final int DEFAULT_ZOOM_LEVEL = 17;

	private static final int[] MAX_WIFI_VISIBLE_DATA = new int[] { 50, 500, 5000 };

	private static final String CONF_MAX_WIFI_VISIBLE = "max_wifi_visible";

	private static final int[] GPS_QUERIES_METERS_DATA = new int[] { 10, 50, 200 };

	private static final String CONF_GPS_QUERIES_METERS = "gps_queries_meters";

	private static final String CONF_SHOW_LABELS = "show_labels";

	private WakeLock wake_lock;

	private MapView mapview;

	private Overlays overlays_closed;

	private Overlays overlays_opened;

	private Overlays overlays_me;

	private SharedPreferences settings;

	private SharedPreferences.Editor settings_editor;

	private int MAX_WIFI_VISIBLE = 0;

	private int GPS_QUERIES_METERS = 1;

	public boolean show_labels = false;

	public boolean follow_me = true;

	//
	// DB Related
	//

	private static final String DATABASE_FULL_PATH = "/sdcard/wardrive.db3";

	private static final String TABLE_NETWORKS = "networks";

	private static final String TABLE_NETWORKS_FIELD_BSSID = "bssid";

	private static final String TABLE_NETWORKS_FIELD_BSSID_EQUALS = "bssid = ?";

	private static final String TABLE_NETWORKS_FIELD_SSID = "ssid";

	private static final String TABLE_NETWORKS_FIELD_CAPABILITIES = "capabilities";

	private static final String TABLE_NETWORKS_FIELD_LEVEL = "level";

	private static final String TABLE_NETWORKS_FIELD_FREQUENCY = "frequency";

	private static final String TABLE_NETWORKS_FIELD_LAT = "lat";

	private static final String TABLE_NETWORKS_FIELD_LON = "lon";

	private static final String TABLE_NETWORKS_FIELD_ALT = "alt";

	private static final String TABLE_NETWORKS_FIELD_TIMESTAMP = "timestamp";

	private static final String TABLE_NETWORKS_LOCATION_BETWEEN = "lat >= ? and lat <= ? and lon >= ? and lon <= ?";

	private static final String SELECT_COUNT_WIFIS = "select count(bssid) from networks";

	private static final String SELECT_COUNT_OPEN = "select count(bssid) from networks where capabilities = ''";

	private static final String CREATE_TABLE_NETWORKS = "create table if not exists networks (bssid text primary key, ssid text, capabilities text, level integer, frequency integer, lat real, lon real, alt real, timestamp integer)";

	private SQLiteDatabase database;

	private Locale language = Locale.ENGLISH;

	//
	// Location Related
	//

	public static final int GPS_EVENT_WAIT = 30000;

	private LocationManager location_manager;

	private Object LAST_LOCATION_LOCK = new Object();

	private Location last_location = null;

	//
	// WiFi Related
	//

	private boolean previous_wifi_state = false;

	private WifiManager wifi_manager;

	//
	// State change
	//

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		settings = getPreferences(MODE_PRIVATE);
		settings_editor = settings.edit();

		MAX_WIFI_VISIBLE = settings.getInt(CONF_MAX_WIFI_VISIBLE, MAX_WIFI_VISIBLE);
		GPS_QUERIES_METERS = settings.getInt(CONF_GPS_QUERIES_METERS, GPS_QUERIES_METERS);
		show_labels = settings.getBoolean(CONF_SHOW_LABELS, show_labels);

		GeoPoint point = new GeoPoint(settings.getInt(LAST_LAT, DEFAULT_LAT), settings.getInt(LAST_LON, DEFAULT_LON));

		mapview = (MapView) findViewById(R.id.mapview);
		mapview.getController().animateTo(point);
		mapview.getController().setZoom(settings.getInt(ZOOM_LEVEL, DEFAULT_ZOOM_LEVEL));
		mapview.setBuiltInZoomControls(true);
		mapview.setClickable(true);
		mapview.setLongClickable(true);

		overlays_closed = new Overlays(getResources().getDrawable(R.drawable.empty), 255, 0, 0);
		overlays_opened = new Overlays(getResources().getDrawable(R.drawable.empty), 0, 200, 0);
		overlays_me = new Overlays(getResources().getDrawable(R.drawable.empty), 0, 0, 255);

		overlays_closed.show_labels = show_labels;
		overlays_opened.show_labels = show_labels;
		overlays_me.show_labels = show_labels;

		//
		// Only one callback, it sets all layers
		//
		overlays_closed.setLazyLoadCallback(lazy_load_callback);

		mapview.getOverlays().add(overlays_closed);
		mapview.getOverlays().add(overlays_opened);
		mapview.getOverlays().add(overlays_me);
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wake_lock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "");
		wake_lock.acquire();

		start_services();
	}

	@Override
	protected void onPause()
	{
		wake_lock.release();
		stop_services();

		super.onPause();
	}

	@Override
	protected void onStop()
	{
		settings_editor.putInt(ZOOM_LEVEL, mapview.getZoomLevel());
		settings_editor.putInt(CONF_MAX_WIFI_VISIBLE, MAX_WIFI_VISIBLE);
		settings_editor.putInt(CONF_GPS_QUERIES_METERS, GPS_QUERIES_METERS);
		settings_editor.putBoolean(CONF_SHOW_LABELS, show_labels);
		if (last_location != null)
		{
			settings_editor.putInt(LAST_LAT, (int) (last_location.getLatitude() * 1E6));
			settings_editor.putInt(LAST_LON, (int) (last_location.getLongitude() * 1E6));
		}
		settings_editor.commit();

		stop_services();

		super.onStop();
	}

	//
	// Menu options
	//

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_QUIT, 0, Locales.get(language).MENU_QUIT_LABEL);
		menu.add(0, MENU_STATS, 0, Locales.get(language).MENU_STATS_LABEL);
		menu.add(0, MENU_MAX_WIFI_VISIBLE, 0, Locales.get(language).MENU_MAX_WIFI_VISIBLE_LABEL);
		menu.add(0, MENU_GPS_QUERIES_METERS, 0, Locales.get(language).MENU_GPS_QUERIES_METERS_LABEL);
		menu.add(0, MENU_TOGGLE_LABELS, 0, Locales.get(language).MENU_TOGGLE_LABELS_LABEL);
		menu.add(0, MENU_TOGGLE_FOLLOW_ME, 0, Locales.get(language).MENU_TOGGLE_FOLLOW_ME_LABEL);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case MENU_QUIT:
			{
				finish();
				return true;
			}
			case MENU_STATS:
			{
				showDialog(DIALOG_STATS);
				return true;
			}
			case MENU_MAX_WIFI_VISIBLE:
			{
				showDialog(DIALOG_MAX_WIFI_VISIBLE);
				break;
			}
			case MENU_GPS_QUERIES_METERS:
			{
				showDialog(DIALOG_GPS_QUERIES_METERS);
				break;
			}
			case MENU_TOGGLE_LABELS:
			{
				show_labels = !show_labels;
				overlays_closed.show_labels = show_labels;
				overlays_opened.show_labels = show_labels;
				overlays_me.show_labels = show_labels;
				mapview.invalidate();
				break;
			}
			case MENU_TOGGLE_FOLLOW_ME:
			{
				follow_me = !follow_me;
				break;
			}
		}
		return false;
	}

	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch (id)
		{
			case DIALOG_STATS:
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(print_stats());
				return builder.create();
			}
			case DIALOG_MAX_WIFI_VISIBLE:
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(Locales.get(language).MENU_MAX_WIFI_VISIBLE_LABEL);
				builder.setSingleChoiceItems(Locales.get(language).MAX_WIFI_VISIBLE_LABEL, MAX_WIFI_VISIBLE,
						new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int item)
							{
								MAX_WIFI_VISIBLE = item;
								closeOptionsMenu();
								dismissDialog(DIALOG_MAX_WIFI_VISIBLE);
							}
						});
				return builder.create();
			}
			case DIALOG_GPS_QUERIES_METERS:
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(Locales.get(language).MENU_GPS_QUERIES_METERS_LABEL);
				builder.setSingleChoiceItems(Locales.get(language).GPS_QUERIES_METERS_LABEL, GPS_QUERIES_METERS,
						new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int item)
							{
								GPS_QUERIES_METERS = item;
								stop_services();
								start_services();
								dismissDialog(DIALOG_GPS_QUERIES_METERS);
							}
						});
				return builder.create();
			}
		}

		return super.onCreateDialog(id);
	}

	private String print_stats()
	{
		StringBuffer sb = new StringBuffer();
		sb.append(Locales.get(language).MESSAGE_STATISTICS);

		if (database != null)
		{
			Cursor c = null;
			try
			{
				c = database.rawQuery(SELECT_COUNT_WIFIS, null);
				if (c.moveToFirst())
				{
					sb.append(Locales.get(language).MESSAGE_STATISTICS_COUNT);
					sb.append(c.getInt(0));
				}
				c.close();
				c = database.rawQuery(SELECT_COUNT_OPEN, null);
				if (c.moveToFirst())
				{
					sb.append(Locales.get(language).MESSAGE_STATISTICS_OPEN);
					sb.append(c.getInt(0));
				}
			}
			finally
			{
				destroy_cursor(c);
			}
		}

		return sb.toString();
	}

	//
	// Services
	//

	private void start_services()
	{
		location_manager = location_manager == null ? (LocationManager) getSystemService(LOCATION_SERVICE) : location_manager;
		wifi_manager = wifi_manager == null ? (WifiManager) getSystemService(Context.WIFI_SERVICE) : wifi_manager;

		if (location_manager != null)
		{
			location_manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_EVENT_WAIT,
					GPS_QUERIES_METERS_DATA[GPS_QUERIES_METERS], this);
		}

		if (wifi_manager != null)
		{
			previous_wifi_state = wifi_manager.isWifiEnabled();
			if (!previous_wifi_state)
			{
				wifi_manager.setWifiEnabled(true);
			}

			IntentFilter i = new IntentFilter();
			i.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
			registerReceiver(wifiEvent, i);
		}

		database = SQLiteDatabase.openOrCreateDatabase(DATABASE_FULL_PATH, null);

		if (database != null)
		{
			database.execSQL(CREATE_TABLE_NETWORKS);
		}
	}

	private void stop_services()
	{
		if (location_manager != null)
		{
			location_manager.removeUpdates(this);
			location_manager = null;
		}

		if (wifi_manager != null)
		{
			if (wifi_manager.isWifiEnabled() != previous_wifi_state)
			{
				wifi_manager.setWifiEnabled(previous_wifi_state);
			}
			unregisterReceiver(wifiEvent);
			wifi_manager = null;
		}

		if (database != null)
		{
			if (database.isOpen())
			{
				database.close();
			}
			database = null;
		}
	}

	//
	// Events
	//

	@Override
	public void onLocationChanged(Location location)
	{
		if (location != null)
		{
			synchronized (LAST_LOCATION_LOCK)
			{
				last_location = location;
				update_map(last_location);
				wifi_manager.startScan();
			}
		}
	}

	private BroadcastReceiver wifiEvent = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			boolean catched = false;
			double lat = 0, lon = 0, alt = 0;

			synchronized (LAST_LOCATION_LOCK)
			{
				if (last_location != null)
				{
					lat = last_location.getLatitude();
					lon = last_location.getLongitude();
					alt = last_location.getAltitude();
					catched = true;
				}

				if (catched)
				{
					List<ScanResult> results = wifi_manager.getScanResults();
					for (ScanResult result : results)
					{
						process_wifi_result(result, lat, lon, alt);
					}
				}
			}
		}
	};

	private void process_wifi_result(ScanResult result, double lat, double lon, double alt)
	{
		Cursor cursor = null;

		boolean toadd = false;
		boolean toupdate = false;
		try
		{
			cursor = database.query(TABLE_NETWORKS, new String[] { TABLE_NETWORKS_FIELD_LEVEL },
					TABLE_NETWORKS_FIELD_BSSID_EQUALS, new String[] { result.BSSID }, null, null, null);
			if (!cursor.moveToFirst())
			{
				toadd = true;
			}
			else
			{
				toupdate = result.level > cursor.getInt(0);
			}
		}
		finally
		{
			destroy_cursor(cursor);
		}

		if (toadd)
		{
			ContentValues values = new ContentValues();
			values.put(TABLE_NETWORKS_FIELD_BSSID, result.BSSID);
			values.put(TABLE_NETWORKS_FIELD_SSID, result.SSID);
			values.put(TABLE_NETWORKS_FIELD_CAPABILITIES, result.capabilities);
			values.put(TABLE_NETWORKS_FIELD_FREQUENCY, result.frequency);
			values.put(TABLE_NETWORKS_FIELD_LEVEL, result.level);
			values.put(TABLE_NETWORKS_FIELD_LAT, lat);
			values.put(TABLE_NETWORKS_FIELD_LON, lon);
			values.put(TABLE_NETWORKS_FIELD_ALT, alt);
			values.put(TABLE_NETWORKS_FIELD_TIMESTAMP, System.currentTimeMillis());

			database.insert(TABLE_NETWORKS, null, values);
		}
		else if (toupdate)
		{
			ContentValues values = new ContentValues();
			values.put(TABLE_NETWORKS_FIELD_SSID, result.SSID);
			values.put(TABLE_NETWORKS_FIELD_CAPABILITIES, result.capabilities);
			values.put(TABLE_NETWORKS_FIELD_FREQUENCY, result.frequency);
			values.put(TABLE_NETWORKS_FIELD_LEVEL, result.level);
			values.put(TABLE_NETWORKS_FIELD_LAT, lat);
			values.put(TABLE_NETWORKS_FIELD_LON, lon);
			values.put(TABLE_NETWORKS_FIELD_ALT, alt);
			values.put(TABLE_NETWORKS_FIELD_TIMESTAMP, System.currentTimeMillis());

			database.update(TABLE_NETWORKS, values, TABLE_NETWORKS_FIELD_BSSID_EQUALS, new String[] { result.BSSID });
		}
	}

	private void update_map(Location location)
	{
		int e6lat = (int) (location.getLatitude() * 1E6);
		int e6lon = (int) (location.getLongitude() * 1E6);
		GeoPoint p = new GeoPoint(e6lat, e6lon);
		ArrayList<OverlayItem> me = new ArrayList<OverlayItem>();
		me.add(new OverlayItem(p, "ME", ""));
		overlays_me.setItems(me);
		if (follow_me)
		{
			mapview.getController().animateTo(p);
		}
		mapview.invalidate();
	}

	private Runnable lazy_load_callback = new Runnable()
	{
		@Override
		public void run()
		{
			if (database == null)
			{
				return;
			}

			GeoPoint top_left = mapview.getProjection().fromPixels(0, 0);
			GeoPoint bottom_right = mapview.getProjection().fromPixels(mapview.getWidth(), mapview.getHeight());

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

			Cursor c = null;
			try
			{
				ArrayList<OverlayItem> itemso = new ArrayList<OverlayItem>();
				ArrayList<OverlayItem> itemsc = new ArrayList<OverlayItem>();

				c = database.query(TABLE_NETWORKS, new String[] { TABLE_NETWORKS_FIELD_LAT, TABLE_NETWORKS_FIELD_LON,
						TABLE_NETWORKS_FIELD_CAPABILITIES, TABLE_NETWORKS_FIELD_SSID }, TABLE_NETWORKS_LOCATION_BETWEEN,
						new String[] { "" + lat_from, "" + lat_to, "" + lon_from, "" + lon_to }, null, null, null);

				if (c != null && c.moveToFirst())
				{
					do
					{
						OverlayItem item = new OverlayItem(new GeoPoint((int) (c.getDouble(0) * 1E6),
								(int) (c.getDouble(1) * 1E6)), c.getString(3), c.getString(3));
						if (c.getString(2).length() > 0)
						{
							itemsc.add(item);
						}
						else
						{
							itemso.add(item);
						}
					}
					while (c.moveToNext() && c.getPosition() < MAX_WIFI_VISIBLE_DATA[MAX_WIFI_VISIBLE]);
				}

				overlays_opened.setItems(itemso);
				overlays_closed.setItems(itemsc);
			}
			finally
			{
				destroy_cursor(c);
			}
		}
	};

	//
	// Miscellaneous
	//

	private static void destroy_cursor(Cursor c)
	{
		if (c != null)
		{
			if (!c.isClosed())
			{
				c.close();
			}
			c = null;
		}
	}

	@Override
	public void onProviderDisabled(String provider)
	{
	}

	@Override
	public void onProviderEnabled(String provider)
	{
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras)
	{
	}

	@Override
	protected boolean isRouteDisplayed()
	{
		return false;
	}
}