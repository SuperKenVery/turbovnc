/*
 * Copyright (c) 2015-2018 ymnk, JCraft,Inc. All rights reserved.
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

abstract class DHECN extends KeyExchange {

  private static final int SSH_MSG_KEX_ECDH_INIT = 30;
  private static final int SSH_MSG_KEX_ECDH_REPLY = 31;
  private int state;

  byte[] Q_C;

  byte[] V_S;
  byte[] V_C;
  byte[] I_S;
  byte[] I_C;

  byte[] e;

  private Buffer buf;
  private Packet packet;

  private ECDH ecdh;

  protected String sha_name;
  protected int key_size;

  @Override
  public void init(Session session, byte[] V_S, byte[] V_C, byte[] I_S, byte[] I_C)
      throws Exception {
    this.V_S = V_S;
    this.V_C = V_C;
    this.I_S = I_S;
    this.I_C = I_C;

    try {
      Class<? extends HASH> c = Class.forName(session.getConfig(sha_name)).asSubclass(HASH.class);
      sha = c.getDeclaredConstructor().newInstance();
      sha.init();
    } catch (Exception e) {
      throw new JSchException(e.toString(), e);
    }

    buf = new Buffer();
    packet = new Packet(buf);

    packet.reset();
    buf.putByte((byte) SSH_MSG_KEX_ECDH_INIT);

    try {
      Class<? extends ECDH> c =
          Class.forName(session.getConfig("ecdh-sha2-nistp")).asSubclass(ECDH.class);
      ecdh = c.getDeclaredConstructor().newInstance();
      ecdh.init(key_size);

      Q_C = ecdh.getQ();
      buf.putString(Q_C);
    } catch (Exception e) {
      throw new JSchException(e.toString(), e);
    }

    if (V_S == null) { // This is a really ugly hack for Session.checkKexes ;-(
      return;
    }

    session.write(packet);

    if (session.getLogger().isEnabled(Logger.INFO)) {
      session.getLogger().log(Logger.INFO, "SSH_MSG_KEX_ECDH_INIT sent");
      session.getLogger().log(Logger.INFO, "expecting SSH_MSG_KEX_ECDH_REPLY");
    }

    state = SSH_MSG_KEX_ECDH_REPLY;
  }

  @Override
  public boolean next(Buffer _buf) throws Exception {
    int i, j;
    switch (state) {
      case SSH_MSG_KEX_ECDH_REPLY:
        // The server responds with:
        //    byte     SSH_MSG_KEX_ECDH_REPLY
        //    string   K_S, server's public host key
        //    string   Q_S, server's ephemeral public key octet string
        //    string   the signature on the exchange hash
        j = _buf.getInt();
        j = _buf.getByte();
        j = _buf.getByte();
        if (j != SSH_MSG_KEX_ECDH_REPLY) {
          if (session.getLogger().isEnabled(Logger.ERROR)) {
            session.getLogger().log(Logger.ERROR, "type: must be SSH_MSG_KEX_ECDH_REPLY " + j);
          }
          return false;
        }

        K_S = _buf.getString();

        byte[] Q_S = _buf.getString();

        byte[][] r_s = KeyPairECDSA.fromPoint(Q_S);

        // RFC 5656,
        // 4.  ECDH Key Exchange
        //    All elliptic curve public keys MUST be validated after they are
        //    received.  An example of a validation algorithm can be found in
        //    Section 3.2.2 of [SEC1].  If a key fails validation, the key exchange
        //    MUST fail.
        if (!ecdh.validate(r_s[0], r_s[1])) {
          return false;
        }

        K = encodeAsMPInt(normalize(ecdh.getSecret(r_s[0], r_s[1])));

        byte[] sig_of_H = _buf.getString();

        // The hash H is computed as the HASH hash of the concatenation of the
        // following:
        //    string   V_C, client's identification string (CR and LF excluded)
        //    string   V_S, server's identification string (CR and LF excluded)
        //    string   I_C, payload of the client's SSH_MSG_KEXINIT
        //    string   I_S, payload of the server's SSH_MSG_KEXINIT
        //    string   K_S, server's public host key
        //    string   Q_C, client's ephemeral public key octet string
        //    string   Q_S, server's ephemeral public key octet string
        //    mpint    K,   shared secret
        // This value is called the exchange hash, and it is used to
        // authenticate the key exchange.
        buf.reset();
        buf.putString(V_C);
        buf.putString(V_S);
        buf.putString(I_C);
        buf.putString(I_S);
        buf.putString(K_S);
        buf.putString(Q_C);
        buf.putString(Q_S);
        byte[] foo = new byte[buf.getLength()];
        buf.getByte(foo);

        sha.update(foo, 0, foo.length);
        sha.update(K, 0, K.length);
        H = sha.digest();

        i = 0;
        j = 0;
        j = ((K_S[i++] << 24) & 0xff000000) | ((K_S[i++] << 16) & 0x00ff0000)
            | ((K_S[i++] << 8) & 0x0000ff00) | ((K_S[i++]) & 0x000000ff);
        String alg = Util.byte2str(K_S, i, j);
        i += j;

        boolean result = verify(alg, K_S, i, sig_of_H);

        state = STATE_END;
        return result;
    }
    return false;
  }

  @Override
  public int getState() {
    return state;
  }
}
