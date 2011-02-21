package com.solab.iso8583.parse;

import java.text.ParseException;

import com.solab.iso8583.CustomField;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import com.solab.iso8583.util.HexCodec;

/** This class is used to parse fields of type BINARY.
 * 
 * @author Enrique Zamudio
 */
public class BinaryParseInfo extends FieldParseInfo {

	
	public BinaryParseInfo(int len) {
		super(IsoType.BINARY, len);
	}

	@Override
	public <T> IsoValue<?> parse(byte[] buf, int pos, CustomField<T> custom)
			throws ParseException {
		if (pos < 0) {
			throw new ParseException(String.format("Invalid position %d", pos), pos);
		}
		if (pos+(length*2) > buf.length) {
			throw new ParseException(String.format("Insufficient data for BINARY field of length %d, pos %d",
				length, pos), pos);
		}
		byte[] binval = HexCodec.hexDecode(new String(buf, pos, length*2));
		if (custom == null) {
			return new IsoValue<byte[]>(type, binval, binval.length, null);
		} else {
			IsoValue<T> v = new IsoValue<T>(type, custom.decodeField(new String(buf, pos, length*2)), length, custom);
			if (v.getValue() == null) {
				return new IsoValue<byte[]>(type, binval, binval.length, null);
			}
			return v;
		}
	}

	@Override
	public <T> IsoValue<?> parseBinary(byte[] buf, int pos,
			CustomField<T> custom) throws ParseException {
		byte[] _v = new byte[length];
		System.arraycopy(buf, pos, _v, 0, length);
		if (custom == null) {
			return new IsoValue<byte[]>(type, _v, length, null);
		} else {
			IsoValue<T> v = new IsoValue<T>(type, custom.decodeField(HexCodec.hexEncode(_v)), length, custom);
			if (v.getValue() == null) {
				return new IsoValue<byte[]>(type, _v, length, null);
			}
			return v;
		}
	}

}
