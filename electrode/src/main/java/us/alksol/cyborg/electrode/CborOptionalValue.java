package us.alksol.cyborg.electrode;

import java.math.BigInteger;
import java.util.Optional;

import us.alksol.bytestring.Bytes;

public interface CborOptionalValue {
	// integer, negative integer, tag, simple types
	Optional<Integer> intValue() throws ArithmeticException, CborException;
	Optional<Long> longValue() throws ArithmeticException, CborException;
	Optional<BigInteger> bigIntegerValue() throws CborException;
	
	// array, map, chunk, tag types. 
	// count returns -1 for invariant. always returns 1 for tags
	boolean isIndefiniteLengthContainer();
	Optional<Integer> count() throws CborException;
	
	// half float / binary16
	Optional<Short> halfFloatValue() throws CborException;
			
	Optional<Float> floatValue() throws CborException;
	
	Optional<Double> doubleValue() throws CborException;
	
	// true and false
	Optional<Boolean> booleanValue() throws CborException;
	
	// all types, true for null
	boolean isNull();

	// all types, true for undefined
	boolean isUndefined();

	// binary and text chunks
	Optional<Bytes> bytes();
	
	// text chunks (UTF-8 of bytes())
	Optional<String> asTextValue();
	
	// all types, true for break
	boolean isBreak();

	// all types, true for breaks not synthesized to end fixed-length arrays and maps
	boolean isLiteralBreak();
}