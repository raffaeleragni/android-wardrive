/*
 *   wardrive - android wardriving application
 *   Copyright (C) 2009 Raffaele Ragni
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

import java.util.ArrayList;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;

public class Overlays extends ItemizedOverlay<OverlayItem>
{
	private static final int CIRCLE_RADIUS = 5;

	private static final int INFO_WINDOW_HEIGHT = 16;

	private static Object DRAW_CALLBACK_LOCK = new Object();

	private ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();

	private Runnable lazy_load_callback = null;

	private Paint paint_circle;

	private TextPaint paint_text;

	private Point point = new Point();

	private String title;
	
	public boolean show_labels = false;

	public Overlays(Drawable d, int r, int g, int b)
	{
		super(d);
		populate();

		paint_circle = new Paint();
		paint_circle.setARGB(255, r, g, b);
		paint_circle.setAntiAlias(true);

		paint_text = new TextPaint();
		paint_text.setARGB(255, 255, 255, 255);
		paint_text.setAntiAlias(true);
		paint_text.setStrokeWidth(3);
		paint_text.setTextSize(14);
	}

	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow)
	{
		if (lazy_load_callback != null)
		{
			synchronized (DRAW_CALLBACK_LOCK)
			{
				lazy_load_callback.run();
			}
		}
		
		for (OverlayItem o : items)
		{
			title = o.getTitle();
			point = mapView.getProjection().toPixels(o.getPoint(), point);
			canvas.drawCircle(point.x, point.y, CIRCLE_RADIUS, paint_circle);

			if (show_labels && title != null && title.length() > 0)
			{
				int INFO_WINDOW_WIDTH = getTextWidth(title) + 4 * 2;
				RectF rect = new RectF(0, 0, INFO_WINDOW_WIDTH, INFO_WINDOW_HEIGHT);
				rect.offset(point.x + 2, point.y + 2);
				canvas.drawRect(rect, paint_circle);
				canvas.drawText(title, point.x + 6, point.y + 14, paint_text);
			}
		}
	}

	private int getTextWidth(String text)
	{
		int count = text.length();
		float[] widths = new float[count];
		paint_text.getTextWidths(text, widths);
		int textWidth = 0;
		for (int i = 0; i < count; i++)
		{
			textWidth += widths[i];
		}
		return textWidth;
	}

	public void setLazyLoadCallback(Runnable lazy_load_callback)
	{
		this.lazy_load_callback = lazy_load_callback;
	}

	public void setItems(ArrayList<OverlayItem> items)
	{
		this.items = items;
		populate();
	}

	@Override
	protected OverlayItem createItem(int i)
	{
		return items.get(i);
	}

	@Override
	public int size()
	{
		return items.size();
	}
}
