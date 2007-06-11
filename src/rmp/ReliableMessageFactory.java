/*
 *  Copyright (c) 2006 Sun Microsystems, Inc.  All rights
 *  reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the
 *  distribution.
 *
 *  3. The end-user documentation included with the redistribution,
 *  if any, must include the following acknowledgment:
 *  "This product includes software developed by the
 *  Sun Microsystems, Inc. for Project JXTA."
 *  Alternately, this acknowledgment may appear in the software itself,
 *  if and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA" must
 *  not be used to endorse or promote products derived from this
 *  software without prior written permission. For written
 *  permission, please contact Project JXTA at http://www.jxta.org.
 *
 *  5. Products derived from this software may not be called "JXTA",
 *  nor may "JXTA" appear in their name, without prior written
 *  permission of Sun.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED.  IN NO EVENT SHALL SUN MICROSYSTEMS OR
 *  ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 *  USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 *  OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 *  SUCH DAMAGE.
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many
 *  individuals on behalf of Project JXTA.  For more
 *  information on Project JXTA, please see
 *  <http://www.jxta.org/>.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation.
 *
 *  $Id: ReliableMessageFactory.java,v 1.7 2006/09/07 22:12:25 dimosp Exp $
 */



package rmp;

import net.jxta.endpoint.StringMessageElement;
import net.jxta.endpoint.MessageElement;
import net.jxta.endpoint.Message;

public class ReliableMessageFactory {

    // Message element names.
    final static String MESSAGETYPE     = "MessageType";         // See list of types below.
    final static String SENDERNAME      = "SenderName";  // The sending peer name.
    final static String SENDERID        = "SenderID";            // Peer ID of the sender.
    final static String CHANNEL         = "ChannelID";
    final static String MESSAGE_SEQ_NUM = "MsgSeqNum";  // Message Sequence Number [current or requested]
    final static String MULTICASTPATH   = "McastPath";    // The path from sender to current peer
    final static String DATA            = "Data";    // One block of file data.
    final static String SERV_PIPE_ID    = "ServerPipeID";
    final static String DEST_ID         = "DestinationPeerID";
    final static String LEVEL           = "McastTreeLevel";

    // MESSAGETYPE element values.
    final static String MSG_BULK           = "Bulk Data Message";
    final static String MSG_JOIN_REQ       = "Join Channel Request Message";
    final static String MSG_JOIN_RESP      = "Join Channel Respond Message";
    final static String MSG_NACK           = "NACK Message";
    final static String MSG_RETR           = "Message Retransmission";      // Request a message from parent peer.
    final static String MSG_DISCONNECT     = "Disconnect Message";  //sent to a peer's children in order to get attached under another peer
    final static String MSG_HEARTBEAT      = "Heartbeat Message";
    final static String MSG_TREELEVEL      = "Tree Level Update Message"; //propagated from the root to every node of the channel
    final static String MSG_DELIVER2ALL    = "Single Peer Data Message";
    final static String MSG_ELECTLEADER    = "Leader Election Message";
    final static String MSG_CLOSE_CHANNEL  = "Channel Closure Message"; //end of transmission

    public static final String  NAMESPACE = "RMS";
    public ReliableMessageFactory() {}

    public static Message createQueryMessage(String snd_nmame, String snd_id, String ch_id) {
        Message msg = new Message();
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGETYPE, MSG_JOIN_REQ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERNAME , snd_nmame   ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERID   , snd_id      ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(CHANNEL    , ch_id       ,  null) );
        return (msg);
    }

    public static Message createResponseMessage(String snd_name, String snd_id, String ch_id, String serv_pipe_id, int tree_level) {
        Message msg = new Message();
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGETYPE , MSG_JOIN_RESP,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERNAME  , snd_name    ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERID    , snd_id       ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(CHANNEL     , ch_id        ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SERV_PIPE_ID, serv_pipe_id ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(LEVEL, String.valueOf(tree_level) ,  null));
        return (msg);

    }

    public static Message createBulkDataMessage(String snd_name, String snd_id, String ch_id, int seq, String data) {
        String str_seq = String.valueOf(seq);
        Message msg = new Message();
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGETYPE     , MSG_BULK     , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERNAME      , snd_name     , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERID        , snd_id       , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(CHANNEL         , ch_id        , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGE_SEQ_NUM , str_seq      ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MULTICASTPATH   , snd_name     , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(DATA            , data         ,  null) );
        return (msg);
    }

    public static Message createNACKMessage(String snd_name, String snd_id, String ch_id, int lostseqnum) {
        Message msg = new Message();
        String str = String.valueOf(lostseqnum);
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGETYPE , MSG_NACK    ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERNAME  , snd_name    ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERID    , snd_id      ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(CHANNEL     , ch_id       ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGE_SEQ_NUM, str      ,  null) );
        return (msg);
    }

    public static Message createRetransmissionMessage(String snd_name, String snd_id, String ch_id, int seq, String data, String dest) {
        String str_seq = String.valueOf(seq);
        Message msg = new Message();
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGETYPE     , MSG_RETR     , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERNAME      , snd_name     , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERID        , snd_id       , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(CHANNEL         , ch_id        , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGE_SEQ_NUM , str_seq      ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(DATA            , data         ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(DEST_ID         , dest         ,  null) );
        return (msg);
    }

    public static Message createChannelClosureMessage(String chann_id) {
        Message msg = new Message();
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGETYPE     , MSG_CLOSE_CHANNEL , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(CHANNEL         , chann_id          , null) );
        return (msg);
    }

    public static Message createDisconnectMessage(String snd_name, String snd_id, String chann_id) {
        Message msg = new Message();
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGETYPE     , MSG_DISCONNECT , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERNAME      , snd_name     , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERID        , snd_id       , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(CHANNEL         , chann_id          , null) );
        return (msg);
    }

    public static Message createHeartbeatMessage(String snd_name, String snd_id, String chann_id) {
        Message msg = new Message();
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGETYPE     , MSG_HEARTBEAT , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERNAME      , snd_name     , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERID        , snd_id       , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(CHANNEL         , chann_id          , null) );
        return (msg);
    }

    public static Message createTreeLevelMessage(String snd_name, String snd_id, String chann_id, int level) {
        Message msg = new Message();
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGETYPE     , MSG_TREELEVEL , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERNAME      , snd_name     , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERID        , snd_id       , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(CHANNEL         , chann_id          , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(LEVEL           , Integer.toString(level)          , null) );
        return (msg);
    }

    public static Message createDeliverr2AllMessage(String snd_name, String snd_id, String ch_id, int seq, String data) {
        String str_seq = String.valueOf(seq);
        Message msg = new Message();
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGETYPE     , MSG_DELIVER2ALL     , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERNAME      , snd_name     , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(SENDERID        , snd_id       , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(CHANNEL         , ch_id        , null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(MESSAGE_SEQ_NUM , str_seq      ,  null) );
        msg.addMessageElement(NAMESPACE,  new StringMessageElement(DATA            , data         ,  null) );
        return (msg);
    }




    public static String getSenderNameElement(Message msg) {
        MessageElement msgElement = null;
        msgElement = msg.getMessageElement(NAMESPACE, SENDERNAME);
        String str = (msgElement == null)? "unknown" : msgElement.toString().trim();
        return str;
    }

    public static String getSenderIdElement(Message msg) {
        MessageElement msgElement = null;
        msgElement = msg.getMessageElement(NAMESPACE, SENDERID);
        String str = (msgElement == null)? "unknown" : msgElement.toString().trim();
        return str;
    }

    public static String getDestinationElement(Message msg) {
        MessageElement msgElement = null;
        msgElement = msg.getMessageElement(NAMESPACE, DEST_ID);
        String str = (msgElement == null)? "unknown" : msgElement.toString().trim();
        return str;
    }


    public static String getChannelIdElement(Message msg) {
        MessageElement msgElement = null;
        msgElement = msg.getMessageElement(NAMESPACE, CHANNEL);
        String str = (msgElement == null)? "unknown" : msgElement.toString().trim();
        return str;
    }

    public static String getMsgSeqNumElement(Message msg) {
        MessageElement msgElement = null;
        msgElement = msg.getMessageElement(NAMESPACE, MESSAGE_SEQ_NUM);
        String str = (msgElement == null)? "unknown" : msgElement.toString().trim();
        return str;
    }

    public static String getMcastPathElement(Message msg) {
        MessageElement msgElement = null;
        msgElement = msg.getMessageElement(NAMESPACE, MULTICASTPATH);
        String str = (msgElement == null)? "unknown" : msgElement.toString().trim();
        return str;
    }

    public static String getDataElement(Message msg) {
        MessageElement msgElement = null;
        msgElement = msg.getMessageElement(NAMESPACE, DATA);
        String str = (msgElement == null)? "corrupted data" : msgElement.toString().trim();
        return str;
    }

    private static String getServerPipeID(Message msg) {
        MessageElement msgElement = null;
        msgElement = msg.getMessageElement(NAMESPACE, SERV_PIPE_ID);
        String str = (msgElement == null)? "unknown" : msgElement.toString().trim();
        return str;
    }

    public static int getTreeLevelElement(Message msg) {
        MessageElement msgElement = null;
        msgElement = msg.getMessageElement(NAMESPACE, LEVEL);
        String str = (msgElement == null)? "corrupted data" : msgElement.toString().trim();
        int retn = -1;
        if(str.compareTo("corrupted data") != 0)
            retn = Integer.parseInt(str);
        return retn;
    }


}
