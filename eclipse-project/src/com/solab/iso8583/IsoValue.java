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
			if (value instanceof Number) {
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
	public void write(OutputStream outs) throws IOException {
		String v = toString();
		if (type == IsoType.LLLVAR || type == IsoType.LLVAR) {
			length = v.length();
			if (length > 100) {
				outs.write((length / 100) + 48);
			} else if (type == IsoType.LLLVAR) {
				outs.write(48);
			}
			if (length >= 10) {
				outs.write(((length % 100) / 10) + 48);
			} else {
				outs.write(48);
			}
			outs.write((length % 10) + 48);
		}
		outs.write(v.getBytes());
	}

}
