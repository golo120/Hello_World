/*
j8583 A Java implementation of the ISO8583 protocol
Copyright (C) 2007 Enrique Zamudio Lopez

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
*/
package com.solab.iso8583;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.solab.iso8583.parse.FieldParseInfo;

/** This class is used to create messages, either from scratch or from an existing String or byte
 * buffer. It can be configured to put default values on newly created messages, and also to know
 * what to expect when reading messages from an InputStream.
 * The factory can be configured to know what values to set for newly created messages, both from
 * a template (useful for fields that must be set with the same value for EVERY message created)
 * and individually (for trace [field 11] and message date [field 7]).
 * It can also be configured to know what fields to expect in incoming messages (all possible values
 * must be stated, indicating the date type for each). This way the messages can be parsed from
 * a byte buffer.
 * 
 * @author Enrique Zamudio
 */
public class MessageFactory {

	/** This map stores the message template for each message type. */
	private Map<Integer, IsoMessage> typeTemplates = new HashMap<Integer, IsoMessage>();
	private Map<Integer, Map<Integer, FieldParseInfo>> parseMap = new HashMap<Integer, Map<Integer, FieldParseInfo>>();
	private Map<Integer, List<Integer>> parseOrder = new HashMap<Integer, List<Integer>>();

	private TraceNumberGenerator traceGen;
	/** The ISO header to be included in each message type. */
	private Map<Integer, String> isoHeaders = new HashMap<Integer, String>();
	/** Indicates if the current should be set on new messages (field 7). */
	private boolean setDate;

	public MessageFactory() {
	}

	/** Creates a new message of the specified type, with optional trace and date values as well
	 * as any other values specified in a message template. */
	public IsoMessage newMessage(int type) {
		IsoMessage m = new IsoMessage(isoHeaders.get(type));
		m.setType(type);

		//Copy the values from the template
		IsoMessage templ = typeTemplates.get(type);
		if (templ != null) {
			for (int i = 2; i < 128; i++) {
				m.setField(i, templ.getField(i));
			}
		}
		if (traceGen != null) {
			m.setValue(11, traceGen.nextTrace(), IsoType.NUMERIC, 6);
		}
		if (setDate) {
			m.setValue(7, new Date(), IsoType.DATE10, 10);
		}
		return m;
	}

	/** Creates a new message instance from the buffer, which must contain a valid ISO8583
	 * message.
	 * @param buf The byte buffer containing the message. Must not include the length header.
	 * @param isoHeaderLength The expected length of the ISO header, after which the message type
	 * and the rest of the message must come. */
	public IsoMessage parseMessage(byte[] buf, int isoHeaderLength) throws ParseException {
		IsoMessage m = new IsoMessage(isoHeaderLength > 0 ? new String(buf, 0, isoHeaderLength) : null);
		int type = ((buf[isoHeaderLength] - 48) << 12)
			| ((buf[isoHeaderLength + 1] - 48) << 8)
			| ((buf[isoHeaderLength + 2] - 48) << 4)
			| (buf[isoHeaderLength + 3] - 48);
		m.setType(type);
		//Parse the bitmap (primary first)
		BitSet bs = new BitSet(64);
		int pos = 0;
		for (int i = isoHeaderLength + 4; i < isoHeaderLength + 20; i++) {
			bs.set(pos++, (buf[i] & 8));
			bs.set(pos++, (buf[i] & 4));
			bs.set(pos++, (buf[i] & 2));
			bs.set(pos++, (buf[i] & 1));
		}
		//Check for secondary bitmap and parse it if necessary
		if (bs.get(0)) {
			for (int i = isoHeaderLength + 20; i < isoHeaderLength + 36; i++) {
				bs.set(pos++, (buf[i] & 8));
				bs.set(pos++, (buf[i] & 4));
				bs.set(pos++, (buf[i] & 2));
				bs.set(pos++, (buf[i] & 1));
			}
			pos = 36;
		} else {
			pos = 20;
		}
		//Parse each field
		Map<Integer, FieldParseInfo> parseGuide = parseMap.get(type);
		List<Integer> index = parseOrder.get(type);
		//TODO this should be done at config time
		if (index == null) {
			index = new ArrayList<Integer>();
			index.addAll(parseGuide.keySet());
			Collections.sort(index);
		}
		for (Integer i : index) {
			FieldParseInfo fpi = parseGuide.get(i);
			if (bs.get(i - 1)) {
				IsoValue val = fpi.parse(buf, pos);
				m.setField(i, val);
			}
		}
		return m;
	}

	/** sets whether the factory should set the current date on newly created messages,
	 * in field 7. */
	public void setAssignDate(boolean flag) {
		setDate = true;
	}
	/** Returns true if the factory is assigning the current date to newly created messages
	 * (field 7). */
	public boolean getAssignDate() {
		return setDate;
	}

	/** Sets the generator that this factory will get new trace numbers from. */
	public void setTraceNumberGenerator(TraceNumberGenerator value) {
		traceGen = value;
	}
	/** Returns the generator used to assign trace numbers to new messages. */
	public TraceNumberGenerator getTraceNumberGenerator() {
		return traceGen;
	}

	/** Sets the ISO header to be used in each message type.
	 * @param A map where the keys are the message types and the values are the ISO headers.
	 */
	public void setIsoHeaders(Map<Integer, String> value) {
		isoHeaders.clear();
		isoHeaders.putAll(value);
	}

	/** Sets a message template for a specified message type. When new messages of that type
	 * are created, they will have the same values as the template. */
	public void setMessageType(int type, IsoMessage templ) {
		typeTemplates.put(type, templ);
	}

}
