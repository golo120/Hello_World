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
package j8583.example;

import java.io.InputStreamReader;
import java.io.LineNumberReader;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.impl.SimpleTraceGenerator;
import com.solab.iso8583.parse.ConfigParser;

/** This little example program creates a message factory out of a XML config file,
 * creates a new message, and parses a couple of message from a text file.
 * 
 * @author Enrique Zamudio
 */
public class Example {

	public static void print(IsoMessage m) {
		System.out.println("TYPE: " + Integer.toHexString(m.getType()));
		for (int i = 2; i < 128; i++) {
			if (m.hasField(i)) {
				System.out.println("F " + i + ": " + m.getObjectValue(i) + " -> '" + m.getField(i).toString() + "'");
			}
		}
	}

	public static void main(String[] args) throws Exception {
		MessageFactory mfact = ConfigParser.createFromClasspathConfig("j8583/example/config.xml");
		mfact.setAssignDate(true);
		mfact.setTraceNumberGenerator(new SimpleTraceGenerator((int)(System.currentTimeMillis() % 100000)));
		LineNumberReader reader = new LineNumberReader(new InputStreamReader(Example.class.getClassLoader().getResourceAsStream("j8583/example/parse.txt")));
		String line = reader.readLine();
		while (line != null && line.length() > 0) {
			IsoMessage m = mfact.parseMessage(line.getBytes(), 12);
			print(m);
			line = reader.readLine();
		}
		reader.close();
		
		//Create a new message
		System.out.println("NEW MESSAGE");
		IsoMessage m = mfact.newMessage(0x200);
		print(m);
	}

}
