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

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Defines the possible values types that can be used in the fields.
 * Some types required the length of the value to be specified (NUMERIC
 * and ALPHA). Other types have a fixed length, like dates and times.
 * Other types do not require a length to be specified, like LLVAR
 * and LLLVAR.
 * 
 * @author Enrique Zamudio
 */
public final class IsoType {

	/** A fixed-length numeric value. It is zero-filled to the left. */
	public final static IsoType NUMERIC = new IsoType(true, 0);
	/** A fixed-length alphanumeric value. It is filled with spaces to the right. */
	public final static IsoType ALPHA = new IsoType(true, 0);
	/** A variable length alphanumeric value with a 2-digit header length. */
	public final static IsoType LLVAR = new IsoType(false, 0);
	/** A variable length alphanumeric value with a 3-digit header length. */
	public final static IsoType LLLVAR = new IsoType(false, 0);
	/** A date in format MMddHHmmss */
	public final static IsoType DATE10 = new IsoType(false, 10);
	/** A date in format MMdd */
	public final static IsoType DATE4 = new IsoType(false, 4);
	/** A date in format yyMM */
	public final static IsoType DATE_EXP = new IsoType(false, 4);
	/** Time of day in format HHmmss */
	public final static IsoType TIME = new IsoType(false, 6);
	/** An amount, expressed in cents with a fixed length of 12. */
	public final static IsoType AMOUNT = new IsoType(false, 12);

	private boolean needsLen;
	private int length;

	private IsoType(boolean flag, int l) {
		needsLen = flag;
		length = l;
	}

	/** Returns true if the type needs a specified length. */
	public boolean needsLength() {
		return needsLen;
	}

	/** Returns the length of the type if it's always fixed, or 0 if it's variable. */
	public int getLength() {
		return length;
	}

	/** Formats a Date if the receiver is DATE10, DATE4, DATE_EXP or TIME; throws an exception
	 * otherwise. */
	public String format(Date value) {
		if (this == DATE10) {
			return new SimpleDateFormat("MMddHHmmss").format(value);
		} else if (this == DATE4) {
			return new SimpleDateFormat("MMdd").format(value);
		} else if (this == DATE_EXP) {
			return new SimpleDateFormat("yyMM").format(value);
		} else if (this == TIME) {
			return new SimpleDateFormat("HHmmss").format(value);
		}
		throw new IllegalArgumentException("Cannot format date as " + this);
	}

	/** Formats the string to the given length (length is only useful if type is ALPHA). */
	public String format(String value, int length) {
		if (this == ALPHA) {
	    	if (value == null) {
	    		value = "";
	    	}
	        if (value.length() > length) {
	            return value.substring(0, length);
	        }
	        char[] c = new char[length];
	        System.arraycopy(value.toCharArray(), 0, c, 0, value.length());
	        for (int i = value.length(); i < c.length; i++) {
	            c[i] = ' ';
	        }
	        return new String(c);
		} else if (this == LLVAR || this == LLLVAR) {
			return value;
		} else if (this == NUMERIC) {
	        char[] c = new char[length];
	        char[] x = value.toCharArray();
	        if (x.length > length) {
	        	throw new IllegalArgumentException("Numeric value is larger than intended length: " + value + " LEN " + length);
	        }
	        int lim = c.length - x.length;
	        for (int i = 0; i < lim; i++) {
	            c[i] = '0';
	        }
	        System.arraycopy(x, 0, c, lim, x.length);
	        return new String(c);
		}
		throw new IllegalArgumentException("Cannot format String as " + this);
	}

	/** Formats the integer value as a NUMERIC, an AMOUNT, or a String. */
	public String format(long value, int length) {
		if (this == NUMERIC) {
	        char[] c = new char[length];
	        char[] x = Long.toString(value).toCharArray();
	        if (x.length > length) {
	        	throw new IllegalArgumentException("Numeric value is larger than intended length: " + value + " LEN " + length);
	        }
	        int lim = c.length - x.length;
	        for (int i = 0; i < lim; i++) {
	            c[i] = '0';
	        }
	        System.arraycopy(x, 0, c, lim, x.length);
	        return new String(c);
		} else if (this == ALPHA || this == LLVAR || this == LLLVAR) {
			return format(Long.toString(value), length);
		} else if (this == AMOUNT) {
			String v = Long.toString(value);
			char[] digits = new char[12];
			for (int i = 0; i < 12; i++) {
				digits[i] = '0';
			}
			//No hay decimales asi que dejamos los dos ultimos digitos como 0
			System.arraycopy(v.toCharArray(), 0, digits, 10 - v.length(), v.length());
			return new String(digits);
		}
		throw new IllegalArgumentException("Cannot format number as " + this);
	}

	/** Formats the BigDecimal as an AMOUNT, NUMERIC, or a String. */
	public String format(BigDecimal value, int length) {
		if (this == AMOUNT) {
			String v = new DecimalFormat("0000000000.00").format(value);
			return v.substring(0, 10) + v.substring(11);
		} else if (this == NUMERIC) {
			return format(value.longValue(), length);
		} else if (this == ALPHA || this == LLVAR || this == LLLVAR) {
			return format(value.toString(), length);
		}
		throw new IllegalArgumentException("Cannot format BigDecimal as " + this);
	}

	public static IsoType valueOf(String s) {
		if (s.equals("NUMERIC")) {
			return NUMERIC;
		} else if (s.equals("ALPHA")) {
			return ALPHA;
		} else if (s.equals("LLVAR")) {
			return LLVAR;
		} else if (s.equals("LLLVAR")) {
			return LLLVAR;
		} else if (s.equals("DATE10")) {
			return DATE10;
		} else if (s.equals("DATE4")) {
			return DATE4;
		} else if (s.equals("DATE_EXP")) {
			return DATE_EXP;
		} else if (s.equals("TIME")) {
			return TIME;
		} else if (s.equals("AMOUNT")) {
			return AMOUNT;
		}
		throw new IllegalArgumentException("Unexistent IsoType " + s);
	}

}
