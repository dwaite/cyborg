package us.alksol.cyborg.implant;

import java.util.Arrays;

import us.alksol.cyborg.implant.DataType.Major;

public class CborIncorrectMajorTypeException extends CborException {
	private static final long serialVersionUID = 1L;

	private final Major[] expected;
	private final DataType header;

	public CborIncorrectMajorTypeException(DataType header, Major expected) {
		super("Received header " + header + ", expected major type " + expected);
		this.header = header;
		this.expected = new Major[] {expected};
	}

	public CborIncorrectMajorTypeException(DataType header, Major... expected) {
		super("Received header " + header + ", expected one of major types " + Arrays.toString(expected));
		this.header = header;
		this.expected = Arrays.copyOf(expected, expected.length);
	}

	public Major[] getExpected() {
		return Arrays.copyOf(expected, expected.length);
	}

	public Major getActual() {
		return header.getMajorType();
	}
	
	public DataType getHeader() {
		return header;
	}

}
