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

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solab.iso8583.parse.ConfigParser;
import com.solab.iso8583.parse.FieldParseInfo;

/** This class is used to create messages, either from scratch or from an existing String or byte
 * buffer. It can be configured to put default values on newly created messages, and also to know
 * what to expect when reading messages from an InputStream.
 * <P>
 * The factory can be configured to know what values to set for newly created messages, both from
 * a template (useful for fields that must be set with the same value for EVERY message created)
 * and individually (for trace [field 11] and message date [field 7]).
 * <P>
 * It can also be configured to know what fields to expect in incoming messages (all possible values
 * must be stated, indicating the date type for each). This way the messages can be parsed from
 * a byte buffer.
 * 
 * @author Enrique Zamudio
 */
public class MessageFactory {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	/** This map stores the message template for each message type. */
	private Map typeTemplates = new HashMap();
	/** Stores the information needed to parse messages sorted by type. */
	private Map parseMap = new HashMap();
	/** Stores the field numbers to be parsed, in order of appearance. */
	private Map parseOrder = new HashMap();

	private TraceNumberGenerator traceGen;
	/** The ISO header to be included in each message type. */
	private Map isoHeaders = new HashMap();
	/** A map for the custom field encoder/decoders, keyed by field number. */
	private Map customFields = new HashMap();
	/** Indicates if the current date should be set on new messages (field 7). */
	private boolean setDate;
	/** Indicates if the factory should create binary messages and also parse binary messages. */
	private boolean useBinary;
	private int etx = -1;

	/** Specifies a map for custom field encoder/decoders. The keys are the field numbers. */
	public void setCustomFields(Map value) {
		customFields = value;
	}

	/** Sets the CustomField encoder for the specified field number. */
	public void setCustomField(int index, CustomField value) {
		customFields.put(new Integer(index), value);
	}
	/** Returns a custom field encoder/decoder for the specified field number, if one is available. */
	public CustomField getCustomField(int index) {
		return (CustomField)customFields.get(new Integer(index));
	}
	/** Returns a custom field encoder/decoder for the specified field number, if one is available. */
	public CustomField getCustomField(Integer index) {
		return (CustomField)customFields.get(index);
	}

	/** Tells the receiver to read the configuration at the specified path. This just calls
	 * ConfigParser.configureFromClasspathConfig() with itself and the specified path at arguments,
	 * but is really convenient in case the MessageFactory is being configured from within, say, Spring. */
	public void setConfigPath(String path) throws IOException {
		ConfigParser.configureFromClasspathConfig(this, path);
	}

	/** Tells the receiver to create and parse binary messages if the flag is true.
	 * Default is false, that is, create and parse ASCII messages. */
	public void setUseBinaryMessages(boolean flag) {
		useBinary = flag;
	}
	/** Returns true is the factory is set to create and parse binary messages,
	 * false if it uses ASCII messages. Default is false. */
	public boolean getUseBinaryMessages() {
		return useBinary;
	}

	/** Sets the ETX character to be sent at the end of the message. This is optional and the
	 * default is -1, which means nothing should be sent as terminator.
	 * @param value The ASCII value of the ETX character or -1 to indicate no terminator should be used. */
	public void setEtx(int value) {
		etx = value;
	}
	public int getEtx() {
		return etx;
	}

	/** Creates a new message of the specified type, with optional trace and date values as well
	 * as any other values specified in a message template. If the factory is set to use binary
	 * messages, then the returned message will be written using binary coding.
	 * @param type The message type, for example 0x200, 0x400, etc. */
	public IsoMessage newMessage(int type) {
		IsoMessage m = new IsoMessage((String)isoHeaders.get(new Integer(type)));
		m.setType(type);
		m.setEtx(etx);
		m.setBinary(useBinary);

		//Copy the values from the template
		IsoMessage templ = (IsoMessage)typeTemplates.get(new Integer(type));
		if (templ != null) {
			for (int i = 2; i < 128; i++) {
				if (templ.hasField(i)) {
					m.setField(i, (IsoValue)templ.getField(i).clone());
				}
			}
		}
		if (traceGen != null) {
			m.setValue(11, new Integer(traceGen.nextTrace()), IsoType.NUMERIC, 6);
		}
		if (setDate) {
			m.setValue(7, new Date(), IsoType.DATE10, 10);
		}
		return m;
	}

	/** Creates a message to respond to a request. Increments the message type by 16,
	 * sets all fields from the template if there is one, and copies all values from the request,
	 * overwriting fields from the template if they overlap.
	 * @param request An ISO8583 message with a request type (ending in 00). */
	public IsoMessage createResponse(IsoMessage request) {
		IsoMessage resp = new IsoMessage((String)isoHeaders.get(new Integer(request.getType() + 16)));
		resp.setBinary(request.isBinary());
		resp.setType(request.getType() + 16);
		resp.setEtx(etx);
		//Copy the values from the template
		IsoMessage templ = (IsoMessage)typeTemplates.get(new Integer(resp.getType()));
		if (templ != null) {
			for (int i = 2; i < 128; i++) {
				if (templ.hasField(i)) {
					resp.setField(i, (IsoValue)templ.getField(i).clone());
				}
			}
		}
		for (int i = 2; i < 128; i++) {
			if (request.hasField(i)) {
				resp.setField(i, (IsoValue)request.getField(i).clone());
			}
		}
		return resp;
	}

	/** Creates a new message instance from the buffer, which must contain a valid ISO8583
	 * message. If the factory is set to use binary messages then it will try to parse
	 * a binary message.
	 * @param buf The byte buffer containing the message. Must not include the length header.
	 * @param isoHeaderLength The expected length of the ISO header, after which the message type
	 * and the rest of the message must come. */
	public IsoMessage parseMessage(byte[] buf, int isoHeaderLength) throws ParseException {
		IsoMessage m = new IsoMessage(isoHeaderLength > 0 ? new String(buf, 0, isoHeaderLength) : null);
		//TODO it only parses ASCII messages for now
		int type = 0;
		if (useBinary) {
			type = ((buf[isoHeaderLength] & 0xff) << 8) | (buf[isoHeaderLength + 1] & 0xff);
		} else {
			type = ((buf[isoHeaderLength] - 48) << 12)
			| ((buf[isoHeaderLength + 1] - 48) << 8)
			| ((buf[isoHeaderLength + 2] - 48) << 4)
			| (buf[isoHeaderLength + 3] - 48);
		}
		m.setType(type);
		//Parse the bitmap (primary first)
		BitSet bs = new BitSet(64);
		int pos = 0;
		if (useBinary) {
			for (int i = isoHeaderLength + 2; i < isoHeaderLength + 10; i++) {
				int bit = 128;
				for (int b = 0; b < 8; b++) {
					bs.set(pos++, (buf[i] & bit) != 0);
					bit >>= 1;
				}
			}
			//Check for secondary bitmap and parse if necessary
			if (bs.get(0)) {
				for (int i = isoHeaderLength + 10; i < isoHeaderLength + 18; i++) {
					int bit = 128;
					for (int b = 0; b < 8; b++) {
						bs.set(pos++, (buf[i] & bit) != 0);
						bit >>= 1;
					}
				}
				pos = 18 + isoHeaderLength;
			} else {
				pos = 10 + isoHeaderLength;
			}
		} else {
			try {
				for (int i = isoHeaderLength + 4; i < isoHeaderLength + 20; i++) {
					int hex = Integer.parseInt(new String(buf, i, 1), 16);
					bs.set(pos++, (hex & 8) > 0);
					bs.set(pos++, (hex & 4) > 0);
					bs.set(pos++, (hex & 2) > 0);
					bs.set(pos++, (hex & 1) > 0);
				}
				//Check for secondary bitmap and parse it if necessary
				if (bs.get(0)) {
					for (int i = isoHeaderLength + 20; i < isoHeaderLength + 36; i++) {
						int hex = Integer.parseInt(new String(buf, i, 1), 16);
						bs.set(pos++, (hex & 8) > 0);
						bs.set(pos++, (hex & 4) > 0);
						bs.set(pos++, (hex & 2) > 0);
						bs.set(pos++, (hex & 1) > 0);
					}
					pos = 36 + isoHeaderLength;
				} else {
					pos = 20 + isoHeaderLength;
				}
			} catch (NumberFormatException ex) {
				ParseException _e = new ParseException("Invalid ISO8583 bitmap", pos);
				_e.initCause(ex);
				throw _e;
			}
		}
		//Parse each field
		Integer itype = new Integer(type);
		Map parseGuide = (Map)parseMap.get(itype);
		List index = (List)parseOrder.get(itype);
		if (index == null) {
			log.error("ISO8583 MessageFactory has no parsing guide for message type " + Integer.toHexString(type)
				+ "[" + new String(buf) + "]");
			return null;
		}
		//First we check if the message contains fields not specified in the parsing template
		boolean abandon = false;
		for (int i = 1; i < bs.length(); i++) {
			if (bs.get(i) && !index.contains(new Integer(i+1))) {
				log.warn("ISO8583 MessageFactory cannot parse field " + (i+1) + ": unspecified in parsing guide");
				abandon = true;
			}
		}
		if (abandon) {
			return m;
		}
		//Now we parse each field
		for (Iterator iter = index.iterator(); iter.hasNext();) {
			Integer i = (Integer)iter.next();
			FieldParseInfo fpi = (FieldParseInfo)parseGuide.get(i);
			if (bs.get(i.intValue() - 1)) {
				IsoValue val = useBinary ? fpi.parseBinary(buf, pos, getCustomField(i)) : fpi.parse(buf, pos, getCustomField(i));
				m.setField(i.intValue(), val);
				if (useBinary && !(val.getType() == IsoType.ALPHA || val.getType() == IsoType.LLVAR
						|| val.getType() == IsoType.LLLVAR)) {
					pos += (val.getLength() / 2) + (val.getLength() % 2);
				} else {
					pos += val.getLength();
				}
				if (val.getType() == IsoType.LLVAR) {
					pos += useBinary ? 1 : 2;
				} else if (val.getType() == IsoType.LLLVAR) {
					pos += useBinary ? 2 : 3;
				}
			}
		}
		m.setBinary(useBinary);
		return m;
	}

	/** Sets whether the factory should set the current date on newly created messages,
	 * in field 7. Default is false. */
	public void setAssignDate(boolean flag) {
		setDate = flag;
	}
	/** Returns true if the factory is assigning the current date to newly created messages
	 * (field 7). Default is false. */
	public boolean getAssignDate() {
		return setDate;
	}

	/** Sets the generator that this factory will get new trace numbers from. There is no
	 * default generator. */
	public void setTraceNumberGenerator(TraceNumberGenerator value) {
		traceGen = value;
	}
	/** Returns the generator used to assign trace numbers to new messages. */
	public TraceNumberGenerator getTraceNumberGenerator() {
		return traceGen;
	}

	/** Sets the ISO header to be used in each message type.
	 * @param value A map where the keys are the message types and the values are the ISO headers.
	 */
	public void setIsoHeaders(Map value) {
		isoHeaders.clear();
		isoHeaders.putAll(value);
	}

	/** Sets the ISO header for a specific message type.
	 * @param type The message type, for example 0x200.
	 * @param value The ISO header, or NULL to remove any headers for this message type. */
	public void setIsoHeader(int type, String value) {
		if (value == null) {
			isoHeaders.remove(new Integer(type));
		} else {
			isoHeaders.put(new Integer(type), value);
		}
	}

	/** Returns the ISO header used for the specified type. */
	public String getIsoHeader(int type) {
		return (String)isoHeaders.get(new Integer(type));
	}

	/** Adds a message template to the factory. If there was a template for the same
	 * message type as the new one, it is overwritten. */
	public void addMessageTemplate(IsoMessage templ) {
		if (templ != null) {
			typeTemplates.put(new Integer(templ.getType()), templ);
		}
	}

	/** Removes the message template for the specified type. */
	public void removeMessageTemplate(int type) {
		typeTemplates.remove(new Integer(type));
	}

	/** Returns the template for the specified message type. This allows templates to be modified
	 * programatically. */
	public IsoMessage getMessageTemplate(int type) {
		return (IsoMessage)typeTemplates.get(new Integer(type));
	}

	/** Sets a map with the fields that are to be expected when parsing a certain type of
	 * message.
	 * @param type The message type.
	 * @param map A map of FieldParseInfo instances, each of which define what type and length
	 * of field to expect. The keys will be the field numbers. */
	public void setParseMap(Integer type, Map map) {
		parseMap.put(type, map);
		ArrayList index = new ArrayList();
		index.addAll(map.keySet());
		Collections.sort(index);
		log.trace("Adding parse map for type " + Integer.toHexString(type.intValue()) + " with fields " + index);
		parseOrder.put(type, index);
	}

}
