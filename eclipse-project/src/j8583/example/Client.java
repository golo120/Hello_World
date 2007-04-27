package j8583.example;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.Socket;
import java.text.ParseException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.impl.SimpleTraceGenerator;
import com.solab.iso8583.parse.ConfigParser;

/** Implements a very simple TCP client application that connects to a server and sends some
 * requests, displaying the response codes and confirmations.
 * 
 * @author Enrique Zamudio
 */
public class Client extends Thread {

	private static final Log log = LogFactory.getLog(Client.class);

	private static final String[] data = new String[]{
		"1234567890", "5432198765", "1020304050", "abcdefghij", "AbCdEfGhIj",
		"1a2b3c4d5e", "A1B2C3D4E5", "a0c0d0f0e0", "j5k4m3nh45"
	};
	private static final BigDecimal[] amounts = new BigDecimal[]{
		new BigDecimal("10"), new BigDecimal("20.50"), new BigDecimal("37.44")
	};
	private static MessageFactory mfact;
	private static ConcurrentHashMap<String, IsoMessage> pending = new ConcurrentHashMap<String, IsoMessage>();

	private Socket sock;

	public Client(Socket socket) {
		sock = socket;
	}

	public void run() {
		byte[] lenbuf = new byte[2];
		try {
			while (sock != null && sock.isConnected() && !isInterrupted()) {
				sock.getInputStream().read(lenbuf);
				int size = ((lenbuf[0] & 0xff) << 8) | (lenbuf[1] & 0xff);
				byte[] buf = new byte[size];
				//We're not expecting ETX in this case
				if (sock.getInputStream().read(buf) == size) {
					try {
						IsoMessage resp = mfact.parseMessage(buf, 12);
						log.debug("Read response " + resp.getField(11) + " conf " + resp.getField(38) + ": " + new String(buf));
						pending.remove(resp.getField(11).toString());
					} catch (ParseException ex) {
						log.error("Parsing response", ex);
					}
				} else {
					pending.clear();
					return;
				}
			}
		} catch (IOException ex) {
			log.error("Reading responses", ex);
		} finally {
			try {
				sock.close();
			} catch (IOException ex) {};
		}
	}

	public static void main(String[] args) throws Exception {
		Random rng = new Random(System.currentTimeMillis());
		log.debug("Reading config");
		mfact = ConfigParser.createFromClasspathConfig("j8583/example/config.xml");
		mfact.setAssignDate(true);
		mfact.setTraceNumberGenerator(new SimpleTraceGenerator((int)(System.currentTimeMillis() % 10000)));
		log.debug("Connecting to server");
		Socket sock = new Socket("localhost", 9999);
		//Send 10 messages, then wait for the responses
		Client reader = new Client(sock);
		reader.start();
		for (int i = 0; i < 10; i++) {
			IsoMessage req = mfact.newMessage(0x200);
			req.setValue(4, amounts[rng.nextInt(amounts.length)], IsoType.AMOUNT, 0);
			req.setValue(12, req.getObjectValue(7), IsoType.TIME, 0);
			req.setValue(13, req.getObjectValue(7), IsoType.DATE4, 0);
			req.setValue(15, req.getObjectValue(7), IsoType.DATE4, 0);
			req.setValue(17, req.getObjectValue(7), IsoType.DATE4, 0);
			req.setValue(37, System.currentTimeMillis() % 1000000, IsoType.NUMERIC, 12);
			req.setValue(41, data[rng.nextInt(data.length)], IsoType.ALPHA, 16);
			req.setValue(48, data[rng.nextInt(data.length)], IsoType.LLLVAR, 0);
			pending.put(req.getField(11).toString(), req);
			log.debug("Sending request " + req.getField(11));
			req.write(sock.getOutputStream(), 2);
		}
		log.debug("Waiting for responses");
		while (pending.size() > 0 && sock.isConnected()) {
			sleep(500);
		}
		reader.interrupt();
		sock.close();
		log.debug("DONE.");
	}

}
