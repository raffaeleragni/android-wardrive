package ki.wardrive;

public interface IScanService
{
	public boolean getNotificationsEnabled();
	public void setNotificationsEnabled(boolean notificationsEnabled);
	public int getGpsSeconds();
	public int getGpsMeters();
	public void setGpsTimes(int s, int m);
	public void start_services();
	public void stop_services();
}
