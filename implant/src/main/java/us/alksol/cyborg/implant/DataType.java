package us.alksol.cyborg.implant;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
// FIXME NOTE initial byte invalid values:
// xxx11100 reserved -8
// xxx11101 reserved -8
// xxx11110 reserved -8
// 00011111 illegal  -1
// 00111111 illegal  -1
// 11011111 illegal  -1
// 1110xxxx reserved -16 
// 111100xx reserved -4
// = 47
// 0x1c-1f, 0x3c-3f, 0x5c-5e, 0x7c-7e, 0x9c-9e, 0Xbc-be, 0xdc-df, 
// 0xe0-0xef, 0xf0-0xf3, 0xfc-0xfe

/** Represents the major of a data item in CBOR.
 * 
 *  A Data item is a single piece of CBOR data. Every data item has
 *  at minimum a major major and some additional information representing either 
 *  the minor major, the actual data, or how the following binary data is 
 *  associated with the data item.
 *  
 *  This class represents the major and additional information of the data item.
 *  Some data items, like integers, are fully contained within the data major 
 *  information. Others, such as arrays, will use the value in the data major to
 *  determine how to perform additional data acquisition.
 *  
 *  The value of a data item is represented by a long, even when it may
 *  indicate some simple major value (such as the boolean value true) or when 
 *  that long is actually a bitwise representation of a double.
 */
public final class DataType {
	// legal values are always non-negative. The special code for indeterminate length values
	// and for the terminating break is represented as this negative value
	public final static int INDETERMINATE = -1;
	
	// represent the major types of CBOR data items
	public enum Major {
		// major major 0, unsigned integers from 0 to 2^^64-1. Due to Java not having an
		// unsigned long major, values over 2^^63-1 are not supported.
		UNSIGNED_INT,
		// major major 1, negative integers from -1 to -2^^64. To represent this as a long in
		// Java, the value is capped to not go below -2^^63.
		NEGATIVE_INT,
		// major major 2, a binary sequence. The value of the data major represents the byte 
		// length of the sequence
		BYTE_STRING,
		// major major 3, a text string. The value of the data major represents the byte 
		// length of the string, represented in UTF-8.
		TEXT_STRING,
		// major major 4, an array of data items. The value of the data major represents the 
		// number of child items in this array.
		ARRAY,
		// major major 5, a map of data items. The value of the data major represents the 
		// number of key value pairs in this map, each of which are allowed to be arbitrary
		// binary CBOR. This is effectively doubled to represent the number of child data items.
		MAP,
		// major major 6, a semantic tag for a data item. The value of the data major represents
		// semantic interpretation of the single child data item.
		TAG,
		// major major 7, shared between simple values, floating point numbers, and the 'break'
		// for terminating indeterminate length sequences, arrays and maps.
		//
		// simple types represent non-integer data values that require no additional content:
		// - boolean values
		// - the concept of 'null' or 'nil'
		// - undefined value
		// additional simple types may be added in the future by standards and registered with
		// the IANA
		//
		// floating point number formats for float and double are supported as well as 
		// half-width floating point, which has traditionally been used mostly for graphical
		// and gpu computation work.
		//
		// finally, the 'stop' signal does not represent data, but rather the termination of
		// an indefinite length binary / text string, array, or map
		SIMPLE_FLOAT;

		// return the appropriate major major based on the content of the initial byte of a data
		// item
		public static Major fromInitialByte(int initialByte) {
			return values()[initialByte >> 5];
		}

		// return the high order bits that this major major represents within the initial byte
		public int getHighOrderBits() {
			return ordinal() << 5;
		}
	}

	// in addition to the major major, a data major has a value associated with it. This value 
	// may represent up to a 64 bit integer, or in the case of floating point a 64-bit double.
	// for shorter values, the value may be encoded using less bytes or even stored as an
	// immediate value in the lower bits of the data major's initial byte.
	// 
	// Additionally, when the value represents the length of a binary sequence or the number of
	// child elements it may be indeterminate, meaning that the elements instead have a 'stop'
	// data item to indicate termination. This is represented by the 'INDETERMINATE' format here,
	// and a -1 value at the data item level.
	
	// For major types other than 7, the value is expected to be interpreted as an integer and
	// therefore has a 'canonical' minimal length of the encoding in CBOR. This format will
	// capture the encoding of the value within a parsed data item, allowing one to determine
	// whether such an item was received in a 'canonical' format or not.
	public enum AdditionalInfoFormat {
		// immediate - the value can be encoded within the initial byte itself. This is
		// true of values from 0 through 23
		IMMEDIATE(0),
		// the value is encoded in a single byte following the initial byte. 
		BYTE(24),
		// the value is encoded in two bytes following the initial byte. The value is
		// expected to be in network byte order, aka big endian. 
		SHORT(25),
		// the value is encoded in four bytes following the initial byte. The value is
		// expected to be in network byte order, aka big endian. 
		INT(26),
		// the value is encoded in eight bytes following the initial byte. The value is
		// expected to be in network byte order, aka big endian. 
		LONG(27),
		// rather than representing a value, this data major represents the start of
		// an indeterminate length sequence
		INDETERMINATE(31);
		
		private final int lowOrderBits;
		
		private AdditionalInfoFormat(int lowOrderBits) {
			this.lowOrderBits = lowOrderBits;
		}
		public static int maskBits(int initialByte) {
			return initialByte & 0x1f;
		}
		
		public int getLowOrderBits() {
			return lowOrderBits;
		}
		
		public static AdditionalInfoFormat fromInitialByte(int initialByte) {
			final int lowOrderBits = maskBits(initialByte);
			if (lowOrderBits < 24) {
				return IMMEDIATE;
			} else {
				switch(lowOrderBits) {
				case 24:
					return BYTE;
				case 25:
					return SHORT;
				case 26:
					return INT;
				case 27:
					return LONG;
				case 31:
					return INDETERMINATE;
				default:
					throw new IllegalStateException("unknown minor value of " + initialByte + " seen");					
				}
			}
		}
		
		public static AdditionalInfoFormat canonicalFromLongValue(long value) {
			if (value < 24) {
				return IMMEDIATE;
			}
			else if (value < 0x100) {
				return BYTE;
			}
			else if (value < 0x10000) {
				return SHORT;
			}
			 else if (value < 0x100000000L)  {
				 return INT;
			} else {
				return LONG;
			}
		}
	}
	
	private final Major major;
	private final AdditionalInfoFormat format;
	private final long value;

	public DataType(Major type, AdditionalInfoFormat additionalInfoFormat, long value) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(additionalInfoFormat);
		this.major = type;
		this.format = additionalInfoFormat;
		this.value = value;
	}

	public static final DataType FALSE = DataType.immediate(0xf4);
	public static final DataType TRUE  = DataType.immediate(0xf5);
	public static final DataType NULL  = DataType.immediate(0xf6);
	public static final DataType UNDEFINED = DataType.immediate(0xf7);
	public static final DataType BREAK = DataType.immediate(0xff);

	static DataType readDataType(DataInput input) throws IOException {
		int ib = input.readUnsignedByte();
		Major type = Major.fromInitialByte(ib);
		AdditionalInfoFormat format = AdditionalInfoFormat.fromInitialByte(ib);
		long value = readAdditionalInfo(input, format, ib);
		return new DataType(type, format, value);
	}

	// construct a data type solely from an initial byte, if possible
	// fails with IllegalArgumentException if not possible
	public static DataType immediate(int ib) {
		if (ib > 0xff) {
			throw new IllegalArgumentException("ib must be an unsigned byte");
		}
		Major major = Major.fromInitialByte(ib);
		AdditionalInfoFormat format = AdditionalInfoFormat.fromInitialByte(ib);
		switch (format) {
			case IMMEDIATE:
				return new DataType(major, format, AdditionalInfoFormat.maskBits(ib));
			case INDETERMINATE:
				return new DataType(major, format, -1);
			default:
				throw new IllegalArgumentException("ib requires additional following byte(s) for processing");
		}
	}

	public static DataType simpleValue(int value) {
		if (value < 0 || value > 255)
		{
			throw new IllegalArgumentException("value is outside expected ranges");
		}
		if (value >= 24 && value < 32)
		{
			throw new IllegalArgumentException("values between 24 and 32 are not legal");
		}
		AdditionalInfoFormat format = value < 24 ? 
				AdditionalInfoFormat.IMMEDIATE :
				AdditionalInfoFormat.BYTE;
		return new DataType(Major.SIMPLE_FLOAT, format, value);
	}

	static DataType canonicalFromMajorAndLongValue(Major major, long value) {
		Objects.requireNonNull(major, "major");
		if (major == Major.SIMPLE_FLOAT) {
			throw new IllegalArgumentException("major cannot be a simple or float type, as the value is not to be interpreted as an integer");
		}
		AdditionalInfoFormat format;
		if (value == INDETERMINATE) {
			format = AdditionalInfoFormat.INDETERMINATE;
		}
		else if (value < 0) {
			throw new IllegalArgumentException("value must be non-negative or INDETERMINATE");
		} else {
			format = 
					AdditionalInfoFormat.canonicalFromLongValue(value);
		}
		return new DataType(major, format, value);
	}
	
	private static long readAdditionalInfo(DataInput input, AdditionalInfoFormat format, int initialByte) throws IOException {
		long length;
		switch(format) {
		case IMMEDIATE:
			length = AdditionalInfoFormat.maskBits(initialByte);
			break;
		case BYTE:
			length = input.readUnsignedByte();
			break;
		case SHORT:
			length = input.readUnsignedShort();
			break;
		case INT:
			length = ((long)input.readInt()) & 0xffffffffL;
			break;
		case LONG:
			length = input.readLong();
			if (length < 0) {
				throw new IllegalStateException("values over 2^63 - 1 unsupported");
			}
			break;
		case INDETERMINATE:
			length = INDETERMINATE;
			break;
		default:
			throw new IllegalStateException("unknown minor value of " + AdditionalInfoFormat.maskBits(initialByte) + " seen");					
		}
		return length;
	}

	public Major getMajorType() {
		return major;
	}
	
	public AdditionalInfoFormat getAdditionalInfo() {
		return format;
	}

	public long getValue() {
		return value;
	}
	
	public float getValueAsFloat() {
		return Float.intBitsToFloat((int)value);
	}
	
	public double getValueAsDouble() {
		return Double.longBitsToDouble(value);
	}
	
	public boolean getValueAsBoolean() {
		return value == 21;
	}
	
	public void write(DataOutput output) throws IOException {
		if (format == AdditionalInfoFormat.IMMEDIATE) {
			output.writeByte(major.getHighOrderBits() | (int)value);
		} else {
			output.writeByte(major.getHighOrderBits() | format.getLowOrderBits());
			switch (format) {
			case BYTE:
				output.writeByte((int)value);
				break;
			case SHORT:
				output.writeShort((int)value);
				break;
			case INT:
				output.writeInt((int)value);
				break;
			case LONG:
				output.writeLong(value);
				break;
			default:
				throw new IllegalStateException(format.name());
			}
		}
	}

	public static DataType longValue(long v) {
		if (v >= 0) {
			return canonicalFromMajorAndLongValue(Major.UNSIGNED_INT, v);
		}
		else {
			return canonicalFromMajorAndLongValue(Major.NEGATIVE_INT, ~v);
		}
	}
	public static DataType integerValue(int v) {
		return longValue(v);
	}

	public static DataType floatValue(float v) {
		int f = Float.floatToIntBits(v);
		return new DataType(Major.SIMPLE_FLOAT, AdditionalInfoFormat.INT, f);
	}

	public static DataType doubleValue(double v) {
		long d = Double.doubleToLongBits(v);
		return new DataType(Major.SIMPLE_FLOAT, AdditionalInfoFormat.LONG, d);
	}

	public static DataType halfFloatValue(int hf) {
		return new DataType(Major.SIMPLE_FLOAT, AdditionalInfoFormat.SHORT, hf);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((format == null) ? 0 : format.hashCode());
		result = prime * result + ((major == null) ? 0 : major.hashCode());
		result = prime * result + (int) (value ^ (value >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DataType other = (DataType) obj;
		if (format != other.format)
			return false;
		if (major != other.major)
			return false;
		if (value != other.value)
			return false;
		return true;
	}
	
	private static String bytesToHex(byte[] in) {
	    final StringBuilder builder = new StringBuilder();
	    for(byte b : in) {
	        builder.append(String.format("%02x", b));
	    }
	    return builder.toString();
	}
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("[DataType ");
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream(5);
			DataOutputStream dos = new DataOutputStream(baos)) {
			this.write(dos);
			dos.close();
			builder.append(bytesToHex(baos.toByteArray()));
		} catch (IOException e) {
			builder.append("exception");
		}
		builder.append(" ").append(major).append(":").append(format).append(" ");
		switch (major) {
		case UNSIGNED_INT:
			builder.append(value);
			break;
		case NEGATIVE_INT:
			builder.append(~value);
			break;
		case BYTE_STRING:
		case TEXT_STRING:
		case ARRAY:
		case MAP:
			builder.append("...");
			break;
		case TAG:
			builder.append("tag#").append(value);
			break;
		case SIMPLE_FLOAT:
			switch (format) {
			case IMMEDIATE:
			case BYTE:
			case INDETERMINATE:
				switch((int)value) {
				case -1:
					builder.append("BREAK");
					break;
				case 20:
					builder.append("FALSE");
					break;
				case 21:
					builder.append("TRUE");
					break;
				case 22:
					builder.append("NULL");
					break;
				case 23:
					builder.append("UNDEFINED");
					break;
				default:
					builder.append("simple(").append(value).append(")");
				}
			case SHORT:
				short half = (short) value;
				builder.append("binary16(").append(String.format("%4x", half)).append(")");
				break;
			case INT:
				builder.append("float ").append(Float.intBitsToFloat((int)value));
				break;
			case LONG:
				builder.append("double ").append(Double.longBitsToDouble(value));
				break;				
			}
		}
		builder.append("]");
		return builder.toString();
	}
}