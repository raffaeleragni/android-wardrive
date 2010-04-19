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
	private static final String _URL = "http://www.wigle.net/gps/gps/main/confirmfile/";
	
	private static final String BOUNDARY = "*****MultiPartBoundary";

	public static boolean upload(String username, String password, File file)
	{
		if (username == null || username.length() == 0 || password == null || password.length() == 0)
		{
			return false;
		}
		
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
	    	dos.writeBytes("--"+BOUNDARY+"\nContent-Disposition: form-data; name=\"observer\"\n\n"+username+"\n");
	    	dos.writeBytes("--"+BOUNDARY+"\nContent-Disposition: form-data; name=\"password\"\n\n"+password+"\n");
	    	dos.writeBytes("--"+BOUNDARY+"\nContent-Disposition: form-data; name=\"stumblefile\";filename=\"wardrive.kml\"\nContent-Type: application/octet-stream\n\n");
	    	int ct;
	    	byte[] buf = new byte[10240];
			BufferedInputStream fis = new BufferedInputStream(new FileInputStream(file));
	    	while((ct = fis.read(buf)) > 0)
	    	{
	    		dos.write(buf, 0, ct);
	    	}
	    	fis.close();
	    	dos.writeBytes("\n--"+BOUNDARY+"--\n");
	    	dos.flush();
	    	dos.close();
	    	DataInputStream dis = new DataInputStream(conn.getInputStream());
	    	byte[] data = new byte[10240];
	    	dis.read(data);
	    	dis.close();
	    	conn.disconnect();
	    	String response = new String(data);
    		return response.matches("uploaded succesfully");
		}
		catch (Exception e)
		{
			Logger.getAnonymousLogger().severe(e.getMessage());
			return false;
		}
	}
}
