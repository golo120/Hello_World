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
package com.solab.iso8583.parse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.MessageFactory;

/** This class is used to parse a XML configuration file and configure
 * a MessageFactory with the values from it.
 * 
 * @author Enrique Zamudio
 */
public class ConfigParser {

	private final static Log log = LogFactory.getLog(ConfigParser.class);

	/** Creates a message factory configured from the default file, which is j8583.xml
	 * located in the root of the classpath. */
	public static MessageFactory createDefault() throws IOException {
		if (MessageFactory.class.getClassLoader().getResource("j8583.xml") == null) {
			log.warn("j8583.xml not found, returning empty message factory");
			return new MessageFactory();
		} else {
			return createFromClasspathConfig("j8583.xml");
		}
	}

	/** Creates a message factory from the specified path inside the classpath. */
	public static MessageFactory createFromClasspathConfig(String path) throws IOException {
		InputStream ins = MessageFactory.class.getClassLoader().getResourceAsStream(path);
		MessageFactory mfact = new MessageFactory();
		if (ins != null) {
			if (log.isDebugEnabled()) {
				log.debug("Parsing config from classpath file " + path);
			}
			try {
				parse(mfact, ins);
			} finally {
				ins.close();
			}
		} else {
			log.warn("File not found in classpath: " + path);
		}
		return mfact;
	}

	/** Creates a message factory from the file located at the specified URL. */
	public static MessageFactory createFromUrl(URL url) throws IOException {
		MessageFactory mfact = new MessageFactory();
		InputStream stream = url.openStream();
		try {
			parse(mfact, stream);
		} finally {
			stream.close();
		}
		return mfact;
	}

	/** Reads the XML from the stream and configures the message factory with its values.
	 * @param mfact The message factory to be configured with the values read from the XML.
	 * @param stream The InputStream containing the XML configuration. */
	protected static void parse(MessageFactory mfact, InputStream stream) throws IOException {
		final DocumentBuilderFactory docfact = DocumentBuilderFactory.newInstance();
		DocumentBuilder docb = null;
		Document doc = null;
		try {
			docb = docfact.newDocumentBuilder();
			doc = docb.parse(stream);
		} catch (ParserConfigurationException ex) {
			log.error("Cannot parse XML configuration", ex);
			return;
		} catch (SAXException ex) {
			log.error("Parsing XML configuration", ex);
		}
		final Element root = doc.getDocumentElement();

		//Read the ISO headers
		NodeList nodes = root.getElementsByTagName("header");
		Element elem = null;
		for (int i = 0; i < nodes.getLength(); i++) {
			elem = (Element)nodes.item(i);
			int type = parseType(elem.getAttribute("type"));
			if (type == -1) {
				throw new IOException("Invalid type for header: " + elem.getAttribute("type"));
			}
			if (elem.getChildNodes() == null || elem.getChildNodes().getLength() == 0) {
				throw new IOException("Invalid header element");
			}
			String header = elem.getChildNodes().item(0).getNodeValue();
			if (log.isTraceEnabled()) {
				log.trace("Adding ISO header for type " + elem.getAttribute("type") + ": " + header);
			}
			mfact.setIsoHeader(type, header);
		}

		//Read the message templates
		nodes = root.getElementsByTagName("template");
		for (int i = 0; i < nodes.getLength(); i++) {
			elem = (Element)nodes.item(i);
			int type = parseType(elem.getAttribute("type"));
			if (type == -1) {
				throw new IOException("Invalid type for template: " + elem.getAttribute("type"));
			}
			NodeList fields = elem.getElementsByTagName("field");
			IsoMessage m = new IsoMessage();
			for (int j = 0; j < fields.getLength(); j++) {
				Element f = (Element)fields.item(j);
				int num = Integer.parseInt(f.getAttribute("num"));
				IsoType itype = IsoType.valueOf(f.getAttribute("type"));
				int length = 0;
				if (f.getAttribute("length").length() > 0) {
					length = Integer.parseInt(f.getAttribute("length"));
				}
				String v = f.getChildNodes().item(0).getNodeValue();
				m.setValue(num, v, itype, length);
			}
			mfact.addMessageTemplate(m);
		}

		//Read the parsing guides
		nodes = root.getElementsByTagName("parse");
		for (int i = 0; i < nodes.getLength(); i++) {
			elem = (Element)nodes.item(i);
			int type = parseType(elem.getAttribute("type"));
			if (type == -1) {
				throw new IOException("Invalid type for parse guide: " + elem.getAttribute("type"));
			}
			NodeList fields = elem.getElementsByTagName("field");
			HashMap<Integer, FieldParseInfo> parseMap = new HashMap<Integer, FieldParseInfo>();
			for (int j = 0; j < fields.getLength(); j++) {
				Element f = (Element)fields.item(j);
				int num = Integer.parseInt(f.getAttribute("num"));
				IsoType itype = IsoType.valueOf(f.getAttribute("type"));
				int length = 0;
				if (f.getAttribute("length").length() > 0) {
					length = Integer.parseInt(f.getAttribute("length"));
				}
				parseMap.put(num, new FieldParseInfo(itype, length));
			}
			mfact.setParseMap(type, parseMap);
		}

	}

	/** Parses a message type expressed as a hex string and returns the integer number.
	 * For example, "0200" or "200" return the number 512 (0x200) */
	private static int parseType(String type) throws IOException {
		if (type.length() % 2 == 1) {
			type = "0" + type;
		}
		if (type.length() != 4) {
			return -1;
		}
		return ((type.charAt(0) - 48) << 12) | ((type.charAt(1) - 48) << 8)
			| ((type.charAt(2) - 48) << 4) | (type.charAt(3) - 48);
	}

}
