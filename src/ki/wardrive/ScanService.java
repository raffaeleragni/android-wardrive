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

import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class ScanService extends Service
{
	private Object LAST_LOCATION_LOCK = new Object();

	private LocationManager location_manager;

	private Location last_location = null;

	private WifiManager wifi_manager;

	private boolean previous_wifi_state = false;

	private SQLiteDatabase database;

	private boolean started = false;

	private boolean notifications_enabled = false;

	private int gps_seconds = Constants.SERVICE_GPS_EVENT_WAIT;

	private int gps_meters = Constants.SERVICE_GPS_EVENT_METERS;

	private class IScanServiceImpl extends Binder implements IScanService
	{
		public boolean getNotificationsEnabled()
		{
			return notifications_enabled;
		}

		public void setNotificationsEnabled(boolean notificationsEnabled)
		{
			notifications_enabled = notificationsEnabled;
		}

		public int getGpsSeconds()
		{
			return gps_seconds;
		}
		
		public int getGpsMeters()
		{
			return gps_meters;
		}

		public void setGpsTimes(int s, int m)
		{
			gps_seconds = s;
			gps_meters = m;
			reset_gps_precision(gps_seconds, gps_meters);
		}
	}

	private IScanServiceImpl binder = new IScanServiceImpl();

	@Override
	public IBinder onBind(Intent intent)
	{
		return binder;
	}

	private void start_services()
	{
		try
		{
			location_manager = location_manager == null ? (LocationManager) getSystemService(LOCATION_SERVICE) : location_manager;
			wifi_manager = wifi_manager == null ? (WifiManager) getSystemService(Context.WIFI_SERVICE) : wifi_manager;
			database = database == null ? SQLiteDatabase.openOrCreateDatabase(Constants.DATABASE_FULL_PATH, null) : database;

			if (database != null)
			{
				database.execSQL(DBTableNetworks.OPTIMIZATION_SQL);
			}

			if (location_manager != null)
			{
				location_manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, gps_seconds, gps_meters, location_listener);
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

			started = true;
		}
		catch (Exception e)
		{
			notify_error(e);
		}
	}

	private void reset_gps_precision(int seconds, int meters)
	{
		if (location_manager != null)
		{
			location_manager.removeUpdates(location_listener);
			location_manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, seconds, meters, location_listener);
		}
	}

	private void stop_services()
	{
		try
		{
			if (location_manager != null)
			{
				location_manager.removeUpdates(location_listener);
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

			started = false;
		}
		catch (Exception e)
		{
			notify_error(e);
		}
	}

	private LocationListener location_listener = new LocationListener()
	{
		public void onStatusChanged(String provider, int status, Bundle extras)
		{
		}

		public void onProviderEnabled(String provider)
		{
		}

		public void onProviderDisabled(String provider)
		{
		}

		public void onLocationChanged(Location location)
		{
			if (location != null)
			{
				try
				{
					synchronized (LAST_LOCATION_LOCK)
					{
						last_location = location;
					}

					if (wifi_manager != null)
					{
						wifi_manager.startScan();
					}
				}
				catch (Exception e)
				{
					notify_error(e);
				}
			}
		}
	};

	private BroadcastReceiver wifiEvent = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Double lat = null, lon = null, alt = null;
			try
			{
				synchronized (LAST_LOCATION_LOCK)
				{
					if (last_location != null)
					{
						lat = last_location.getLatitude();
						lon = last_location.getLongitude();
						alt = last_location.getAltitude();
					}
				}

				if (lat != null && lon != null && alt != null)
				{
					boolean added = false;
					List<ScanResult> results = wifi_manager.getScanResults();
					for (ScanResult result : results)
					{
						added |= process_wifi_result(result, lat, lon, alt);
					}

					if (added)
					{
						notification_bar_message("New WiFi(s) added to database.");
					}
				}
			}
			catch (Exception e)
			{
				notify_error(e);
			}
		}
	};

	private boolean process_wifi_result(ScanResult result, double lat, double lon, double alt)
	{
		try
		{
			Cursor cursor = null;

			boolean toadd = false;
			boolean toupdate = false;
			boolean toupdate_coords = false;
			boolean toupdate_values = false;
			try
			{
				cursor = database.query(DBTableNetworks.TABLE_NETWORKS, new String[] {
						DBTableNetworks.TABLE_NETWORKS_FIELD_LEVEL, DBTableNetworks.TABLE_NETWORKS_FIELD_SSID,
						DBTableNetworks.TABLE_NETWORKS_FIELD_CAPABILITIES, DBTableNetworks.TABLE_NETWORKS_FIELD_FREQUENCY,
						DBTableNetworks.TABLE_NETWORKS_FIELD_TIMESTAMP }, DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID_EQUALS,
						new String[] { result.BSSID }, null, null, null);
				if (!cursor.moveToFirst())
				{
					toadd = true;
				}
				else
				{
					toupdate_values = !cursor.getString(1).equals(result.SSID)
							|| !cursor.getString(2).equals(result.capabilities) || cursor.getInt(3) != result.frequency;
					toupdate_coords = result.level > cursor.getInt(0);
					toupdate = toupdate_values || toupdate_coords;
				}
			}
			finally
			{
				destroy_cursor(cursor);
			}

			if (toadd)
			{
				ContentValues values = new ContentValues();
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID, result.BSSID);
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_SSID, result.SSID);
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_CAPABILITIES, result.capabilities);
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_FREQUENCY, result.frequency);
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_LEVEL, result.level);
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_LAT, lat);
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_LON, lon);
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_ALT, alt);
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_TIMESTAMP, System.currentTimeMillis());

				database.insert(DBTableNetworks.TABLE_NETWORKS, null, values);
			}
			else if (toupdate)
			{
				ContentValues values = new ContentValues();

				if (toupdate_values)
				{
					values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_SSID, result.SSID);
					values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_CAPABILITIES, result.capabilities);
					values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_FREQUENCY, result.frequency);
				}

				if (toupdate_coords)
				{
					values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_LAT, lat);
					values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_LON, lon);
					values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_ALT, alt);
					values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_LEVEL, result.level);
				}

				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_TIMESTAMP, System.currentTimeMillis());
				database.update(DBTableNetworks.TABLE_NETWORKS, values, DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID_EQUALS,
						new String[] { result.BSSID });
			}

			return toadd;
		}
		catch (Exception e)
		{
			notify_error(e);
			return false;
		}
	}

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
	public void onStart(Intent intent, int startId)
	{
		super.onStart(intent, startId);

		if (!started)
		{
			start_services();

			if (started)
			{
				Toast.makeText(this, R.string.SERVICE_STARTED, Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	public void onDestroy()
	{
		if (started)
		{
			stop_services();

			if (!started)
			{
				Toast.makeText(this, R.string.SERVICE_STOPPED, Toast.LENGTH_SHORT).show();
			}
		}

		super.onDestroy();
	}

	public void notification_bar_message(String message)
	{
		if (notifications_enabled)
		{
			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			Notification n = new Notification(R.drawable.icon, message, System.currentTimeMillis());
			n.defaults |= Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND;
			n.flags |= Notification.FLAG_AUTO_CANCEL;
			Context context = getApplicationContext();
			Intent notificationIntent = new Intent(this, ScanService.class);
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
			n.setLatestEventInfo(context, message, "", contentIntent);
			nm.notify(1, n);
		}
	}

	private void notify_error(Exception e)
	{
		Log.e(this.getClass().getName(), e.getMessage(), e);
	}
}
