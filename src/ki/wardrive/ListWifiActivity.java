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

import android.app.ListActivity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * Shows a list of the actually visible wifis on map.
 *
 * @author Raffaele Ragni raffaele.ragni@gmail.com
 */
public class ListWifiActivity extends ListActivity
{
	private SQLiteDatabase database;
	
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        
        database = SQLiteDatabase.openDatabase(DBTableNetworks.getDBFullPath(), null, SQLiteDatabase.OPEN_READWRITE);
        if (database == null)
        {
        	finish();
        }
        
        Bundle b = getIntent().getExtras();

        double lat_from = ((double) b.getInt("minlat")) / 1E6;
		double lat_to = ((double) b.getInt("maxlat")) / 1E6;
		double lon_from = ((double) b.getInt("minlon")) / 1E6;
		double lon_to = ((double) b.getInt("maxlon")) / 1E6;
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
        
        Cursor c = database.query(DBTableNetworks.TABLE_NETWORKS, new String[]
			{
				DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID + " as _id",
				DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID,
				DBTableNetworks.TABLE_NETWORKS_FIELD_SSID,
				DBTableNetworks.TABLE_NETWORKS_FIELD_CAPABILITIES,
				DBTableNetworks.TABLE_NETWORKS_FIELD_LAT,
				DBTableNetworks.TABLE_NETWORKS_FIELD_LON
			},
			DBTableNetworks.TABLE_NETWORKS_LOCATION_BETWEEN,
			new String[]
			{
				String.valueOf(lat_from),
				String.valueOf(lat_to),
				String.valueOf(lon_from),
				String.valueOf(lon_to)
			},
			null,
			null,
			DBTableNetworks.TABLE_NETWORKS_FIELD_SSID + " asc");
        
        startManagingCursor(c);
        
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
			this,
			R.layout.list_wifi_row,
			c,
			new String[]
	         {
				DBTableNetworks.TABLE_NETWORKS_FIELD_SSID,
	    		DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID,
				DBTableNetworks.TABLE_NETWORKS_FIELD_CAPABILITIES,
				DBTableNetworks.TABLE_NETWORKS_FIELD_LAT,
				DBTableNetworks.TABLE_NETWORKS_FIELD_LON
	         },
	        new int[]{R.id.f1, R.id.f2, R.id.f3, R.id.f4, R.id.f5});
        
        setListAdapter(adapter);
        
        getListView().setOnCreateContextMenuListener(new OnCreateContextMenuListener()
		{
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
			{
		    	MenuInflater mi = getMenuInflater();
				mi.inflate(R.menu.wifi_list_item_menu, menu);
			}
		});
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
    	MenuInflater mi = getMenuInflater();
		mi.inflate(R.menu.wifi_list_item_menu, menu);
		
    	super.onCreateContextMenu(menu, v, menuInfo);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
    	switch (item.getItemId())
		{
    		case R.menu_id.LIST_ITEM_DELETE:
    		{
    	    	AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    	    	Cursor c = (Cursor) getListView().getAdapter().getItem(info.position);
    			database.delete(DBTableNetworks.TABLE_NETWORKS, DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID +"='"+ c.getString(1)+"'", null);
    			c.requery();
    			break;
    		}
		}

    	return super.onContextItemSelected(item);
    }
        
    @Override
    protected void onDestroy()
    {
    	database.close();
    	super.onDestroy();
    }
}
