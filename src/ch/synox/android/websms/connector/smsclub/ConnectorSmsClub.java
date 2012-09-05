/*
 * Copyright (C) 2012 Synox
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
package ch.synox.android.websms.connector.smsclub;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import de.ub0r.android.websms.connector.common.Connector;
import de.ub0r.android.websms.connector.common.ConnectorCommand;
import de.ub0r.android.websms.connector.common.ConnectorSpec;
import de.ub0r.android.websms.connector.common.ConnectorSpec.SubConnectorSpec;
import de.ub0r.android.websms.connector.common.Log;
import de.ub0r.android.websms.connector.common.Utils;
import de.ub0r.android.websms.connector.common.Utils.HttpOptions;
import de.ub0r.android.websms.connector.common.WebSMSException;

/**
 * AsyncTask to manage IO to sms-club.ch API.
 * 
 */
public class ConnectorSmsClub extends Connector {
	// see api doc:
	// http://blog.sms-club.ch/wp-content/uploads/2009/06/sms-club_api_interface.pdf

	private static final Pattern PATTERN_ERR = Pattern.compile("<error id=\"([^\"]+)\"/>");
	private static final Pattern PATTERN_CREDIT = Pattern.compile("credit=\"([0-9]+)\"");

	/** Tag for output. */
	private static final String TAG = "sms-club.ch";

	/** Gateway URL. */
	private static final String URL_SEND = "https://www.sms-club.ch/api/send";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec initSpec(final Context context) {
		final String name = context.getString(R.string.connector_smsclub_name);
		ConnectorSpec c = new ConnectorSpec(name);
		c.setAuthor(// .
		context.getString(R.string.connector_smsclub_author));
		c.setBalance(null);

		// balance update without sending is currently not supported by the api,
		// so it also not supported by the implementation.
		c.setCapabilities(ConnectorSpec.CAPABILITIES_SEND | ConnectorSpec.CAPABILITIES_PREFS
				| ConnectorSpec.CAPABILITIES_UPDATE);
		c.addSubConnector(TAG, name, SubConnectorSpec.FEATURE_MULTIRECIPIENTS);
		return c;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec updateSpec(final Context context, final ConnectorSpec connectorSpec) {
		final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
		if (p.getBoolean(Preferences.PREFS_ENABLED, false)) {
			if (p.getString(Preferences.PREFS_PASSWORD, "").length() > 0) {
				connectorSpec.setReady();
			} else {
				connectorSpec.setStatus(ConnectorSpec.STATUS_ENABLED);
			}
		} else {
			connectorSpec.setStatus(ConnectorSpec.STATUS_INACTIVE);
		}
		return connectorSpec;
	}

	/**
	 * Check return code from sms-club.ch.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param ret
	 *            return code
	 * @return true if no error code
	 */
	private boolean checkReturnCode(final Context context, final int ret) {
		Log.d(TAG, "ret=" + ret);
		if (ret < 200) {
			return true;
		} else if (ret < 300) {
			throw new WebSMSException(context, R.string.error_input);
		} else if (ret == 401) {
			throw new WebSMSException(context, R.string.error_pw);
		} else {
			throw new WebSMSException(context, R.string.error_server, // .
					" " + ret);
		}
	}

	private static void checkForErrors(final Context context, final String htmlText) {
		if (htmlText.contains("<error")) {
			// there was an error
			Matcher m = PATTERN_ERR.matcher(htmlText);
			if (m.find()) {
				String errMsg = m.group(1);
				Log.e(TAG, "found error: " + errMsg);

				if (errMsg.startsWith("recipient.")) {
					throw new WebSMSException(context, R.string.error_recipient, errMsg);
				}
				if (errMsg.startsWith("message.")) {
					throw new WebSMSException(context, R.string.error_text, errMsg);
				}
				if (errMsg.equals("duplicate_sms")) {
					throw new WebSMSException(context, R.string.error_text, errMsg);
				}

				throw new WebSMSException(context, R.string.error_input, errMsg);
			}
		}
		if (htmlText.contains("<okay")) {
			// all good! there can be multiple okay for multiple recipients.
			Log.d(TAG, "response message contains <okay>");
		} else {
			// no "okay" found, not good!
			throw new WebSMSException(context, R.string.error_input, htmlText);

		}

	}

	private static void readBalance(final ConnectorSpec cs, final String htmlText) {
		// example: <smsResponse credit="580">
		Matcher m = PATTERN_CREDIT.matcher(htmlText);
		if (m.find()) {

			String credit = m.group(1);
			Log.i(TAG, "found credit: " + credit);
			cs.setBalance(credit);
		}
	}

	/**
	 * Send data.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param command
	 *            {@link ConnectorCommand}
	 */
	private void sendData(final Context context, final ConnectorCommand command) {
		// do IO
		try { // get Connection
			final String text = command.getText();
			final ConnectorSpec cs = this.getSpec(context);
			final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);

			// prepair form to send
			HttpOptions options = new HttpOptions("UTF-8");
			options.url = URL_SEND;
			List<BasicNameValuePair> formData = new ArrayList<BasicNameValuePair>();
			// allow messages longer than 160 chars.
			formData.add(new BasicNameValuePair("multimessage", "true"));
			formData.add(new BasicNameValuePair("message", text));
			formData.add(new BasicNameValuePair("recipient", Utils.joinRecipientsNumbers(
					command.getRecipients(), ",", true)));
			options.addFormParameter(formData);

			// setup user/password
			options.addBasicAuthHeader(p.getString(Preferences.PREFS_USER, ""),
					p.getString(Preferences.PREFS_PASSWORD, ""));
			options.trustAll = true;

			String htmlText = this.doHttpRequest(context, options);
			// for testing, comment out previous command.
			// String htmlText = this.doFakeRequest(context, options);

			/*
			 * example response: <?xml version="1.0" encoding="ISO-8859-1"?>
			 * <!DOCTYPE smsResponse PUBLIC "smsResponse"
			 * "http://messenger.handybutler.ch/dtd/smsResponse.dtd">
			 * <smsResponse credit="20000"> <okay id="53911:41796481111"
			 * notiflink=
			 * "https://www.sms- club.ch/websms/sclub/statusxml.do?msgid=53911&msisdn=+41796481111"
			 * /> </smsResponse>
			 */

			// Log.d(TAG, "--HTTP RESPONSE--");
			// Log.d(TAG, htmlText);
			// Log.d(TAG, "--HTTP RESPONSE--");

			readBalance(cs, htmlText);
			checkForErrors(context, htmlText);

			htmlText = null;
		} catch (IOException e) {
			Log.e(TAG, null, e);
			throw new WebSMSException(e.getMessage());
		}
	}

	private String doFakeRequest(final Context context, final HttpOptions options) {
		return "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
				+ "<!DOCTYPE smsResponse PUBLIC \"smsResponse\" \"http://messenger.handybutler.ch/dtd/smsResponse.dtd\">\n"
				+ "<smsResponse credit=\"20000\"> <okay id=\"53911:41796481111\" notiflink=\"https://www.sms- club.ch/websms/sclub/statusxml.do?msgid=53911&msisdn=+41796481111\"/> </smsResponse>";

	}

	private String doHttpRequest(final Context context, final HttpOptions options)
			throws IOException {
		// send message
		HttpResponse response = Utils.getHttpClient(options);

		// evaluate response
		int resp = response.getStatusLine().getStatusCode();
		if (resp != HttpURLConnection.HTTP_OK) {
			this.checkReturnCode(context, resp);
			throw new WebSMSException(context, R.string.error_http, " " + resp);
		}

		String htmlText = Utils.stream2str(response.getEntity().getContent()).trim();
		return htmlText;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doSend(final Context context, final Intent intent) {
		this.sendData(context, new ConnectorCommand(intent));
	}

	@Override
	protected void doUpdate(final Context context, final Intent intent) throws IOException {
		// not implemented by the API, but still required for the balance to be
		// updated. must be a bug in websms version 4.5
	}
}
