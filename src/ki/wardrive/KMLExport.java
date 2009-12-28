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
import java.io.FileWriter;
import java.io.IOException;

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
	private static final String STYLE_RED = "<styleUrl>#red</styleUrl>";
	
	private static final String STYLE_YELLOW = "<styleUrl>#yellow</styleUrl>";
	
	private static final String STYLE_GREEN = "<styleUrl>#green</styleUrl>";
	
	private static final String ROOT_START = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><Style id=\"red\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/red-dot.png</href></Icon></IconStyle></Style> <Style id=\"yellow\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/yellow-dot.png</href></Icon></IconStyle></Style><Style id=\"green\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/green-dot.png</href></Icon></IconStyle></Style>";

	private static final String ROOT_END = "\n</Document></kml>";

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

	private static final String GENERICS_INFO_5 = "</b><br/>Timestamp: <b>";

	private static final String GENERICS_INFO_END = "</b>";

	private static final String FOLDER_1 = "\n<Folder><name>Open WiFis</name>";

	private static final String FOLDER_2 = "\n</Folder><Folder><name>Closed WiFis</name>";

	private static final String FOLDER_END = "\n</Folder>";

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
					fw.append(FOLDER_1);

					c = database.query(DBTableNetworks.TABLE_NETWORKS, new String[] { DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID,
							DBTableNetworks.TABLE_NETWORKS_FIELD_SSID, DBTableNetworks.TABLE_NETWORKS_FIELD_CAPABILITIES,
							DBTableNetworks.TABLE_NETWORKS_FIELD_FREQUENCY, DBTableNetworks.TABLE_NETWORKS_FIELD_LEVEL,
							DBTableNetworks.TABLE_NETWORKS_FIELD_LAT, DBTableNetworks.TABLE_NETWORKS_FIELD_LON,
							DBTableNetworks.TABLE_NETWORKS_FIELD_ALT, DBTableNetworks.TABLE_NETWORKS_FIELD_TIMESTAMP }, DBTableNetworks.TABLE_NETWORKS_OPEN_CONDITION, null,
							null, null, null);

					if (c != null && c.moveToFirst())
					{
						do
						{
							write_mark(c, fw);
						}
						while (c.moveToNext());
					}

					fw.append(FOLDER_2);

					c = database.query(DBTableNetworks.TABLE_NETWORKS, new String[] { DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID,
							DBTableNetworks.TABLE_NETWORKS_FIELD_SSID, DBTableNetworks.TABLE_NETWORKS_FIELD_CAPABILITIES,
							DBTableNetworks.TABLE_NETWORKS_FIELD_FREQUENCY, DBTableNetworks.TABLE_NETWORKS_FIELD_LEVEL,
							DBTableNetworks.TABLE_NETWORKS_FIELD_LAT, DBTableNetworks.TABLE_NETWORKS_FIELD_LON,
							DBTableNetworks.TABLE_NETWORKS_FIELD_ALT, DBTableNetworks.TABLE_NETWORKS_FIELD_TIMESTAMP }, DBTableNetworks.TABLE_NETWORKS_CLOSED_CONDITION, null,
							null, null, null);

					if (c != null && c.moveToFirst())
					{
						do
						{
							write_mark(c, fw);
						}
						while (c.moveToNext());
					}

					fw.append(FOLDER_END);
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

	private static void write_mark(Cursor c, FileWriter fw) throws IOException
	{
		String cap = c.getString(2);
		boolean open = cap == null || cap.length() == 0;
		boolean wep = cap != null && cap.contains("WEP");
		fw.append(MARK_START);
		fw.append(NAME_START);
		fw.append(c.getString(1)); // SSID
		fw.append(NAME_END);
		fw.append(DESCRIPTION_START);
		fw.append(GENERICS_INFO_1);
		fw.append(c.getString(0)); // BSSID
		fw.append(GENERICS_INFO_2);
		fw.append(cap); // CAPABILITIES
		fw.append(GENERICS_INFO_3);
		fw.append(c.getString(3)); // FREQUENCY
		fw.append(GENERICS_INFO_4);
		fw.append(c.getString(4)); // LEVEL
		fw.append(GENERICS_INFO_5);
		fw.append(c.getString(8)); // TIMESTAMP
		fw.append(GENERICS_INFO_END);
		fw.append(DESCRIPTION_END);
		fw.append(open ? STYLE_GREEN : (wep ? STYLE_YELLOW : STYLE_RED)); // Dot color
		fw.append(POINT_START);
		fw.append(COORDINATES_START);
		fw.append(c.getDouble(6) + "," + c.getDouble(5) + "," + c.getDouble(7)); // LAT, LON, ALT
		fw.append(COORDINATES_END);
		fw.append(POINT_END);
		fw.append(MARK_END);
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
