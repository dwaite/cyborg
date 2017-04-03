/**
 * <strong>Implant</strong> represents a low level interface for [CBOR (RFC 7049)](https://tools.ietf.org/html/rfc70490).
 * 
 * CBOR defines a <em>data item</em>, a single piece of CBOR data. However, this contains both metadata on the item
 * as well as any child items. For the purpose of this library we define a <em>data type</em>
 * ({@link us.alksol.cyborg.implant.DataType}). A data type represents the type and format of an item, but not any 
 * contained items such as binary data or child data items.
 */
package us.alksol.cyborg.implant;