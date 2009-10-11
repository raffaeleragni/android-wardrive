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

public class DBTableNetworks
{
	public static final String DATABASE_FULL_PATH = "/sdcard/wardrive.db3";

	public static final String TABLE_NETWORKS = "networks";

	public static final String TABLE_NETWORKS_FIELD_BSSID = "bssid";

	public static final String TABLE_NETWORKS_FIELD_COUNT_BSSID = "count(bssid)";

	public static final String TABLE_NETWORKS_FIELD_SUM_LAT = "sum(lat)";

	public static final String TABLE_NETWORKS_FIELD_SUM_LON = "sum(lon)";

	public static final String TABLE_NETWORKS_FIELD_BSSID_EQUALS = "bssid = ?";

	public static final String TABLE_NETWORKS_FIELD_SSID = "ssid";

	public static final String TABLE_NETWORKS_FIELD_CAPABILITIES = "capabilities";

	public static final String TABLE_NETWORKS_FIELD_LEVEL = "level";

	public static final String TABLE_NETWORKS_FIELD_FREQUENCY = "frequency";

	public static final String TABLE_NETWORKS_FIELD_LAT = "lat";

	public static final String TABLE_NETWORKS_FIELD_LON = "lon";

	public static final String TABLE_NETWORKS_FIELD_ALT = "alt";

	public static final String TABLE_NETWORKS_FIELD_TIMESTAMP = "timestamp";

	public static final String TABLE_NETWORKS_LOCATION_BETWEEN = "lat >= ? and lat <= ? and lon >= ? and lon <= ?";

	public static final String TABLE_NETWORKS_OPEN_CONDITION = "capabilities = ''";

	public static final String TABLE_NETWORKS_CLOSED_CONDITION = "capabilities <> ''";

	public static final String SELECT_COUNT_WIFIS = "select count(bssid) from networks";

	public static final String SELECT_COUNT_OPEN = "select count(bssid) from networks where capabilities = ''";

	public static final String CREATE_TABLE_NETWORKS = "create table if not exists networks (bssid text primary key, ssid text, capabilities text, level integer, frequency integer, lat real, lon real, alt real, timestamp integer)";

	public static final String CREATE_INDEX_LATLON = "create index if not exists networks_latlon_idx on networks(lat, lon)";

	public static final String CREATE_INDEX_CAPABILITIES = "create index if not exists networks_capabilities_idx on networks(capabilities)";

	public static final String DELETE_ALL_WIFI = "delete from networks";
}
