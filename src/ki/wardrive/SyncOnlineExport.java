package ki.wardrive;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class SyncOnlineExport
{
	public static int export(long tstamp, SQLiteDatabase database, URL url, Handler message_handler)
			throws IllegalStateException, IOException
	{
		int inserted_count = 0;
		int count = 0;
		BasicNameValuePair action = new BasicNameValuePair("action", "post_spots");
		List<NameValuePair> values = new ArrayList<NameValuePair>(10);
		values.add(action);

		Cursor c = null;
		try
		{
			c = database.query(DBTableNetworks.TABLE_NETWORKS, new String[] { DBTableNetworks.TABLE_NETWORKS_FIELD_BSSID,
					DBTableNetworks.TABLE_NETWORKS_FIELD_SSID, DBTableNetworks.TABLE_NETWORKS_FIELD_CAPABILITIES,
					DBTableNetworks.TABLE_NETWORKS_FIELD_LEVEL, DBTableNetworks.TABLE_NETWORKS_FIELD_FREQUENCY,
					DBTableNetworks.TABLE_NETWORKS_FIELD_LAT, DBTableNetworks.TABLE_NETWORKS_FIELD_LON,
					DBTableNetworks.TABLE_NETWORKS_FIELD_ALT, DBTableNetworks.TABLE_NETWORKS_FIELD_TIMESTAMP },
					DBTableNetworks.TABLE_NETWORKS_FIELD_TIMESTAMP_AFTER, new String[] { "" + tstamp }, null, null, null);

			if (c != null && c.moveToFirst())
			{
				do
				{
					values.add(new BasicNameValuePair("bssids", c.getString(0)));
					values.add(new BasicNameValuePair("ssids", c.getString(1)));
					values.add(new BasicNameValuePair("capabilities", c.getString(2)));
					values.add(new BasicNameValuePair("levels", c.getString(3)));
					values.add(new BasicNameValuePair("frequencies", c.getString(4)));
					values.add(new BasicNameValuePair("lats", "" + c.getDouble(5)));
					values.add(new BasicNameValuePair("lons", "" + c.getDouble(6)));
					values.add(new BasicNameValuePair("alts", "" + c.getDouble(7)));
					values.add(new BasicNameValuePair("timestamps", c.getString(8)));
					count++;

					if (count == Constants.SYNC_ONLINE_BUFFER || c.isLast())
					{
						HttpClient client = new DefaultHttpClient();
						HttpPost post = new HttpPost(Constants.SYNC_ONLINE_URL);
						values.add(new BasicNameValuePair("spots_count", "" + count));
						post.setEntity(new UrlEncodedFormEntity(values));
						HttpResponse response = client.execute(post);
						if (response != null)
						{
							inserted_count += count;

							if (!c.isLast())
							{
								Message msg = Message.obtain(message_handler, Main.EVENT_SYNC_ONLINE_PROGRESS);
								Bundle b = new Bundle();
								b.putInt(Main.EVENT_SYNC_ONLINE_PROGRESS_PAR_INSERTED_COUNT,
										(int) (((double) c.getPosition() / (double) c.getCount()) * 100));
								msg.setData(b);
								message_handler.sendMessage(msg);
							}
						}

						count = 0;
						values.clear();
						values.add(action);
					}
				}
				while (c.moveToNext());
			}
		}
		finally
		{
			destroy_cursor(c);
		}

		return inserted_count;
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