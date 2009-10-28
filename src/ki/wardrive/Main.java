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
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
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

	private SharedPreferences settings;

	private SharedPreferences.Editor settings_editor;

	private WakeLock wake_lock;

	public boolean service = true;

	private Intent service_intent = null;

	//
	// Interface Related
	//

	private SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

	private MapView mapview;

	private Overlays overlays_closed;

	private Overlays overlays_opened;

	private Overlays overlays_me;

	public boolean show_labels = false;

	public boolean follow_me = true;

	public boolean map_mode = false;

	public boolean show_open = true;

	public boolean show_closed = false;

	private boolean sending_sync = false;

	private ProgressDialog progressDialog;

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
	// Status change
	//

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		try
		{
			settings = getPreferences(MODE_PRIVATE);
			settings_editor = settings.edit();

			show_labels = settings.getBoolean(Constants.CONF_SHOW_LABELS, show_labels);
			follow_me = settings.getBoolean(Constants.CONF_FOLLOW, follow_me);
			map_mode = settings.getBoolean(Constants.CONF_MAP_MODE, map_mode);
			show_open = settings.getBoolean(Constants.CONF_SHOW_OPEN, show_open);
			show_closed = settings.getBoolean(Constants.CONF_SHOW_CLOSED, show_closed);

			GeoPoint point = new GeoPoint(settings.getInt(Constants.LAST_LAT, Constants.DEFAULT_LAT), settings.getInt(
					Constants.LAST_LON, Constants.DEFAULT_LON));

			mapview = (MapView) findViewById(R.id.mapview);
			mapview.getController().animateTo(point);
			mapview.getController().setZoom(settings.getInt(Constants.ZOOM_LEVEL, Constants.DEFAULT_ZOOM_LEVEL));
			mapview.setBuiltInZoomControls(true);
			mapview.setClickable(true);
			mapview.setLongClickable(true);
			mapview.setSatellite(map_mode);

			Drawable d = getResources().getDrawable(R.drawable.empty);

			overlays_closed = new Overlays(Constants.OTYPE_CLOSED_WIFI, d, 96, 255, 0, 0);
			overlays_opened = new Overlays(Constants.OTYPE_OPEN_WIFI, d, 192, 0, 200, 0);
			overlays_me = new Overlays(Constants.OTYPE_MY_LOCATION, d, 255, 0, 0, 255);

			overlays_closed.show_labels = show_labels;
			overlays_opened.show_labels = show_labels;
			overlays_me.show_labels = show_labels;

			mapview.getOverlays().add(overlays_closed);
			mapview.getOverlays().add(overlays_opened);
			mapview.getOverlays().add(overlays_me);

			database = SQLiteDatabase.openOrCreateDatabase(DBTableNetworks.DATABASE_FULL_PATH, null);
			if (database != null)
			{
				database.execSQL(DBTableNetworks.CREATE_TABLE_NETWORKS);
				database.execSQL(DBTableNetworks.CREATE_INDEX_LATLON);
				database.execSQL(DBTableNetworks.CREATE_INDEX_CAPABILITIES);
				database.execSQL(DBTableNetworks.OPTIMIZATION_SQL);
			}

			service_intent = new Intent();
			service_intent.setClass(this, ScanService.class);
			startService(service_intent);

			location_manager = (LocationManager) getSystemService(LOCATION_SERVICE);

			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wake_lock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, this.getClass().getName());
		}
		catch (Exception e)
		{
			notify_error(e);
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		if (wake_lock != null && !wake_lock.isHeld())
		{
			wake_lock.acquire();
		}
	}

	@Override
	protected void onPause()
	{
		if (wake_lock != null && wake_lock.isHeld())
		{
			wake_lock.release();
		}

		super.onPause();
	}

	@Override
	protected void onStart()
	{
		super.onStart();

		try
		{
			if (location_manager != null)
			{
				location_manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, Constants.MAPS_GPS_EVENT_WAIT,
						Constants.MAPS_GPS_EVENT_METERS, Main.this);
			}
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
			{
				location_manager.removeUpdates(Main.this);
			}

			settings_editor.putInt(Constants.ZOOM_LEVEL, mapview.getZoomLevel());
			settings_editor.putBoolean(Constants.CONF_SHOW_LABELS, show_labels);
			settings_editor.putBoolean(Constants.CONF_FOLLOW, follow_me);
			settings_editor.putBoolean(Constants.CONF_MAP_MODE, map_mode);
			settings_editor.putBoolean(Constants.CONF_SHOW_OPEN, show_open);
			settings_editor.putBoolean(Constants.CONF_SHOW_CLOSED, show_closed);
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
		menu.findItem(R.menu_id.FOLLOW).setChecked(follow_me);
		menu.findItem(R.menu_id.MAP_MODE).setChecked(map_mode);
		menu.findItem(R.menu_id.LABELS).setChecked(show_labels);
		menu.findItem(R.menu_id.SHOW_CLOSED).setChecked(show_closed);
		menu.findItem(R.menu_id.SHOW_OPEN).setChecked(show_open);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.menu_id.QUIT:
			{
				stopService(service_intent);
				save_service_tstamp();
				save_app_tstamp();

				finish();

				return true;
			}
			case R.menu_id.STATS:
			{
				showDialog(Constants.DIALOG_STATS);
				return true;
			}
			case R.menu_id.LABELS:
			{
				show_labels = !show_labels;
				overlays_closed.show_labels = show_labels;
				overlays_opened.show_labels = show_labels;
				overlays_me.show_labels = show_labels;
				item.setChecked(show_labels);
				mapview.invalidate();
				break;
			}
			case R.menu_id.FOLLOW:
			{
				follow_me = !follow_me;
				item.setChecked(follow_me);
				break;
			}
			case R.menu_id.MAP_MODE:
			{
				map_mode = !map_mode;
				item.setChecked(map_mode);
				mapview.setSatellite(map_mode);
				break;
			}
			case R.menu_id.ABOUT:
			{
				showDialog(Constants.DIALOG_ABOUT);
				break;
			}
			case R.menu_id.SHOW_OPEN:
			{
				show_open = !show_open;
				item.setChecked(show_open);
				mapview.invalidate();
				break;
			}
			case R.menu_id.SHOW_CLOSED:
			{
				show_closed = !show_closed;
				item.setChecked(show_closed);
				mapview.invalidate();
				break;
			}
			case R.menu_id.SERVICE:
			{
				service = !service;
				if (service)
				{
					startService(service_intent);
				}
				else
				{
					stopService(service_intent);
					save_service_tstamp();
				}
				break;
			}
			case R.menu_id.DELETE:
			{
				showDialog(Constants.DIALOG_DELETE_ALL_WIFI);
				break;
			}
			case R.menu_id.KML_EXPORT:
			{
				toast(getResources().getString(R.string.MESSAGE_STARTING_KML_EXPORT));
				new Thread(kml_export_proc).start();

				break;
			}
			case R.menu_id.SYNC_ONLINE_DB:
			{
				showDialog(Constants.DIALOG_SYNC_PROGRESS);
				if (!sending_sync)
				{
					new Thread(sync_online_proc).start();
				}

				break;
			}
		}
		return false;
	}

	private Runnable kml_export_proc = new Runnable()
	{
		public void run()
		{
			if (KMLExport.export(database, new File(Constants.KML_EXPORT_FILE)))
			{
				message_handler.sendMessage(Message.obtain(message_handler, Constants.EVENT_KML_EXPORT_DONE));
			}
		}
	};

	private Runnable sync_online_proc = new Runnable()
	{
		public void run()
		{
			try
			{
				long tstamp = settings.getLong(Constants.CONF_SYNC_TSTAMP, 0);
				long newtstamp = System.currentTimeMillis();

				URL url = new URL(Constants.SYNC_ONLINE_URL);
				sending_sync = true;
				int inserted_count = SyncOnlineExport.export(tstamp, database, url, message_handler);
				Message msg = Message.obtain(message_handler, Constants.EVENT_SYNC_ONLINE_DONE);
				Bundle b = new Bundle();
				b.putInt(Constants.EVENT_SYNC_ONLINE_PROGRESS_PAR_INSERTED_COUNT, inserted_count);
				msg.setData(b);
				message_handler.sendMessage(msg);

				settings_editor.putLong(Constants.CONF_SYNC_TSTAMP, newtstamp);
				settings_editor.commit();
			}
			catch (Exception e)
			{
				Message msg = Message.obtain(message_handler, Constants.EVENT_SYNC_ONLINE_DONE);
				Bundle b = new Bundle();
				b.putSerializable("exception", e);
				msg.setData(b);
				message_handler.sendMessage(msg);
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
				{
					toast(getResources().getString(R.string.MESSAGE_SUCCESFULLY_EXPORTED_KML));
					break;
				}

				case Constants.EVENT_SYNC_ONLINE_PROGRESS:
				{
					if (progressDialog.isShowing())
					{
						progressDialog.setProgress(msg.getData().getInt(Constants.EVENT_SYNC_ONLINE_PROGRESS_PAR_INSERTED_COUNT));
					}
					break;
				}

				case Constants.EVENT_SYNC_ONLINE_DONE:
				{
					sending_sync = false;
					if (progressDialog.isShowing())
					{
						dismissDialog(Constants.DIALOG_SYNC_PROGRESS);
					}
					toast(getResources().getString(R.string.MESSAGE_SUCCESFULLY_SYNC_ONLINE) + " "
							+ msg.getData().getInt(Constants.EVENT_SYNC_ONLINE_PROGRESS_PAR_INSERTED_COUNT));
					break;
				}

				case Constants.EVENT_NOTIFY_ERROR:
				{
					notify_error((Exception) msg.getData().getSerializable("exception"));
					break;
				}
			}
		}
	};

	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch (id)
		{
			case Constants.DIALOG_STATS:
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(print_stats());
				return builder.create();
			}
			case Constants.DIALOG_ABOUT:
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(getResources().getText(R.string.ABOUT_BOX));
				return builder.create();
			}
			case Constants.DIALOG_DELETE_ALL_WIFI:
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(getResources().getText(R.string.MENU_DELETE_WARNING_LABEL));
				builder.setCancelable(false);
				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int id)
					{
						delete_all_wifi();
					}
				});
				builder.setNegativeButton("No", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int id)
					{
						dialog.cancel();
					}
				});
				return builder.create();
			}
			case Constants.DIALOG_SYNC_PROGRESS:
			{
				progressDialog = new ProgressDialog(this);
				progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				progressDialog.setMessage(getResources().getString(R.string.MESSAGE_STARTING_SYNC_ONLINE));
				progressDialog.setProgress(0);
				progressDialog.setCancelable(true);
				return progressDialog;
			}
		}

		return super.onCreateDialog(id);
	}

	private void delete_all_wifi()
	{
		if (database != null)
		{
			database.execSQL(DBTableNetworks.DELETE_ALL_WIFI);
		}
	}

	private String print_stats()
	{
		StringBuffer sb = new StringBuffer();
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

				sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_COUNT));
				sb.append(" " + total);
				sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_OPEN));
				sb.append(" " + open);
				sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_CLOSED));
				sb.append(" " + (total - open));
				sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_NEW_WIFIS));
				sb.append(" " + last);
				sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_LASTAPP_TSTAMP));
				sb.append("\n    " + sdf.format(new Date(settings.getLong(Constants.CONF_LASTAPP_TSTAMP, 0))));
				sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_LASTSERVICE_TSTAMP));
				sb.append("\n    " + sdf.format(new Date(settings.getLong(Constants.CONF_LASTSERVICE_TSTAMP, 0))));
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

	public class Overlays extends ItemizedOverlay<OverlayItem>
	{
		private static final int CIRCLE_RADIUS = 4;

		private static final int INFO_WINDOW_HEIGHT = 16;

		private Paint paint_circle;

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

		public Overlays(int type, Drawable d, int a, int r, int g, int b)
		{
			super(d);
			populate();

			this.type = type;

			paint_circle = new Paint();
			paint_circle.setARGB(a, r, g, b);
			paint_circle.setAntiAlias(true);

			paint_text = new TextPaint();
			paint_text.setARGB(255, 255, 255, 255);
			paint_text.setAntiAlias(true);
			paint_text.setStrokeWidth(3);
			paint_text.setTextSize(14);
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
			try
			{
				if (type == Constants.OTYPE_OPEN_WIFI && !show_open)
				{
					return;
				}

				if (type == Constants.OTYPE_CLOSED_WIFI && !show_closed)
				{
					return;
				}

				if (database == null)
				{
					return;
				}

				if (type == Constants.OTYPE_MY_LOCATION)
				{
					if (last_location != null)
					{
						draw_single(canvas, mapView, new GeoPoint((int) (last_location.getLatitude() * 1E6), (int) (last_location
								.getLongitude() * 1E6)), getResources().getString(R.string.GPS_LABEL_ME));
					}
					return;
				}

				GeoPoint top_left = mapView.getProjection().fromPixels(0, 0);
				GeoPoint bottom_right = mapView.getProjection().fromPixels(mapView.getWidth(), mapView.getHeight());

				Cursor c = null;
				try
				{
					c = database.query(DBTableNetworks.TABLE_NETWORKS, new String[] { DBTableNetworks.TABLE_NETWORKS_FIELD_LAT,
							DBTableNetworks.TABLE_NETWORKS_FIELD_LON, DBTableNetworks.TABLE_NETWORKS_FIELD_SSID },
							DBTableNetworks.TABLE_NETWORKS_LOCATION_BETWEEN
									+ " and "
									+ (Constants.OTYPE_CLOSED_WIFI == type ? DBTableNetworks.TABLE_NETWORKS_CLOSED_CONDITION
											: DBTableNetworks.TABLE_NETWORKS_OPEN_CONDITION), compose_latlon_between(top_left,
									bottom_right), null, null, null);

					if (c != null && c.moveToFirst())
					{
						if (c.getCount() <= Constants.MAX_WIFI_VISIBLE
								|| mapView.getZoomLevel() >= mapView.getMaxZoomLevel()
										- Constants.QUADRANT_ACTIVATION_AT_ZOOM_DIFFERENCE)
						{
							do
							{
								draw_single(canvas, mapView, new GeoPoint((int) (c.getDouble(0) * 1E6),
										(int) (c.getDouble(1) * 1E6)), c.getString(2));
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
													new String[] { DBTableNetworks.TABLE_NETWORKS_FIELD_COUNT_BSSID,
															DBTableNetworks.TABLE_NETWORKS_FIELD_SUM_LAT,
															DBTableNetworks.TABLE_NETWORKS_FIELD_SUM_LON },
													DBTableNetworks.TABLE_NETWORKS_LOCATION_BETWEEN
															+ " and "
															+ (Constants.OTYPE_CLOSED_WIFI == type ? DBTableNetworks.TABLE_NETWORKS_CLOSED_CONDITION
																	: DBTableNetworks.TABLE_NETWORKS_OPEN_CONDITION),
													compose_latlon_between(top_left, bottom_right), null, null, null);

									if (c != null && c.moveToFirst())
									{
										count = c.getInt(0);
										avg_lat = count > 0 ? c.getDouble(1) / count : 0;
										avg_lon = count > 0 ? c.getDouble(2) / count : 0;
									}

									if (count > 0)
									{
										draw_sized_item(canvas, mapView, new GeoPoint((int) (avg_lat * 1E6),
												(int) (avg_lon * 1E6)), count > max_radius_for_quadrant ? max_radius_for_quadrant
												: count);
									}
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

		private void draw_single(Canvas canvas, MapView mapView, GeoPoint geo_point, String title)
		{
			point = mapView.getProjection().toPixels(geo_point, point);
			canvas.drawCircle(point.x, point.y, CIRCLE_RADIUS, paint_circle);

			if (show_labels && title != null && title.length() > 0)
			{
				rect = new RectF(0, 0, getTextWidth(title) + 4 * 2, INFO_WINDOW_HEIGHT);
				rect.offset(point.x + 2, point.y + 2);
				canvas.drawRect(rect, paint_circle);
				canvas.drawText(title, point.x + 6, point.y + 14, paint_text);
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
			return new String[] { "" + lat_from, "" + lat_to, "" + lon_from, "" + lon_to };
		}

		private int getTextWidth(String text)
		{
			int count = text.length();
			float[] widths = new float[count];
			paint_text.getTextWidths(text, widths);
			int textWidth = 0;
			for (int i = 0; i < count; i++)
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

	public void toast(String message)
	{
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}

	public void notification_bar_message(String message)
	{
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification n = new Notification(R.drawable.icon, message, System.currentTimeMillis());
		Context context = getApplicationContext();
		Intent notificationIntent = new Intent(this, ScanService.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		n.setLatestEventInfo(context, message, "", contentIntent);
		nm.notify(1, n);
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
			{
				c.close();
			}
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