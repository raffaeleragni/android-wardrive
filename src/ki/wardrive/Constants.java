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

public class Constants
{
	public static final String SYNC_ONLINE_URL = "http://wardrivedb.appspot.com/main";

	public static final int SYNC_ONLINE_BUFFER = 20;

	public static final String DATABASE_FULL_PATH = "/sdcard/wardrive.db3";

	public static final String KML_EXPORT_FILE = "/sdcard/wardrive.kml";

	public static final int DEFAULT_LAT = 0;

	public static final int DEFAULT_LON = 0;

	public static final int DEFAULT_ZOOM_LEVEL = 17;

	public static final int MAX_WIFI_VISIBLE = 50;

	public static final int QUADRANT_DOTS_SCALING_CONSTANT = 3;

	public static final int QUADRANT_DOTS_SCALING_FACTOR = 12;

	public static final int QUADRANT_ACTIVATION_AT_ZOOM_DIFFERENCE = 3;

	public static final int MAPS_GPS_EVENT_WAIT = 3000;

	public static final int MAPS_GPS_EVENT_METERS = 0;

	public static final int SERVICE_GPS_EVENT_WAIT = 3000;

	public static final int SERVICE_GPS_EVENT_METERS = 3;

	public static final boolean SERVICE_TOASTS_ENABLED = false;

	public static final int OTYPE_MY_LOCATION = 0;

	public static final int OTYPE_OPEN_WIFI = OTYPE_MY_LOCATION + 1;

	public static final int OTYPE_CLOSED_WIFI = OTYPE_OPEN_WIFI + 1;

	public static final String LAST_LAT = "last_lat";

	public static final String LAST_LON = "last_lon";

	public static final String ZOOM_LEVEL = "zoom_level";

	public static final String CONF_SHOW_LABELS = "show_labels";

	public static final String CONF_FOLLOW = "follow";

	public static final String CONF_MAP_MODE = "map_mode";

	public static final String CONF_SHOW_OPEN = "show_open";

	public static final String CONF_SHOW_CLOSED = "show_closed";

	public static final String CONF_NOTIFICATIONS_ENABLED = "notifications_enabled";

	public static final String CONF_SYNC_TSTAMP = "sync_tstamp";

	public static final String CONF_LASTAPP_TSTAMP = "lastapp_tstamp";

	public static final String CONF_LASTSERVICE_TSTAMP = "lastservice_tstamp";

	public static final String CONF_GPS_TIMES = "gps_times";

	public static final int EVENT_KML_EXPORT_DONE = 0;

	public static final int EVENT_SYNC_ONLINE_PROGRESS = 1;

	public static final String EVENT_SYNC_ONLINE_PROGRESS_PAR_INSERTED_COUNT = "inserted_count";

	public static final int EVENT_SYNC_ONLINE_DONE = 2;

	public static final int EVENT_NOTIFY_ERROR = 3;

	public static final int DIALOG_STATS = 0;

	public static final int DIALOG_ABOUT = DIALOG_STATS + 1;

	public static final int DIALOG_DELETE_ALL_WIFI = DIALOG_ABOUT + 1;

	public static final int DIALOG_SYNC_PROGRESS = DIALOG_DELETE_ALL_WIFI + 1;
	
	public static final int DIALOG_SYNC_ALL = DIALOG_SYNC_PROGRESS + 1;
	
	public static final int[] GPS_SECONDS = {3000, 10000, 30000};
	                        
	public static final int[] GPS_METERS = {3, 10, 50};
}
