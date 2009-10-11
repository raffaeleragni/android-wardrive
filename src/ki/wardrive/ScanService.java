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
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class ScanService extends Service
{
	private static final String DATABASE_FULL_PATH = "/sdcard/wardrive.db3";

	private Object LAST_LOCATION_LOCK = new Object();

	private LocationManager location_manager;

	private Location last_location = null;

	private WifiManager wifi_manager;

	private boolean previous_wifi_state = false;

	private SQLiteDatabase database;

	private int gpsWaitTime = 30000;

	private int gpsMeterSpan = 50;

	private boolean started = false;

	private void start_services()
	{
		try
		{
			location_manager = location_manager == null ? (LocationManager) getSystemService(LOCATION_SERVICE) : location_manager;
			wifi_manager = wifi_manager == null ? (WifiManager) getSystemService(Context.WIFI_SERVICE) : wifi_manager;
			database = database == null ? SQLiteDatabase.openOrCreateDatabase(DATABASE_FULL_PATH, null) : database;

			if (location_manager != null)
			{
				location_manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, gpsWaitTime, gpsMeterSpan,
						location_listener);
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
				synchronized (LAST_LOCATION_LOCK)
				{
					try
					{
						last_location = location;
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
		}
	};

	private BroadcastReceiver wifiEvent = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			synchronized (LAST_LOCATION_LOCK)
			{
				try
				{
					if (last_location != null)
					{
						List<ScanResult> results = wifi_manager.getScanResults();
						for (ScanResult result : results)
						{
							process_wifi_result(result, last_location.getLatitude(), last_location.getLongitude(), last_location
									.getAltitude());
						}
					}
				}
				catch (Exception e)
				{
					notify_error(e);
				}
			}
		}
	};

	private void process_wifi_result(ScanResult result, double lat, double lon, double alt)
	{
		try
		{
			Cursor cursor = null;

			boolean toadd = false;
			boolean toupdate = false;
			boolean toupdate_coords = false;
			try
			{
				cursor = database.query(DBTableNetworks.TABLE_NETWORKS,
						new String[] { DBTableNetworks.TABLE_NETWORKS_FIELD_LEVEL },
						DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID_EQUALS, new String[] { result.BSSID }, null, null, null);
				if (!cursor.moveToFirst())
				{
					toadd = true;
				}
				else
				{
					toupdate = true;
					toupdate_coords = result.level > cursor.getInt(0);
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
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_SSID, result.SSID);
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_CAPABILITIES, result.capabilities);
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_FREQUENCY, result.frequency);
				values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_TIMESTAMP, System.currentTimeMillis());
				if (toupdate_coords)
				{
					values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_LAT, lat);
					values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_LON, lon);
					values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_ALT, alt);
					values.put(DBTableNetworks.TABLE_NETWORKS_FIELD_LEVEL, result.level);
				}

				database.update(DBTableNetworks.TABLE_NETWORKS, values, DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID_EQUALS,
						new String[] { result.BSSID });
			}
		}
		catch (Exception e)
		{
			notify_error(e);
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

	public int getGpsWaitTime()
	{
		return gpsWaitTime;
	}

	public synchronized void setGpsWaitTime(int gpsWaitTime)
	{
		this.gpsWaitTime = gpsWaitTime;
		try
		{
			if (location_manager != null)
			{
				location_manager.removeUpdates(location_listener);
				location_manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, this.gpsWaitTime, gpsMeterSpan,
						location_listener);
			}
		}
		catch (Exception e)
		{
			notify_error(e);
		}
	}

	public int getGpsMeterSpan()
	{
		return gpsMeterSpan;
	}

	public synchronized void setGpsMeterSpan(int gpsMeterSpan)
	{
		this.gpsMeterSpan = gpsMeterSpan;
		try
		{
			if (location_manager != null)
			{
				location_manager.removeUpdates(location_listener);
				location_manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, gpsWaitTime, this.gpsMeterSpan,
						location_listener);
			}
		}
		catch (Exception e)
		{
			notify_error(e);
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
				NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				Notification n = new Notification(R.drawable.icon, "wardrive Service started", System.currentTimeMillis());
				Context context = getApplicationContext();
				Intent notificationIntent = new Intent(this, ScanService.class);
				PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
				n.setLatestEventInfo(context, "wardrive Service started", "", contentIntent);
				nm.notify(1, n);
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
				NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				Notification n = new Notification(R.drawable.icon, "wardrive Service stopped", System.currentTimeMillis());
				Context context = getApplicationContext();
				Intent notificationIntent = new Intent(this, ScanService.class);
				PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
				n.setLatestEventInfo(context, "wardrive Service stopped", "", contentIntent);
				nm.notify(1, n);
			}
		}

		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	private void notify_error(Exception e)
	{
		Log.e(this.getClass().getName(), e.getMessage(), e);
	}
}
