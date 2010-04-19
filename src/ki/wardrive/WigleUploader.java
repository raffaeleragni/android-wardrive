package ki.wardrive;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

public class WigleUploader
{
	private static Object LOCK = new Object();
	
	private static final String _URL = "http://www.wigle.net/gps/gps/main/confirmfile/";
	
	private static final String BOUNDARY = "----MultiPartBoundary";
	
	private static final String NL = "\r\n";

	public static boolean upload(String username, String password, File file)
	{
		if (username == null || username.length() == 0 || password == null || password.length() == 0)
		{
			return false;
		}
		
		synchronized (LOCK)
		{
			try
			{
		        URL url = new URL(_URL);
		        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		    	conn.setDoInput(true);
		    	conn.setDoOutput(true);
		    	conn.setUseCaches(false);
		    	conn.setRequestMethod("POST");
		    	conn.setRequestProperty("User-Agent","wardrive");
		    	conn.setRequestProperty("Content-Type","multipart/form-data;boundary="+BOUNDARY);
		    	conn.connect();
		    	DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
		    	dos.writeBytes("--"+BOUNDARY+NL+"Content-Disposition: form-data; name=\"observer\""+NL+NL+username+NL);
		    	dos.writeBytes("--"+BOUNDARY+NL+"Content-Disposition: form-data; name=\"password\""+NL+NL+password+NL);
		    	dos.writeBytes("--"+BOUNDARY+NL+"Content-Disposition: form-data; name=\"stumblefile\";filename=\"wardrive.kml\""+NL+"Content-Type: application/octet-stream"+NL+NL);
		    	int ct;
		    	byte[] buf = new byte[10240];
				BufferedInputStream fis = new BufferedInputStream(new FileInputStream(file));
		    	while(fis.available() > 0)
		    	{
		    		ct = fis.read(buf);
		    		dos.write(buf, 0, ct);
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
