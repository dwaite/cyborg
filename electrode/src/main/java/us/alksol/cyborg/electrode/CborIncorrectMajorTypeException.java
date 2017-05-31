package us.alksol.cyborg.electrode;

import java.util.Arrays;

import us.alksol.cyborg.electrode.InitialByte.Major;

public class CborIncorrectMajorTypeException extends CborException {
	private static final long serialVersionUID = 1L;

	private final Major[] expected;
	private final InitialByte ib;

	public CborIncorrectMajorTypeException(InitialByte ib, Major expected) {
		super("Received data item with initial byte " + ib + ", expected major type " + expected);
		this.ib = ib;
		this.expected = new Major[] {expected};
	}

	public CborIncorrectMajorTypeException(InitialByte ib, Major... expected) {
		super("Received data item with initial byte " + ib + ", expected one of major types " + Arrays.toString(expected));
		this.ib = ib;
		this.expected = Arrays.copyOf(expected, expected.length);
	}

	public Major[] getExpected() {
		return Arrays.copyOf(expected, expected.length);
	}

	public Major getActual() {
		return ib.getMajor();
	}
	
	public InitialByte getInitialByte() {
		return ib;
	}

}
