/*
 * Copyright (C) 2012 
 *
 * This file is part of sms-club.ch Connector for WebSMS by Felix Bechstein.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package de.ub0r.android.websms.connector.smsclub;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Preferences.
 */
public final class Preferences extends PreferenceActivity {
	/** Preference key: enabled. */
	static final String PREFS_ENABLED = "enable_smsclub";

	/** Preference's name: user's username. */
	static final String PREFS_USER = "user_smsclub";
	/** Preference's name: user's password. */
	static final String PREFS_PASSWORD = "password_smsclub";

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.addPreferencesFromResource(R.xml.connector_smsclub_prefs);
	}
}
