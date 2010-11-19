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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class WigleUploader
{
	private static Object LOCK = new Object();
	
	private static final String _URL = "http://www.wigle.net/gps/gps/main/confirmfile/";
	
	private static final String BOUNDARY = "----MultiPartBoundary";
	
	private static final String NL = "\r\n";

	public static boolean upload(String username, String password, File file, Handler message_handler)
	{
		if (username == null || username.length() == 0 || password == null || password.length() == 0)
			return false;
		
		synchronized (LOCK)
		{
			try
			{
				Message msg;
				Bundle b;
		        URL url = new URL(_URL);
		        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		    	conn.setDoInput(true);
		    	conn.setDoOutput(true);
		    	conn.setUseCaches(false);
		    	conn.setChunkedStreamingMode(1);
		    	conn.setRequestMethod("POST");
		    	conn.setRequestProperty("User-Agent","wardrive");
		    	conn.setRequestProperty("Content-Type","multipart/form-data;boundary="+BOUNDARY);
		    	conn.connect();
		    	DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
		    	dos.writeBytes("--"+BOUNDARY+NL+"Content-Disposition: form-data; name=\"observer\""+NL+NL+username+NL);
		    	dos.writeBytes("--"+BOUNDARY+NL+"Content-Disposition: form-data; name=\"password\""+NL+NL+password+NL);
		    	dos.writeBytes("--"+BOUNDARY+NL+"Content-Disposition: form-data; name=\"stumblefile\";filename=\"wardrive.kml\""+NL+"Content-Type: application/octet-stream"+NL+NL);
		    	int ct;
		    	long readbytes = 0;
		    	long filelength = file.length();
		    	byte[] buf = new byte[1240];
				BufferedInputStream fis = new BufferedInputStream(new FileInputStream(file), 1024);
		    	while(fis.available() > 0)
		    	{
		    		ct = fis.read(buf);
		    		dos.write(buf, 0, ct);
		    		dos.flush();
		    		
		    		readbytes += ct;
		    		msg = Message.obtain(message_handler, Constants.EVENT_WIGLE_UPLOAD_PROGRESS);
					b = new Bundle();
					b.putInt(Constants.EVENT_WIGLE_UPLOAD_PROGRESS_PAR_COUNT, (int) readbytes);
					b.putInt(Constants.EVENT_WIGLE_UPLOAD_PROGRESS_PAR_TOTAL, (int) filelength);
					msg.setData(b);
					message_handler.sendMessage(msg);
		    	}
		    	fis.close();
		    	dos.writeBytes(NL+"--"+BOUNDARY+NL+"Content-Disposition: form-data; name=\"Send\""+NL+NL+"Send");
		    	dos.writeBytes(NL+"--"+BOUNDARY+"--"+NL);
		    	dos.flush();
		    	dos.close();
		    	DataInputStream dis = new DataInputStream(conn.getInputStream());
		    	byte[] data = new byte[10240];
		    	ct = dis.read(data);
		    	dis.close();
		    	conn.disconnect();
		    	String response = new String(data, 0, ct);
	    		return response.indexOf("uploaded successfully") != -1;
			}
			catch (Exception e)
			{
				Logger.getAnonymousLogger().severe(e.getMessage());
				return false;
			}
		}
	}
}
