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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Represents an ISO8583 message. This is the core class of the framework.
 * Contains the bitmap which is modified as fields are added/removed.
 * This class makes no assumptions as to what types belong in each field,
 * nor what fields should each different message type have; that is left
 * for the developer, since the different ISO8583 implementations can vary
 * greatly.
 * 
 * @author Enrique Zamudio
 */
public class IsoMessage {

	static final byte[] HEX = new byte[]{ '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	/** The message type. */
    private int type;
    /** Indicates if the message is binary-coded. */
    private boolean binary;
    /** This is where the values are stored. */
    private Map<Integer,IsoValue<?>> fields = new ConcurrentHashMap<Integer,IsoValue<?>>();
    /** Stores the optional ISO header. */
    private String isoHeader;
    private int etx = -1;

    /** Creates a new empty message with no values set. */
    public IsoMessage() {
    }

    /** Creates a new message with the specified ISO header. This will be prepended to the message. */
    IsoMessage(String header) {
    	isoHeader = header;
    }

    /** Returns the ISO header that this message was created with. */
    public String getIsoHeader() {
    	return isoHeader;
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

    /** Sets the ETX character, which is sent at the end of the message as a terminator.
     * Default is -1, which means no terminator is sent. */
    public void setEtx(int value) {
    	etx = value;
    }

    /** Returns the stored value in the field, without converting or formatting it.
     * @param field The field number. 1 is the secondary bitmap and is not returned as such;
     * real fields go from 2 to 128. */
    public Object getObjectValue(int field) {
    	IsoValue<?> v = fields.get(field);
    	if (v == null) {
    		return null;
    	}
    	return v.getValue();
    }

    /** Returns the IsoValue for the specified field. First real field is 2. */
    public IsoValue<?> getField(int field) {
    	return fields.get(field);
    }

    /** Stored the field in the specified index. The first field is the secondary bitmap and has index 1,
     * so the first valid value for index must be 2. */
    public void setField(int index, IsoValue<?> field) {
    	if (index < 2 || index > 128) {
    		throw new IndexOutOfBoundsException("Field index must be between 2 and 128");
    	}
    	if (field == null) {
    		fields.remove(index);
    	} else {
    		fields.put(index, field);
    	}
    }

    /** Sets the specified value in the specified field, creating an IsoValue internally.
     * @param index The field number (2 to 128)
     * @param value The value to be stored.
     * @param t The ISO type.
     * @param length The length of the field, used for ALPHA and NUMERIC values only, ignored
     * with any other type. */
    public void setValue(int index, Object value, IsoType t, int length) {
    	if (index < 2 || index > 128) {
    		throw new IndexOutOfBoundsException("Field index must be between 2 and 128");
    	}
    	if (value == null) {
    		fields.remove(index);
    	} else {
    		IsoValue v = null;
    		if (t.needsLength()) {
    			v = new IsoValue<Object>(t, value, length);
    		} else {
    			v = new IsoValue<Object>(t, value);
    		}
    		fields.put(index, v);
    	}
    }

    /** Returns true is the message has a value in the specified field.
     * @param idx The field number. */
    public boolean hasField(int idx) {
    	return fields.get(idx) != null;
    }

    /** Writes a message to a stream, after writing the specified number of bytes indicating
     * the message's length. The message will first be written to an internal memory stream
     * which will then be dumped into the specified stream. This method flushes the stream
     * after the write. There are at most two write operations to the stream: one for the
     * length header and the other one with the whole message.
     * @param outs The stream to write the message to.
     * @param lengthBytes The size of the message length header. Valid ranges are 0 to 4.
     * @throws IllegalArgumentException if the specified length header is more than 4 bytes.
     * @throws IOException if there is a problem writing to the stream. */
    public void write(OutputStream outs, int lengthBytes) throws IOException {
    	if (lengthBytes > 4) {
    		throw new IllegalArgumentException("The length header can have at most 4 bytes");
    	}
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
    		bs.set(0);
    	}
    	//Write bitmap to stream
    	if (binary) {
    		int pos = 128;
    		int b = 0;
    		for (int i = 0; i < bs.size(); i++) {
    			if (bs.get(i)) {
    				b |= pos;
    			}
    			pos >>= 1;
    			if (pos == 0) {
    				bout.write(b);
    				pos = 128;
    				b = 0;
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
    		v.write(bout, binary);
    	}
    	if (lengthBytes > 0) {
    		int l = bout.size();
    		if (etx > -1) {
    			l++;
    		}
    		byte[] buf = new byte[lengthBytes];
    		int pos = 0;
    		if (lengthBytes == 4) {
    			buf[0] = (byte)((l & 0xff000000) >> 24);
    			pos++;
    		}
    		if (lengthBytes > 2) {
    			buf[pos] = (byte)((l & 0xff0000) >> 16);
    			pos++;
    		}
    		if (lengthBytes > 1) {
    			buf[pos] = (byte)((l & 0xff00) >> 8);
    			pos++;
    		}
    		buf[pos] = (byte)(l & 0xff);
    		outs.write(buf);
    	}
    	bout.writeTo(outs);
    	//ETX
    	if (etx > -1) {
    		outs.write(etx);
    	}
    	outs.flush();
    }

}
