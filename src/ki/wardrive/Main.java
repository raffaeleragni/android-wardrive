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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
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

	private static int OTYPE_MY_LOCATION = 0;

	private static int OTYPE_OPEN_WIFI = OTYPE_MY_LOCATION + 1;

	private static int OTYPE_CLOSED_WIFI = OTYPE_OPEN_WIFI + 1;

	private static final String LAST_LAT = "last_lat";

	private static final String LAST_LON = "last_lon";

	private static final String ZOOM_LEVEL = "zoom_level";

	private static final String CONF_SHOW_LABELS = "show_labels";

	private static final String CONF_FOLLOW = "follow";

	private static final String CONF_MAP_MODE = "map_mode";

	private static final int MAX_WIFI_VISIBLE = 20;

	private SharedPreferences settings;

	private SharedPreferences.Editor settings_editor;

	private WakeLock wake_lock;

	//
	// Interface Related
	//

	private static final int DIALOG_STATS = 0;

	private static final int DIALOG_ABOUT = DIALOG_STATS + 1;

	private static final int QUADRANT_DOTS_SCALING_CONSTANT = 3;

	private static final int QUADRANT_DOTS_SCALING_FACTOR = 12;

	private static final int QUADRANT_ACTIVATION_AT_ZOOM_DIFFERENCE = 3;

	private MapView mapview;

	private Overlays overlays_closed;

	private Overlays overlays_opened;

	private Overlays overlays_me;

	public boolean show_labels = false;

	public boolean follow_me = true;

	public boolean map_mode = false;

	//
	// DB Related
	//

	private static final String DATABASE_FULL_PATH = "/sdcard/wardrive.db3";

	private SQLiteDatabase database;

	//
	// Location Related
	//

	private static final int DEFAULT_LAT = 0;

	private static final int DEFAULT_LON = 0;

	private static final int DEFAULT_ZOOM_LEVEL = 17;

	public static final int GPS_EVENT_WAIT = 30000;

	public static final int GPS_EVENT_METERS = 30;

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

			show_labels = settings.getBoolean(CONF_SHOW_LABELS, show_labels);
			follow_me = settings.getBoolean(CONF_FOLLOW, follow_me);
			map_mode = settings.getBoolean(CONF_MAP_MODE, map_mode);

			GeoPoint point = new GeoPoint(settings.getInt(LAST_LAT, DEFAULT_LAT), settings.getInt(LAST_LON, DEFAULT_LON));

			mapview = (MapView) findViewById(R.id.mapview);
			mapview.getController().animateTo(point);
			mapview.getController().setZoom(settings.getInt(ZOOM_LEVEL, DEFAULT_ZOOM_LEVEL));
			mapview.setBuiltInZoomControls(true);
			mapview.setClickable(true);
			mapview.setLongClickable(true);
			mapview.setSatellite(map_mode);

			Drawable d = getResources().getDrawable(R.drawable.empty);

			overlays_closed = new Overlays(OTYPE_CLOSED_WIFI, d, 96, 255, 0, 0);
			overlays_opened = new Overlays(OTYPE_OPEN_WIFI, d, 192, 0, 200, 0);
			overlays_me = new Overlays(OTYPE_MY_LOCATION, d, 255, 0, 0, 255);

			overlays_closed.show_labels = show_labels;
			overlays_opened.show_labels = show_labels;
			overlays_me.show_labels = show_labels;

			mapview.getOverlays().add(overlays_closed);
			mapview.getOverlays().add(overlays_opened);
			mapview.getOverlays().add(overlays_me);

			Intent i = new Intent();
			i.setClass(this, ScanService.class);
			startService(i);

			database = SQLiteDatabase.openOrCreateDatabase(DATABASE_FULL_PATH, null);
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
				location_manager
						.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_EVENT_WAIT, GPS_EVENT_METERS, Main.this);
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

			settings_editor.putInt(ZOOM_LEVEL, mapview.getZoomLevel());
			settings_editor.putBoolean(CONF_SHOW_LABELS, show_labels);
			settings_editor.putBoolean(CONF_FOLLOW, follow_me);
			settings_editor.putBoolean(CONF_MAP_MODE, map_mode);
			GeoPoint p = mapview.getProjection().fromPixels(mapview.getWidth() / 2, mapview.getHeight() / 2);
			settings_editor.putInt(LAST_LAT, p.getLatitudeE6());
			settings_editor.putInt(LAST_LON, p.getLongitudeE6());
			settings_editor.commit();
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

	@Override
	public void onProviderDisabled(String provider)
	{
		if (LocationManager.GPS_PROVIDER.equals(provider) && location_manager != null)
		{
			location_manager.removeUpdates(Main.this);
		}
	}

	@Override
	public void onProviderEnabled(String provider)
	{
		if (LocationManager.GPS_PROVIDER.equals(provider) && location_manager != null)
		{
			location_manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_EVENT_WAIT, GPS_EVENT_METERS, Main.this);
		}
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
		/*((MenuItem) findViewById(R.menu_id.LABELS)).setChecked(show_labels);
		((MenuItem) findViewById(R.menu_id.FOLLOW)).setChecked(follow_me);
		((MenuItem) findViewById(R.menu_id.MAP_MODE)).setChecked(map_mode);*/

		// TODO Auto-generated method stub
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
			case R.menu_id.QUIT:
			{
				Intent i = new Intent();
				i.setClass(this, ScanService.class);
				stopService(i);

				finish();

				return true;
			}
			case R.menu_id.STATS:
			{
				showDialog(DIALOG_STATS);
				return true;
			}
			case R.menu_id.LABELS:
			{
				show_labels = !show_labels;
				overlays_closed.show_labels = show_labels;
				overlays_opened.show_labels = show_labels;
				overlays_me.show_labels = show_labels;
				mapview.invalidate();
				break;
			}
			case R.menu_id.FOLLOW:
			{
				follow_me = !follow_me;
				break;
			}
			case R.menu_id.MAP_MODE:
			{
				map_mode = !map_mode;
				mapview.setSatellite(map_mode);
				break;
			}
			case R.menu_id.ABOUT:
			{
				showDialog(DIALOG_ABOUT);
				break;
			}
		}
		return false;
	}

	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch (id) {
			case DIALOG_STATS:
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(print_stats());
				return builder.create();
			}
			case DIALOG_ABOUT:
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(getResources().getText(R.string.ABOUT_BOX));
				return builder.create();
			}
		}

		return super.onCreateDialog(id);
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
				c = database.rawQuery(DBTableNetworks.SELECT_COUNT_WIFIS, null);
				if (c.moveToFirst())
				{
					sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_COUNT));
					sb.append(c.getInt(0));
				}
				c.close();
				c = database.rawQuery(DBTableNetworks.SELECT_COUNT_OPEN, null);
				if (c.moveToFirst())
				{
					sb.append(getResources().getString(R.string.MESSAGE_STATISTICS_OPEN));
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
	// Events
	//

	@Override
	public void onLocationChanged(Location location)
	{
		if (location != null)
		{
			try
			{
				last_location = location;
				int e6lat = (int) (location.getLatitude() * 1E6);
				int e6lon = (int) (location.getLongitude() * 1E6);
				GeoPoint p = new GeoPoint(e6lat, e6lon);
				if (follow_me)
				{
					mapview.getController().animateTo(p);
				}
				mapview.invalidate();
			}
			catch (Exception e)
			{
				notify_error(e);
			}
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
				if (database == null)
				{
					return;
				}

				if (OTYPE_MY_LOCATION == type)
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
									+ (OTYPE_CLOSED_WIFI == type ? DBTableNetworks.TABLE_NETWORKS_CLOSED_CONDITION
											: DBTableNetworks.TABLE_NETWORKS_OPEN_CONDITION), compose_latlon_between(top_left,
									bottom_right), null, null, null);

					if (c != null && c.moveToFirst())
					{
						if (c.getCount() <= MAX_WIFI_VISIBLE
								|| mapView.getZoomLevel() >= mapView.getMaxZoomLevel() - QUADRANT_ACTIVATION_AT_ZOOM_DIFFERENCE)
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

							zoom_divider = mapView.getZoomLevel() - QUADRANT_DOTS_SCALING_FACTOR + QUADRANT_DOTS_SCALING_CONSTANT;
							max_radius_for_quadrant /= zoom_divider >= 0 ? QUADRANT_DOTS_SCALING_CONSTANT : -zoom_divider;

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
															+ (OTYPE_CLOSED_WIFI == type ? DBTableNetworks.TABLE_NETWORKS_CLOSED_CONDITION
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