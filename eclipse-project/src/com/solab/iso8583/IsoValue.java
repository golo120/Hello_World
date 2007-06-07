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
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Date;

/** Represents a value that is stored in a field inside an ISO8583 message.
 * It can format the value when the message is generated.
 * Some values have a fixed length, other values require a length to be specified
 * so that the value can be padded to the specified length. LLVAR and LLLVAR
 * values do not need a length specification because the length is calculated
 * from the stored value.
 * 
 * @author Enrique Zamudio
 */
public class IsoValue<T> implements Cloneable {

	private IsoType type;
	private int length;
	private T value;

	/** Creates a new instance that stores the specified value as the specified type.
	 * Useful for storing LLVAR or LLLVAR types, as well as fixed-length value types
	 * like DATE10, DATE4, AMOUNT, etc.
	 * @param t the ISO type.
	 * @param value The value to be stored. */
	public IsoValue(IsoType t, T value) {
		if (t.needsLength()) {
			throw new IllegalArgumentException("Fixed-value types must use constructor that specifies length");
		}
		type = t;
		this.value = value;
		if (type == IsoType.LLVAR || type == IsoType.LLLVAR) {
			length = value.toString().length();
			if (t == IsoType.LLVAR && length > 99) {
				throw new IllegalArgumentException("LLVAR can only hold values up to 99 chars");
			} else if (t == IsoType.LLLVAR && length > 999) {
				throw new IllegalArgumentException("LLLVAR can only hold values up to 999 chars");
			}
		} else {
			length = type.getLength();
		}
	}

	/** Creates a new instance that stores the specified value as the specified type.
	 * Useful for storing fixed-length value types. */
	public IsoValue(IsoType t, T val, int len) {
		type = t;
		value = val;
		length = len;
		if (length == 0 && t.needsLength()) {
			throw new IllegalArgumentException("Length must be greater than zero");
		} else if (t == IsoType.LLVAR || t == IsoType.LLLVAR) {
			length = val.toString().length();
			if (t == IsoType.LLVAR && length > 99) {
				throw new IllegalArgumentException("LLVAR can only hold values up to 99 chars");
			} else if (t == IsoType.LLLVAR && length > 999) {
				throw new IllegalArgumentException("LLLVAR can only hold values up to 999 chars");
			}
		}
	}

	/** Returns the ISO type to which the value must be formatted. */
	public IsoType getType() {
		return type;
	}

	/** Returns the length of the stored value, of the length of the formatted value
	 * in case of NUMERIC or ALPHA. It doesn't include the field length header in case
	 * of LLVAR or LLLVAR. */
	public int getLength() {
		return length;
	}

	/** Returns the stored value without any conversion or formatting. */
	public T getValue() {
		return value;
	}

	/** Returns the formatted value as a String. The formatting depends on the type of the
	 * receiver. */
	public String toString() {
		if (value == null) {
			return "ISOValue<null>";
		}
		if (type == IsoType.NUMERIC || type == IsoType.AMOUNT) {
			if (type == IsoType.AMOUNT) {
				return type.format((BigDecimal)value, 12);
			} else if (value instanceof Number) {
				return type.format(((Number)value).longValue(), length);
			} else {
				return type.format(value.toString(), length);
			}
		} else if (type == IsoType.ALPHA) {
			return type.format(value.toString(), length);
		} else if (type == IsoType.LLLVAR || type == IsoType.LLLVAR) {
			return value.toString();
		} else if (value instanceof Date) {
			return type.format((Date)value);
		}
		return value.toString();
	}

	/** Returns a copy of the receiver that references the same value object. */
	@SuppressWarnings("unchecked")
	public IsoValue<T> clone() {
		try {
			return (IsoValue<T>)super.clone();
		} catch (CloneNotSupportedException ex) {
			return null;
		}
	}

	/** Returns true of the other object is also an IsoValue and has the same type and length,
	 * and if other.getValue().equals(getValue()) returns true. */
	public boolean equals(Object other) {
		if (other == null || !(other instanceof IsoValue)) {
			return false;
		}
		IsoValue comp = (IsoValue)other;
		return (comp.getType() == getType() && comp.getValue().equals(getValue()) && comp.getLength() == getLength());
	}

	/** Writes the formatted value to a stream, with the length header
	 * if it's a variable length type. */
	public void write(OutputStream outs, boolean binary) throws IOException {
		if (type == IsoType.LLLVAR || type == IsoType.LLVAR) {
			if (binary) {
				if (type == IsoType.LLLVAR) {
					outs.write(length / 100); //00 to 09 automatically in BCD
				}
				//BCD encode the rest of the length
				outs.write((((length % 100) / 10) << 4) | (length % 10));
			} else {
				//write the length in ASCII
				if (type == IsoType.LLLVAR) {
					outs.write((length / 100) + 48);
				}
				if (length >= 10) {
					outs.write(((length % 100) / 10) + 48);
				} else {
					outs.write(48);
				}
				outs.write((length % 10) + 48);
			}
		} else if (binary) {
			//numeric types in binary are coded like this
			byte[] buf = null;
			if (type == IsoType.NUMERIC) {
				buf = new byte[(length / 2) + (length % 2)];
			} else if (type == IsoType.AMOUNT) {
				buf = new byte[6];
			} else if (type == IsoType.DATE10 || type == IsoType.DATE4 || type == IsoType.DATE_EXP || type == IsoType.TIME) {
				buf = new byte[length / 2];
			}
			//Encode in BCD if it's one of these types
			if (buf != null) {
				toBcd(toString(), buf);
				outs.write(buf);
				return;
			}
		}
		//Just write the value as text
		outs.write(toString().getBytes());
	}

	/** Encode the value as BCD and put it in the buffer. The buffer must be big enough
	 * to store the digits in the original value (half the length of the string). */
	private void toBcd(String value, byte[] buf) {
		int charpos = 0; //char where we start
		int bufpos = 0;
		if (value.length() % 2 == 1) {
			//for odd lengths we encode just the first digit in the first byte
			buf[0] = (byte)(value.charAt(0) - 48);
			charpos = 1;
			bufpos = 1;
		}
		//encode the rest of the string
		while (charpos < value.length()) {
			buf[bufpos] = (byte)(((value.charAt(charpos) - 48) << 4)
					| (value.charAt(charpos + 1) - 48));
			charpos += 2;
			bufpos++;
		}
	}

}
