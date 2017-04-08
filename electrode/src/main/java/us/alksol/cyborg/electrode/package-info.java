/** A Data type represents the description of a CBOR data item, minus binary streams or contained data items. This
 *  might be considered somewhat like the 'header' of a data item, although some data types (such as integers 
 *  and simple types) actually contain all the necessary information to be data items.
 *  
 *  A data type contains the following parts:
 *  
 *  * The major type ({@link Major}), representing the kind of CBOR data item
 *  * The additional info value ({@link InitialByte#getValue()}), containing additional information for the CBOR data
 *    item, such as the value of an integer or the length of an array
 *  * An information format ({@link AdditionalInfoFormat}) giving details on the format of the value. This is needed
 *    both to fully represent the binary format of the data type, and to provide minor type information in the case
 *    of {@link Major#ETC}.
 *  
 *                                                
 * ```                                                   
 *                    Data Item                        
 * +--------------------------------------------------+
 * |                                                  |
 *            Data Type                                
 * +----------------------------+                      
 * |                            |    + - - - - - - - - 
 *                                     Bytes...       |
 * +-------------+--------------+    + - - - - - - - - 
 * |  Major Type | Addl. Info + |    + - - - - - - - - 
 * |             | Info Format  |      Child Items... |
 * +-------------+--------------+    + - - - - - - - - 
 * 	```
 * 
 *  Where the term <em>data item</em> is used in the documentation of this class, it is meant to indicate a data
 *  type which is not a container or tag, thus actually representing a fully formed CBOR data item.
 */
package us.alksol.cyborg.electrode;