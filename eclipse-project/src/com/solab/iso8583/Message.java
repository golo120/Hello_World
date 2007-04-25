package com.solab.iso8583;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;

/** Represents an ISO8583 message. This is the core class of the framework.
 * Contains the bitmap which is modified as fields are added/removed.
 * 
 * @author Enrique Zamudio
 */
public class Message {

	static final byte[] HEX = new byte[]{ '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	/** The message type. */
    private int type;
    /** Indicates if the message is binary-coded. */
    private boolean binary;
    /** This is where the values are stored. */
    private Map<Integer,IsoValue<?>> fields = new ConcurrentHashMap<Integer,IsoValue<?>>();
    /** Stores the optional ISO header. */
    private String isoHeader;

    /** Creates a new empty message with no values set. */
    Message() {
    }

    Message(String header) {
    	isoHeader = header;
    }

    /** Creates a message by parsing an ISO8583 string representation. The string
     * must ONLY contain the ISO message with optional ISO header. */
    Message(String string, int isoHeaderLength) {
    }

    /** Creates a message by parsing a buffer containing ISO8583 data.
     * This data must not contain header length info; it must start with the ISO header
     * (optional) or the message type. */
    Message(byte[] buffer, int isoHeaderLength) {
    }

    /** Sets the ISO message type. Common values are 0x200, 0x210, 0x400, 0x410, 0x800, 0x810. */
    public void setType(int value) {
    	type = value;
    }
    /** Returns the ISO message type. */
    public int getType() {
    	return type;
    }

    /** Indicates whether the message should be binary. Default is false. */
    public void setBinary(boolean flag) {
    	binary = flag;
    }
    /** Returns true if the message is binary coded; default is false. */
    public boolean isBinary() {
    	return binary;
    }

    /** Returns the stored value in the field, without converting or formatting it. */
    public Object getObjectValue(int field) {
    	IsoValue<?> v = fields.get(field);
    	if (v == null) {
    		return null;
    	}
    	return v.getValue();
    }

    /** Returns the IsoValue for the specified field. */
    public IsoValue<?> getField(int field) {
    	return fields.get(field);
    }

    /** Stored the field in the specified index. The first field is the bitmap and has index 1;
     * index 2 is the extended bitmap; so the first valid value for index must be 3. */
    public void setField(int index, IsoValue<?> field) {
    	if (index < 3 || index > 128) {
    		throw new IndexOutOfBoundsException("Field index must be between 3 and 128");
    	}
    	if (field == null) {
    		fields.remove(index);
    	} else {
    		fields.put(index, field);
    	}
    }

    public void setValue(int index, Object value, IsoType t, int length) {
    	if (value == null) {
    		fields.remove(index);
    	} else {
    		IsoValue v = null;
    		if (t == IsoType.LLVAR || t == IsoType.LLLVAR) {
    			v = new IsoValue<Object>(t, value);
    		} else {
    			v = new IsoValue<Object>(t, value, length);
    		}
    		fields.put(index, v);
    	}
    }

    /** Writes a message to a stream, after writing the specified number of bytes indicating
     * the message's length. The message will first be written to an internal memory stream
     * which will then be dumped into the specified stream. */
    public void write(OutputStream outs, int lengthBytes) throws IOException {
    	ByteArrayOutputStream bout = new ByteArrayOutputStream();
    	if (isoHeader != null) {
    		bout.write(isoHeader.getBytes());
    	}
    	//Message Type
    	if (binary) {
        	bout.write((type & 0xff00) >> 8);
        	bout.write(type & 0xff);
    	} else {
    		String x = Integer.toHexString(type);
    		if (x.length() < 4) {
    			bout.write(48);
    		}
    		bout.write(x.getBytes());
    	}

    	//Bitmap
    	ArrayList<Integer> keys = new ArrayList<Integer>();
    	keys.addAll(fields.keySet());
    	Collections.sort(keys);
    	BitSet bs = new BitSet(64);
    	for (Integer i : keys) {
    		bs.set(i - 1);
    	}
    	//Extend to 128 if needed
    	if (bs.length() > 64) {
    		BitSet b2 = new BitSet(128);
    		b2.or(bs);
    		bs = b2;
    	}
    	//Write bitmap to stream
    	if (binary) {
    		int pos = 0;
    		int b = 0;
    		for (int i = 0; i < bs.size(); i++) {
    			if (bs.get(i)) {
    				b |= 1 << pos;
    			}
    			pos++;
    			if (pos == 8) {
    				pos = 0;
    				b = 0;
    				bout.write(b);
    			}
    		}
    	} else {
            int pos = 0;
            int lim = bs.size() / 4;
            for (int i = 0; i < lim; i++) {
                int nibble = 0;
                if (bs.get(pos++))
                    nibble += 8;
                if (bs.get(pos++))
                    nibble += 4;
                if (bs.get(pos++))
                    nibble += 2;
                if (bs.get(pos++))
                    nibble++;
                bout.write(HEX[nibble]);
            }
    	}

    	//Fields
    	for (Integer i : keys) {
    		IsoValue v = fields.get(i);
    		v.write(bout);
    	}
    	if (lengthBytes > 0) {
    		int l = bout.size();
    		byte[] buf = new byte[lengthBytes];
    		//TODO
    		bout.write(buf);
    	}
    	bout.writeTo(outs);
    }

    public static void main(String[] args) throws Exception {
    	Message m = new Message("ISOHEADER");
    	m.setType(0x200);
    	m.setValue(3, 650000, IsoType.NUMERIC, 6);
    	m.setValue(4, 50, IsoType.AMOUNT, 0);
    	m.setValue(7, new Date(), IsoType.DATE10, 0);
    	m.setValue(11, 123, IsoType.NUMERIC, 6);
    	m.setValue(32, "Hola", IsoType.ALPHA, 10);
    	m.setValue(48, "Probando", IsoType.LLLVAR, 0);
    	m.write(System.out, 0);
    	System.out.println();
    	m.setValue(92, 5, IsoType.NUMERIC, 1);
    	m.write(System.out, 0);
    	System.out.println();
    }

}
