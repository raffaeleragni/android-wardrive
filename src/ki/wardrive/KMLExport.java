package ki.wardrive;

import java.io.File;
import java.io.FileWriter;
import java.text.DecimalFormat; /*
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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Exports the networks table to a KML file format.
 * 
 * @author Raffaele Ragni raffaele.ragni@gmail.com
 */
public class KMLExport
{
	private static DecimalFormat df = new DecimalFormat("#.#");

	private static final String ROOT_START = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<kml xmlns=\"http://www.opengis.net/kml/2.2\">";

	private static final String ROOT_END = "\n</kml>";

	private static final String MARK_START = "\n\t<Placemark>";

	private static final String MARK_END = "\n\t</Placemark>";

	private static final String NAME_START = "\n\t\t<name><![CDATA[";

	private static final String NAME_END = "]]></name>";

	private static final String DESCRIPTION_START = "\n\t\t<description><![CDATA[";

	private static final String DESCRIPTION_END = "]]></description>";

	private static final String POINT_START = "\n\t\t<Point>";

	private static final String POINT_END = "\n\t\t</Point>";

	private static final String COORDINATES_START = "\n\t\t<coordinates>";

	private static final String COORDINATES_END = "</coordinates>";

	private static final String GENERICS_INFO_1 = "BSSID: <b>";

	private static final String GENERICS_INFO_2 = "</b><br/>Capabilities: <b>";

	private static final String GENERICS_INFO_3 = "</b><br/>Frequency: <b>";

	private static final String GENERICS_INFO_4 = "</b><br/>Level: <b>";

	private static final String GENERICS_INFO_END = "</b>";

	public static boolean export(SQLiteDatabase database, File file)
	{
		try
		{
			if (file.exists())
			{
				file.delete();
			}
			file.createNewFile();

			FileWriter fw = null;
			try
			{
				fw = new FileWriter(file);
				fw.append(ROOT_START);

				Cursor c = null;
				try
				{
					c = database.query(DBTableNetworks.TABLE_NETWORKS, new String[] { DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID,
							DBTableNetworks.TABLE_NETWORKS_FIELD_SSID, DBTableNetworks.TABLE_NETWORKS_FIELD_CAPABILITIES,
							DBTableNetworks.TABLE_NETWORKS_FIELD_FREQUENCY, DBTableNetworks.TABLE_NETWORKS_FIELD_LEVEL,
							DBTableNetworks.TABLE_NETWORKS_FIELD_LAT, DBTableNetworks.TABLE_NETWORKS_FIELD_LON,
							DBTableNetworks.TABLE_NETWORKS_FIELD_ALT }, null, null, null, null, null);

					if (c != null && c.moveToFirst())
					{
						do
						{
							fw.append(MARK_START);
							fw.append(NAME_START);
							fw.append(c.getString(1)); //SSID
							fw.append(NAME_END);
							fw.append(DESCRIPTION_START);
							fw.append(GENERICS_INFO_1);
							fw.append(c.getString(0)); //BSSID
							fw.append(GENERICS_INFO_2);
							fw.append(c.getString(2)); //CAPABILITIES
							fw.append(GENERICS_INFO_3);
							fw.append(c.getString(3)); //FREQUENCY
							fw.append(GENERICS_INFO_4);
							fw.append(c.getString(4)); //LEVEL
							fw.append(GENERICS_INFO_END);
							fw.append(DESCRIPTION_END);
							fw.append(POINT_START);
							fw.append(COORDINATES_START);
							fw.append(df.format(c.getDouble(5)) + "," + df.format(c.getDouble(6)) + ","
									+ df.format(c.getDouble(7))); // LAT, LON, ALT
							fw.append(COORDINATES_END);
							fw.append(POINT_END);
							fw.append(MARK_END);
						}
						while (c.moveToNext());
					}
				}
				finally
				{
					destroy_cursor(c);
				}

				fw.append(ROOT_END);
			}
			finally
			{
				if (fw != null)
				{
					fw.close();
				}
			}

			return true;
		}
		catch (Exception e)
		{
			Log.e(KMLExport.class.getName(), "", e);
		}
		return false;
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
}
