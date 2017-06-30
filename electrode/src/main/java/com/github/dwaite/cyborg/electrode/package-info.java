/**
 *  This package is meant to represent a lower-level interface of working with CBOR
 *  data. An abstraction of <em>Data events</em> is provided to aid in the writing
 *  of reusable CBOR components, in particular Parser and Generator-based components.
 *  
 *  For many applications, this package may be sufficient. Higher level packages are
 *  envisioned to provide DOM and ORM functionalities, built on top of this package.
 *   
 *  A Data event represents the description of a CBOR data item, minus contained data items. This creates
 *  a homogeneous stream of values as a CBOR document is traversed depth-first.
 *  
 *  For data events which do not represent containers (indefinite length/chunked binary or text, arrays, maps, or tags), 
 *  a data event is a complete CBOR data item.
 *  
 *  A data event contains the following parts:
 *  
 *  * The major type ({@link Major}), representing the kind of CBOR data item
 *  * An information format ({@link InfoFormat}) giving details on the format of the value. This is needed
 *    both to fully represent the binary format of the data type, and to provide minor type information in the case
 *    of {@link Major#ETC}.
 *  * The additional info value ({@link DataEvent#getAdditionalInfo()}), containing additional information for the CBOR data
 *    item such as the value of an integer or the length of an array
 *  * Any binary data (bytes or utf-8 text as bytes)
 *                                                
 * ```                                                   
 *                    Data Event                        
 * +---------------------------------------------------------------------+
 * |                                                                     |
 *         Initial Byte                                
 * +-------------------------------+                      
 * |                               |                   + - - - - - - - - 
 *                                   +-------------+   . Bytes...       |
 * +-------------+-----------------+ | Addl. Info  |   + - - - - - - - - 
 * |  Major Type | Info Format     | +-------------+   + - - - - - - - - 
 * |             | Immediate Data  |                   . Child Items... |
 * +-------------+-----------------+                   + - - - - - - - - 
 * 	```
 */
package com.github.dwaite.cyborg.electrode;