package com.solab.iso8583;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Defines the possible values types that can be used in the fields.
 * 
 * @author Enrique Zamudio
 */
public enum IsoType {

	NUMERIC(true, 0),
	ALPHA(true, 0),
	LLVAR(false, 0),
	LLLVAR(false, 0),
	DATE10(false, 10),
	DATE4(false, 4),
	DATE_EXP(false, 4),
	TIME(false, 6),
	AMOUNT(false, 12);

	private boolean needsLen;
	private int length;

	IsoType(boolean flag, int l) {
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
			return v.substring(0, 10) + v.substring(10);
		} else if (this == NUMERIC) {
			return format(value.longValue(), length);
		} else if (this == ALPHA || this == LLVAR || this == LLLVAR) {
			return format(value.toString(), length);
		}
		throw new IllegalArgumentException("Cannot format BigDecimal as " + this);
	}

}
