/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.nmoncho.helenus.internal.codec

import com.datastax.oss.driver.api.core.ProtocolVersion

import java.nio.ByteBuffer

package object collection {

  // TODO check if this could be more Scala-y
  // TODO check if we could skip the second iteration
  // TODO `elements` seems unnecessary given that we've an array
  def pack(buffers: Array[ByteBuffer], elements: Int, version: ProtocolVersion): ByteBuffer = {
    var size = 0
    for (idx <- buffers.indices) {
      size += (if (buffers(idx) == null) 4 else 4 + buffers(idx).remaining())
    }

    val result = ByteBuffer.allocate(4 + size)
    result.putInt(elements)

    for (value <- buffers) {
      if (value == null) {
        result.putInt(-1)
      } else {
        result.putInt(value.remaining().toShort)
        result.put(value.duplicate())
      }
    }

    result.flip()
  }

  def readValue(input: ByteBuffer, version: ProtocolVersion): ByteBuffer = {
    val size = input.getInt()

    if (size < 0) null
    else {
      val copy = input.duplicate()
      copy.limit(copy.position() + size)
      input.position(input.position() + size)
      copy
    }
  }
}
