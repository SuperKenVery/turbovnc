/*
 * Copyright (c) 2002-2018 ymnk, JCraft,Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. The names of the authors may not be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL JCRAFT, INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.jcraft.jsch;

class RequestWindowChange extends Request {
  int width_columns = 80;
  int height_rows = 24;
  int width_pixels = 640;
  int height_pixels = 480;

  void setSize(int col, int row, int wp, int hp) {
    this.width_columns = col;
    this.height_rows = row;
    this.width_pixels = wp;
    this.height_pixels = hp;
  }

  @Override
  public void request(Session session, Channel channel) throws Exception {
    super.request(session, channel);

    Buffer buf = new Buffer();
    Packet packet = new Packet(buf);

    // byte     SSH_MSG_CHANNEL_REQUEST
    // uint32   recipient_channel
    // string   "window-change"
    // boolean  FALSE
    // uint32   terminal width, columns
    // uint32   terminal height, rows
    // uint32   terminal width, pixels
    // uint32   terminal height, pixels
    packet.reset();
    buf.putByte((byte) Session.SSH_MSG_CHANNEL_REQUEST);
    buf.putInt(channel.getRecipient());
    buf.putString(Util.str2byte("window-change"));
    buf.putByte((byte) (waitForReply() ? 1 : 0));
    buf.putInt(width_columns);
    buf.putInt(height_rows);
    buf.putInt(width_pixels);
    buf.putInt(height_pixels);
    write(packet);
  }
}
