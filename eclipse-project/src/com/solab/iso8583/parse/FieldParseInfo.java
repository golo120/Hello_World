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
package com.solab.iso8583.parse;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;

/** This class contains the information needed to parse a field from a message buffer.
 * 
 * @author Enrique Zamudio
 */
public class FieldParseInfo {

	private IsoType type;
	private int length;

	/** Creates a new instance that parses a value of the specified type, with the specified length.
	 * The length is only useful for ALPHA and NUMERIC types.
	 * @param t The ISO type to be parsed.
	 * @param len The length of the data to be read (useful only for ALPHA and NUMERIC types). */
	public FieldParseInfo(IsoType t, int len) {
		type = t;
		length = len;
	}

	/** Returns the specified length for the data to be parsed. */
	public int getLength() {
		return length;
	}

	/** Returns the data type for the data to be parsed. */
	public IsoType getType() {
		return type;
	}

	/** Parses the data from the buffer and returns the IsoValue with the correct data type in it. */
	public IsoValue<?> parse(byte[] buf, int pos) throws ParseException {
		if (type == IsoType.NUMERIC || type == IsoType.ALPHA) {
			return new IsoValue<String>(type, new String(buf, pos, length), length);
		} else if (type == IsoType.LLVAR) {
			length = ((buf[pos] - 48) * 10) + (buf[pos + 1] - 48);
			return new IsoValue<String>(type, new String(buf, pos + 2, length));
		} else if (type == IsoType.LLLVAR) {
			length = ((buf[pos] - 48) * 100) + ((buf[pos + 1] - 48) * 10) + (buf[pos + 2] - 48);
			return new IsoValue<String>(type, new String(buf, pos + 3, length));
		} else if (type == IsoType.AMOUNT) {
			byte[] c = new byte[13];
			System.arraycopy(buf, pos, c, 0, 10);
			System.arraycopy(buf, pos + 10, c, 11, 2);
			c[10] = '.';
			return new IsoValue<BigDecimal>(type, new BigDecimal(new String(c)));
		} else if (type == IsoType.DATE10) {
			//A SimpleDateFormat in the case of dates won't help because of the missing data
			//we have to use the current date for reference and change what comes in the buffer
			Calendar cal = Calendar.getInstance();
			//Set the month in the date
			cal.set(Calendar.MONTH, ((buf[pos] - 48) * 10) + buf[pos + 1] - 49);
			cal.set(Calendar.DATE, ((buf[pos + 2] - 48) * 10) + buf[pos + 3] - 48);
			cal.set(Calendar.HOUR_OF_DAY, ((buf[pos + 4] - 48) * 10) + buf[pos + 5] - 48);
			cal.set(Calendar.MINUTE, ((buf[pos + 6] - 48) * 10) + buf[pos + 7] - 48);
			cal.set(Calendar.SECOND, ((buf[pos + 8] - 48) * 10) + buf[pos + 9] - 48);
			return new IsoValue<Date>(type, cal.getTime());
		} else if (type == IsoType.DATE4) {
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			//Set the month in the date
			cal.set(Calendar.MONTH, ((buf[pos] - 48) * 10) + buf[pos + 1] - 49);
			cal.set(Calendar.DATE, ((buf[pos + 2] - 48) * 10) + buf[pos + 3] - 48);
			return new IsoValue<Date>(type, cal.getTime());
		} else if (type == IsoType.DATE_EXP) {
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			//Set the month in the date
			cal.set(Calendar.YEAR, ((cal.get(Calendar.YEAR) % 100) * 100) + ((buf[pos] - 48) * 10) + buf[pos] - 48);
			cal.set(Calendar.MONTH, ((buf[pos + 2] - 48) * 10) + buf[pos + 3] - 49);
			return new IsoValue<Date>(type, cal.getTime());
		} else if (type == IsoType.TIME) {
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR_OF_DAY, ((buf[pos] - 48) * 10) + buf[pos + 1] - 48);
			cal.set(Calendar.MINUTE, ((buf[pos + 2] - 48) * 10) + buf[pos + 3] - 48);
			cal.set(Calendar.SECOND, ((buf[pos + 4] - 48) * 10) + buf[pos + 5] - 48);
			return new IsoValue<Date>(type, cal.getTime());
		}
		return null;
	}

}
