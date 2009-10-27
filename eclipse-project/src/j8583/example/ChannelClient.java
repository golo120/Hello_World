package j8583.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
import java.util.Date;
import java.util.Iterator;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.ParseException;

import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import com.solab.iso8583.impl.SimpleTraceGenerator;
import com.solab.iso8583.parse.ConfigParser;

/** This is basically the same as the Client example but uses java.nio instead of java.io
 *
 * @author Enrique Zamudio
 */
public class ChannelClient implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ChannelClient.class);
    private static final String[] data = new String[]{
        "1234567890", "5432198765", "1020304050", "abcdefghij", "AbCdEfGhIj",
        "1a2b3c4d5e", "A1B2C3D4E5", "a0c0d0f0e0", "j5k4m3nh45"
    };
    private static final BigDecimal[] amounts = new BigDecimal[]{
        new BigDecimal("10"), new BigDecimal("20.50"), new BigDecimal("37.44")
    };
    private static MessageFactory mfact;
    private static ConcurrentHashMap<String, IsoMessage> pending = new ConcurrentHashMap<String, IsoMessage>();
    private boolean done = false;
    private SocketChannel socket;
    private Selector selector;

    private ChannelClient(SocketChannel sock) {
        socket = sock;
    }

    public void run() {
        try {
            socket.configureBlocking(false);
            selector = Selector.open();
            socket.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(512));
            while (socket != null && socket.isConnected()) {
                selector.select();
                for (Iterator<SelectionKey> iter = selector.selectedKeys().iterator(); iter.hasNext(); ) {
                    SelectionKey skey = iter.next();
                    if (skey.isReadable() && skey.isValid()) {
                        ByteBuffer buf = (ByteBuffer)skey.attachment();
                        socket.read(buf);
                        if (buf.position() > 2) {
                            int cuantos = ((buf.get(0) & 0xff) << 8) | (buf.get(1) & 0xff);
                            log.info("buf indica medir {} y tenemos ya {}", cuantos, buf.position()-2);
                            if (cuantos + 2 <= buf.position()) {
                                //Message has been read completely
                                String respHeader = mfact.getIsoHeader(0x200);
                                try {
                                    IsoMessage resp = mfact.parseMessage(buf.array(), respHeader.length() + 2);
                                    log.debug("Read response {} conf {}: {}", new Object[]{
                                        resp.getField(11), resp.getField(38), new String(buf.array(), 2, cuantos)});
                                    pending.remove(resp.getField(11).toString());
                                    IsoValue<?> f48 = resp.getField(48);
                                    if (f48 != null && f48.getValue() instanceof ProductData) {
                                        ProductData v = (ProductData)f48.getValue();
                                        log.debug("Field 48 encoded: '{}' pid:{} cat:{}", new Object[]{
                                            f48, v.getProductId(), v.getCategoryId()});
                                    }
                                } catch (ParseException ex) {
                                    log.error("Parsing message: {}", new String(buf.array(), 2, cuantos));
                                }
                                buf.clear();
                            }
                        }
                    }
                    iter.remove();
                }
            }
        } catch (IOException ex) {
            if (done) {
                log.info("ChannelSocket closed because we're done ({} pending)", pending.size());
            } else {
                log.error(String.format("Reading responses, %d pending", pending.size()), ex);
            }
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {};
            }
        }

    }

    protected void stop() {
        done = true;
        try {
            selector.close();
            socket.close();
        } catch (IOException ex) {
            log.error("Couldn't close socket");
        }
        socket = null;
    }

    public static void main(String[] args) throws Exception {
        Random rng = new Random(System.currentTimeMillis());
        log.debug("Reading config");
        mfact = ConfigParser.createFromClasspathConfig("j8583/example/config.xml");
        mfact.setAssignDate(true);
        mfact.setTraceNumberGenerator(new SimpleTraceGenerator((int)(System.currentTimeMillis() % 10000)));
        mfact.setCustomField(48, new ProductEncoder());
        log.debug("Connecting to server");
        SocketChannel sock = SocketChannel.open(new InetSocketAddress("localhost", 9999));
        ChannelClient client = new ChannelClient(sock);
        Thread reader = new Thread(client, "reader");
        reader.start();
        for (int i = 0; i < 20; i++) {
            IsoMessage req = mfact.newMessage(0x200);
            req.setValue(4, amounts[rng.nextInt(amounts.length)], IsoType.AMOUNT, 0);
            req.setValue(12, req.getObjectValue(7), IsoType.TIME, 0);
            req.setValue(13, req.getObjectValue(7), IsoType.DATE4, 0);
            req.setValue(15, req.getObjectValue(7), IsoType.DATE4, 0);
            req.setValue(17, new Date(System.currentTimeMillis() + (86400*720)), IsoType.DATE_EXP, 0);
            req.setValue(37, System.currentTimeMillis() % 1000000, IsoType.NUMERIC, 12);
            req.setValue(41, data[rng.nextInt(data.length)], IsoType.ALPHA, 16);
            req.setField(48, new IsoValue<ProductData>(IsoType.LLLVAR,
                    new ProductData((int)(System.currentTimeMillis() % 1000), data[rng.nextInt(data.length)]),
                    new ProductEncoder()));
            //req.setValue(48, "string data", IsoType.LLLVAR, 0);
            pending.put(req.getField(11).toString(), req);
            log.debug("Sending request {}", req.getField(11));
            sock.write(req.writeToBuffer(2));
        }
        log.debug("Waiting for responses");
        while (pending.size() > 0 && sock.isConnected()) {
            Thread.sleep(500);
        }
        client.stop();
        reader.interrupt();
        log.debug("DONE.");
    }
}
