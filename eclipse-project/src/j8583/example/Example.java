package j8583.example;

import java.io.InputStreamReader;
import java.io.LineNumberReader;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.impl.SimpleTraceGenerator;
import com.solab.iso8583.parse.ConfigParser;

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
