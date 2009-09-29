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

import java.util.HashMap;
import java.util.Locale;

public class Locales
{
	public static final Locale default_language = Locale.ENGLISH;

	public static HashMap<Locale, Locales> localizations = new HashMap<Locale, Locales>();

	public String MENU_QUIT_LABEL = "Quit";

	public String MENU_STATS_LABEL = "Stats";

	public String MENU_MAX_WIFI_VISIBLE_LABEL = "WiFis";

	public String MENU_GPS_QUERIES_METERS_LABEL = "GPS";

	public String MENU_TOGGLE_LABELS_LABEL = "Labels";

	public String MENU_TOGGLE_FOLLOW_ME_LABEL = "Follow";

	public String MESSAGE_STATISTICS = "Statistics:";

	public String MESSAGE_STATISTICS_COUNT = "\nTotal WiFis in DB: ";

	public String MESSAGE_STATISTICS_OPEN = "\nOf which open: ";

	public CharSequence[] MAX_WIFI_VISIBLE_LABEL = { "50", "500", "5000" };

	public CharSequence[] GPS_QUERIES_METERS_LABEL = { "Short (10 meters)", "Medium (50 meters)", "Long (200 meters)" };

	public static Locales get(Locale language)
	{
		if (localizations.containsKey(language))
		{
			return localizations.get(language);
		}
		else
		{
			return localizations.get(default_language);
		}
	}

	static
	{
		localizations.put(default_language, new Locales());

		Locales l;

		// Localized ones
		l = new Locales();
		l.MENU_QUIT_LABEL = "Esci";
		l.MENU_STATS_LABEL = "Dati";
		l.MENU_MAX_WIFI_VISIBLE_LABEL = "WiFi";
		l.MENU_GPS_QUERIES_METERS_LABEL = "GPS";
		l.MENU_TOGGLE_LABELS_LABEL = "Nomi WiFi";
		l.MENU_TOGGLE_FOLLOW_ME_LABEL = "Traccia";
		l.MESSAGE_STATISTICS = "Statistiche:";
		l.MESSAGE_STATISTICS_COUNT = "\nTotale WiFi in database: ";
		l.MESSAGE_STATISTICS_OPEN = "\nDi cui aperte: ";
		l.MAX_WIFI_VISIBLE_LABEL = new CharSequence[] { "50", "500", "5000" };
		l.GPS_QUERIES_METERS_LABEL = new CharSequence[] { "Corto (10 metri)", "Medio (50 metri)", "Lungo (200 metri)" };
		localizations.put(Locale.ITALIAN, l);
	}
}
