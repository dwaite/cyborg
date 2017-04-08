package us.alksol.cyborg.electrode;

import java.util.Arrays;

public class CborIncorrectMajorTypeException extends CborException {
	private static final long serialVersionUID = 1L;

	private final Major[] expected;
	private final InitialByte header;

	public CborIncorrectMajorTypeException(InitialByte header, Major expected) {
		super("Received header " + header + ", expected major type " + expected);
		this.header = header;
		this.expected = new Major[] {expected};
	}

	public CborIncorrectMajorTypeException(InitialByte header, Major... expected) {
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
	
	public InitialByte getHeader() {
		return header;
	}

}
