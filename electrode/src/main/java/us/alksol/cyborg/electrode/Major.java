package us.alksol.cyborg.electrode;

/** 
 * Represents the major type of a data item in CBOR. 
 * 
 * @see [CBOR Major Data Types](https://tools.ietf.org/html/rfc7049#section-2.1) 
 */
public enum Major {
	/** major 0, unsigned integers from 0 to 2^^64-1. */
	INTEGER(false, false, false),
	
	/** major 1, negative integers from -1 to -2^^64. */
	NEGATIVE_INTEGER(false, false, false),
	
	/** major 2, a binary sequence. The value of the data major represents the byte length of the sequence */
	BYTE_STRING(true, true, true),
	
	/** major 3, a text string. The value of the data major represents the byte length of the string, 
	 * represented in UTF-8. */
	TEXT_STRING(true, true, true),
	
	/** major 4, an array of data items. The value of the data major represents the number of child items in 
	 * this array. */
	ARRAY(true, true, false),
	
	/** major 5, a map of data items. The value of the data major represents the number of key value pairs in 
	 * this map, where each key and value are allowed to be arbitrary CBOR data items. This is effectively 
	 * doubled to represent the number of child data items.
	 */
	MAP(true, true, false),
	
	/** major 6, a semantic tag for a data item. The value of the data major represents semantic interpretation
	 * of the single child data item.
	 */
	TAG(false, false, false),
	
	/** 
	 * major 7, shared between:
	 * 
	 * - simple values
	 * - floating point numbers
	 * - 'break' for terminating indeterminate length sequences, arrays and maps.
	 * 
	 * Simple values represent non-integer data values that require no additional content:
	 * 
	 * - boolean values
	 * - the concept of `null` or `nil`
	 * - undefined value
	 * 
	 * Additional simple types may be added in the future by standards and registered with the IANA
	 * 
	 * Floating point number formats for float and double are supported as well as half-width floating point, 
	 * which has traditionally been used mostly for graphical and GPU computation work.
	 *
	 * finally, the `break` signal can be represented by this major type. The `break` does not represent data,
	 * but rather the termination of an indefinite length binary / text string, array, or map
	 * 
	 * The @link {@link LogicalType} enumeration can be used to differentiate all of these options.
	 */
	ETC(false, true, false);

	private final boolean isContainer;
	private final boolean isIndeterminateAllowed;
	private final boolean isPossiblyFollowedByBinaryData;

	private Major(boolean isContainer, boolean isIndeterminateAllowed, boolean isPossiblyFollowedByBinaryData) {
		this.isContainer = isContainer;
		this.isIndeterminateAllowed = isIndeterminateAllowed;
		this.isPossiblyFollowedByBinaryData = isPossiblyFollowedByBinaryData;
	}
	
	/**
	 * Determine the appropriate major major based on the content of the initial byte of a data
	 * 
	 * @param initialByte initial byte of the CBOR data item
	 * @return Major type corresponding to initial byte
	 * @throws ArrayIndexOutOfBoundsException initialByte is not an unsigned byte (0x00 - 0xff)
	 */
	public static Major fromInitialByte(int initialByte) {
		// simply rely on the declaration order above
		return values()[initialByte >> 5];
	}

	/** determine the high order bits that this major represents when computing an initial byte */
	public int getHighOrderBits() {
		return ordinal() << 5;
	}
	
	/**
	 * Represents that this is can contain data items (including an indefinite # of items). A tag is not 
	 * considered a container.
	 */
	public boolean isContainer() {
		return this.isContainer;
	}

	/**
	 * represents that this major type allows the lower order bits to indicate the indeterminate value (e.g.
	 * either an indeterminate-length container or the `break` signal
	 */
	public boolean isIndefiniteAllowed() {
		return isIndeterminateAllowed;
	}

	/**
	 * represents whether a binary stream is always followed by another data type, 
	 * or may be followed by binary data.
	 * @return
	 */
	public boolean isPossiblyFollowedByBinaryData() {
		return isPossiblyFollowedByBinaryData;
	}
}