package j8583.example;

import java.io.IOException;
import java.text.ParseException;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.MessageFactory;

/** A simple parser that works with a config file and user's input.
 * 
 * @author Enrique Zamudio
 */
public class Parse {

	public static void main(String[] args) throws IOException, ParseException {
		MessageFactory mfact = new MessageFactory();
		if (args.length > 0) {
			mfact.setConfigPath(args[0]);
		} else {
			mfact.setConfigPath(System.console().readLine("Enter the path to the config file: "));
		}
		String line = System.console().readLine("Enter the message to parse (no ISO header): ");
		while (line != null && line.length() > 0) {
			IsoMessage msg = mfact.parseMessage(line.getBytes(), 0);
			if (msg != null) {
				System.console().printf("Message type: %04x%n", msg.getType());
				for (int i = 1; i <= 128; i++) {
					if (msg.hasField(i)) {
						System.console().printf("Field %3d: '%s' -> '%s'%n", i, msg.getField(i), msg.getObjectValue(i));
					}
				}
			}
			line = System.console().readLine("%nEnter the message to parse (no ISO header): ");
		}
	}

}
