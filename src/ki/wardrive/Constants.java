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
	public static final String SYNC_ONLINE_URL = "http://localhost:8080/main";

//	public static final String SYNC_ONLINE_URL = "http://wardrivedb.appspot.com/main";

	public static final int SYNC_ONLINE_BUFFER = 100;

	public static final String DATABASE_FULL_PATH = "/sdcard/wardrive.db3";

	public static final String KML_EXPORT_FILE = "/sdcard/wardrive.kml";

	public static final int DEFAULT_LAT = 0;

	public static final int DEFAULT_LON = 0;

	public static final int DEFAULT_ZOOM_LEVEL = 17;

	public static final int MAX_WIFI_VISIBLE = 50;

	public static final int QUADRANT_DOTS_SCALING_CONSTANT = 3;

	public static final int QUADRANT_DOTS_SCALING_FACTOR = 12;

	public static final int QUADRANT_ACTIVATION_AT_ZOOM_DIFFERENCE = 3;

	public static final int MAPS_GPS_EVENT_WAIT = 500;

	public static final int MAPS_GPS_EVENT_METERS = 0;

	public static final int SERVICE_GPS_EVENT_WAIT = 3000;

	public static final int SERVICE_GPS_EVENT_METERS = 3;

	public static final boolean SERVICE_TOASTS_ENABLED = false;
}
