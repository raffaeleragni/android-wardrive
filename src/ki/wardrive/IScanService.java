package ki.wardrive;

public interface IScanService
{
	public boolean getNotificationsEnabled();
	public void setNotificationsEnabled(boolean notificationsEnabled);
	public int getGpsSeconds();
	public int getGpsMeters();
	public void setGpsTimes(int s, int m);
}
