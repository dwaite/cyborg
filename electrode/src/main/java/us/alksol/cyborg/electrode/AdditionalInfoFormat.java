package us.alksol.cyborg.electrode;

/**
 * Represents the format of additional information, as well as differentiation of minor types.
 * 
 * The initial byte contains not just a major type in its high order bits, but additional information in the
 * low order bits. This may be an immediate integer or special value, or may indicate the real additional 
 * information follows in the next several bytes.
 * 
 * * For integer and tag types, this value lets us know if the integer was encoded canonically.
 * * For container types (binary/text strings, arrays and maps) this also lets us know if the container
 *   represents an indefinite number of items.
 * * For {@link Major#ETC}, this helps us differentiate from the various possible items represented - simple
 *   values, the various floating point number formats, and the `break` signal
 */
public enum AdditionalInfoFormat {
	/** The integer values 0 through 23 can be encoded as immediate values within the initial byte itself. */
	IMMEDIATE(0, 0),
	
	/** The value is encoded in a single byte following the initial byte */
	BYTE(24, 1),
	
	/** The value is encoded in two bytes following the initial byte, in network byte order (big-endian). */
	SHORT(25, 2),
	
	/** The value is encoded in four bytes following the initial byte, in network byte order (big-endian). */
	INT(26, 4),
	
	/** The value is encoded in eight bytes following the initial byte, in network byte order (big-endian). */
	LONG(27, 8),
	
	/** For container types, this indicates an indeterminate number of contained data items. For 
	 * {@link Major#ETC}, this indicates the `break` signal terminating such an indeterminate container. */
	INDEFINITE(31, 0);
	
	private final int lowOrderBits;
	private final int additionalInfoBytes;
	
	private AdditionalInfoFormat(int lowOrderBits, int additionalInfoBytes) {
		this.lowOrderBits = lowOrderBits;
		this.additionalInfoBytes = additionalInfoBytes;
	}
	
	/** mask the low order bits representing the `InfoFormat` and any immediate value off from the initial byte */
	public static int maskBits(int initialByte) {
		return initialByte & 0x1f;
	}
	
	/** return the low order bits of the initial byte represented by this `InfoFormat` type. All immediate values are
	 * represented by the return value of `0`
	 */
	public int getLowOrderBits() {
		return lowOrderBits;
	}
	
	public int getAdditionalInfoBytes() {
		return additionalInfoBytes;
	}
	/** extract an InfoFormat from the initial byte.
	 * 
	 * @param initialByte initial byte of the CBOR data item
	 * @return InfoFormat, or null if the InfoFormat would be one of the three reserved values (28, 29, and 30)
	 */
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
				return INDEFINITE;
			default:
				return null;
			}
		}
	}
	
	/** 
	 * Determine the appropriate canonical (smallest) encoding of a given long value. 
	 * 
	 * @param value integer value to encode.	Note that while the long primitive type in Java is signed, this 
	 * long is interpreted as an unsigned long - all negative values are larger than {@link Long#MAX_VALUE}
	 * @return InfoFormat which holds the value in the canonical/smallest stream size
	 */
	public static AdditionalInfoFormat canonicalFromLongValue(long value) {
		if (value < 0) {
			return LONG;
		}
		if (value < 24) {
			return IMMEDIATE;
		}
		if (value < 0x100) {
			return BYTE;
		}
		if (value < 0x10000) {
			return SHORT;
		}
		if (value < 0x100000000L)  {
			 return INT;
		}
		return LONG;
	}
}