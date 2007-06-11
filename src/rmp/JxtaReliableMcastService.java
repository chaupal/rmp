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
 *  $Id: JxtaReliableMcastService.java,v 1.7 2006/09/07 22:12:25 dimosp Exp $
 */


package rmp;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;
import net.jxta.document.AdvertisementFactory;
import net.jxta.document.MimeMediaType;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.document.XMLDocument;
import net.jxta.endpoint.Message;
import net.jxta.endpoint.MessageElement;
import net.jxta.endpoint.Message.ElementIterator;
import net.jxta.endpoint.StringMessageElement;
import net.jxta.peergroup.PeerGroup;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.socket.JxtaMulticastSocket;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import java.net.*;



public class JxtaReliableMcastService implements Runnable {
    private boolean service_is_up;
    private boolean service_is_joined;
    private boolean periodical_req;
    private boolean processing_message;
    private boolean fast_join; //join to the channel on the first response without waiting extra responses for RESP_TIMEOUT_NORMAL | SHORT | LONG

    private PipeAdvertisement pipeAdv;
    private JxtaMulticastSocket mcastSocket;

    private JxtaReliableMcastChannel channel;  //lets start by supporting joining only 1 channel
    private Object channel_responses_lock;
    private Object processing_lock;

    private final static int BUFF_SIZE = 16384;

    public final static int RESP_TIMEOUT_LONG   = 120000; //for LAN use only for testing RESP_TIMEOUT = 4000 is pretty enough
    public final static int RESP_TIMEOUT_NORMAL = 60000;
    public final static int RESP_TIMEOUT_SHORT  = 4000;

    public final static int JOIN_RETRIES_PERSISTENT = 4;
    public final static int JOIN_RETRIES_NONPERSISTENT = 1;

    private final static int MAX_PROCESSING_DURATION = 3000;

    private Thread rms_Thread;
    private Timer timer;
    private TimerTask task;

    private PeerGroup pgrp;
    private final static Logger LOG = Logger.getLogger(JxtaReliableMcastService.class.getName());

    public JxtaReliableMcastService(PeerGroup our_group) throws RMSException {
        //init mcastsocket from private adv that only the peer who run our service has
        pgrp = our_group;
        create_McastSock(our_group);
        channel = null;
        channel_responses_lock = new Object();
        processing_lock = new Object();
        service_is_up = false;
        service_is_joined = false;
        periodical_req = false;
        processing_message = false;
        fast_join = false;
        rms_Thread = null;
    }

    private void create_McastSock(PeerGroup our_group)throws RMSException {
        try {
            FileInputStream is = new FileInputStream("prv_mcastsocket.adv");
            XMLDocument document = (XMLDocument) StructuredDocumentFactory.newStructuredDocument(MimeMediaType.XMLUTF8, is);
            pipeAdv = (PipeAdvertisement) AdvertisementFactory.newAdvertisement(document);
            is.close();
            mcastSocket = new JxtaMulticastSocket(our_group, pipeAdv);
            try {
                mcastSocket.setSoTimeout(0);
            }
            catch (SocketException ex1) {
                ex1.printStackTrace();
            }
        } catch (Exception e) {
            throw new RMSException("Reliable Multicast Service failed to init -- read/parse pipe advertisement failure.");
        }
    }

    public void start_service()throws RMSException {   // Sos : check if mcastSocket is closed and is needed to call create_McastSock() again
        if(!service_is_up) {
            service_is_up = true;
            rms_Thread = new Thread(this);
            rms_Thread.start();
            if (LOG.isEnabledFor(Level.INFO)) {
                LOG.info("RMS has started");
            }
        } else
            throw new RMSException("Reliable Multicast Service already started.");
    }


    public void stop_service()throws RMSException {
        if(service_is_up) {
            if (periodical_req)
                stop_periodical_requests();

            if((channel != null) && (channel.isActive()))
                channel.terminate_channel(null, true);//must be called for every channel
            service_is_joined = false;//again for safety (exists also in clear_channel())
            service_is_up = false;
            mcastSocket.close();
            if (LOG.isEnabledFor(Level.INFO)) {
                LOG.info("RMS has stopped");
            }
            rms_Thread = null;
        } else
            throw new RMSException("Reliable Multicast Service already stopped.");
    }


    public void run() {
        //check if init
        byte buffer[];
        int i = 0;
        boolean from_myself = false;
        DatagramPacket packet;
        Message msg;

        while (service_is_up) {
            buffer = new byte[BUFF_SIZE];
            packet = new DatagramPacket(buffer, buffer.length);

            try {
                mcastSocket.receive(packet);
            }
            catch (IOException e) {
                //System.out.println("---  JxtaMulticastSocket timeout occured");
                e.printStackTrace();
                if (LOG.isEnabledFor(Level.DEBUG)) {
                    LOG.debug("---  JxtaMulticastSocket timeout occured\n");
                }
            }

            //a packet with null Address is generated when the socket is closed
            from_myself = true;
            if (packet.getAddress() != null)
                from_myself = (pgrp.getPeerID().toString().indexOf(packet.getAddress().getHostName()) != -1);

            //process the message if the state of the service is "Joined" and the received message is not sent from "ourself"
            if (service_is_joined && !from_myself) {
                processing_message = true;

                msg = Datagram2Message(packet);
                System.out.println("*** Service Message #" + i++ +" received *** ");
                if (LOG.isEnabledFor(Level.DEBUG)) {
                    LOG.debug("*** Service Message #" + i++ +" received *** \n\n" + Message2String(msg));
                }
                System.out.println(Message2String(msg));
                try {
                    process_message(msg, packet.getAddress());
                }
                catch (RMSException ex) {
                    ex.printStackTrace();
                }

                processing_message = false;
                synchronized(processing_lock)
                {
                    processing_lock.notify();
                }
            }
        } //while
    }

    public JxtaReliableMcastChannel createChannel (String channelID) throws RMSException {
        if(!service_is_up || service_is_joined) {
            throw new RMSException("Reliable Multicast Service has to be started before creating a channel or channel is already created.");
        }

        channel = new JxtaReliableMcastChannel(pgrp, channelID, this);
        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Channel created");
        }

        channel.setRoot(); // it also set the channel as a provider ----> channel.setProvider(true);

        if(channel.prepare_channel())
        {
            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("Channel is ready for use");
            }
            System.err.println("Channel " + channel.getChannelId() + " is ready for use");
        }
        else
        {
            channel = null;
            service_is_joined = false;
            throw new RMSCreateException("Reliable Multicast Service failed to create the \"" + channelID + "\" channel.");
        }

        service_is_joined = true;

        return channel;
    }



    public JxtaReliableMcastChannel joinChannel(String channelID, int timeoutval, int retries, JxtaReliableEventListener listener, boolean fast)throws InterruptedException, RMSException //called by the app
    {
        if(!service_is_up || service_is_joined) {
            throw new RMSException("Reliable Multicast Service has to be started before joining in a channel or already joined.");
        }

        int svcJoinTimeout;
        int svcJoinRetries;

        switch(timeoutval)
        {
            case RESP_TIMEOUT_LONG: case RESP_TIMEOUT_NORMAL: case RESP_TIMEOUT_SHORT: {svcJoinTimeout = timeoutval; break;}
            default: {svcJoinTimeout = RESP_TIMEOUT_NORMAL; break;}
        }

        switch(retries)
        {
            case JOIN_RETRIES_NONPERSISTENT: case JOIN_RETRIES_PERSISTENT: {svcJoinRetries = retries; break;}
            default: {svcJoinRetries = JOIN_RETRIES_NONPERSISTENT; break;}
        }


        channel = new JxtaReliableMcastChannel(pgrp, channelID, this);
        int loops = 0;

        //using fast_join=true results in a quick joining but the peer that we get attached under (the provider that we selected) may not be
        //very close to the root of the channel. It is generally more rational to wait the whole timeout for responses (using fast_join=false).
        fast_join = fast;
        while((channel.getNumberOfProviders() == 0) && (loops < svcJoinRetries)) //the service doesn't stop to cache other peers as well (based on their responses) when this loops breaks
        {
            synchronized (channel_responses_lock) {
                channel_responses_lock.wait(RESP_TIMEOUT_SHORT);
            }
            service_is_joined = true;
            send_multicast_query_for_channel(channel.getChannelId()); //JxtaMulticastSocket is not reliable so send*3 times is ok
            send_multicast_query_for_channel(channel.getChannelId());
            //send_multicast_query_for_channel(channel.getChannelId());
            synchronized (channel_responses_lock) {
                channel_responses_lock.wait(svcJoinTimeout);
            }

            loops++;
        }

        //Sometimes it (rarely) happens the svcJoinTimeout to occur while the rms_Thread is precessing a message that is received from the multicast socket.
        //This message, that is under processing, may be the only one join response to our join request message and so we will
        //not be able to join to the channel if we directly check the responses before the processing is finished.
        //The solution is to wait the processing of the Rreceived message to end BEFORE the main thread checks if there are any responses.
        /*if(processing_message)
        {
            synchronized (processing_lock) {
                processing_lock.wait(MAX_PROCESSING_DURATION);
            }
        }*/

        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("TIMEOUT --No more waiting for incoming response messages to our join requests");
        }

        if (channel.getNumberOfProviders() == 0) // we got no responses at all
        {
            service_is_joined = false;
            channel = null;
            fast_join = false;
            throw new RMSJoinException("Reliable Multicast Service has encoutered some problem upon joining channel \"" + channelID + "\".");
            /*channel.setIsland(true);
            start_periodical_requests();//start timer for periodical request queries as we are an island
            channel.setProvider(true);*/
        }
        else   //we got responses from some nodes of the channel
        {
            channel.setProvider(true);
            channel.addRMSEventListener(listener);

            if (channel.prepare_channel()) {
                if (LOG.isEnabledFor(Level.DEBUG)) {
                    LOG.debug("Joined to channel \"" + channelID + "\".");
                }
                System.err.println("Joined to channel " + channel.getChannelId() + ".");
            }
            else {
                channel.removeEventListener(listener);
                channel = null;
                service_is_joined = false;
                fast_join = false;
                throw new RMSJoinException("Reliable Multicast Service failed to create the \"" + channelID + "\" channel.");
            }
        }
        return channel;
    }

    protected void clear_channel(String theChId)//is only called by the channel (function terminate_channel)
    {
        if( (service_is_up) && (channel.getChannelId().compareTo(theChId) == 0) ) {
            channel = null;
            service_is_joined = false;//SOS : in case of multiple channels here must be CHECKED if we haven't any left channel joined

            if (LOG.isEnabledFor(Level.INFO))
                LOG.info("Channel " + theChId + " has been cleard");
        }
    }

    private void send_multicast_query_for_channel(String channel_id) {
        Message msg = ReliableMessageFactory.createQueryMessage(pgrp.getPeerName(), pgrp.getPeerID().toString(), channel_id);
        DatagramPacket pk = Message2Datagram(msg);
        try {
            mcastSocket.send(pk);
            mcastSocket.send(pk);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void send_unicast_response_for_channel(String dest_name, String dest_id, String channel_id, InetAddress reply_addr) {
        Message msg = ReliableMessageFactory.createResponseMessage(pgrp.getPeerName(), pgrp.getPeerID().toString(), channel_id, channel.get_ServerPipe_ID(), channel.get_tree_level());
        System.err.println("**Sending back Response :\n" + this.Message2String(msg));
        DatagramPacket pk = Message2Datagram(msg);
        pk.setAddress(reply_addr);
        try {
            mcastSocket.send(pk);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * process_message is called only if "service_is_up" and "service_is_joined" are both true
     * @param msg MessageIs the received message by the mcastSocket that heve to be processed
     * @param reply_addr InetAddress
     * @throws RMSException
     */
    private void process_message(Message msg, InetAddress reply_addr)throws RMSException {
        MessageElement elements [] = new MessageElement[6];
        boolean is_Request = false; //else is respond
        String chann_id = "", sender_name = "", sender_id = "", serv_pipe_id="";
        int the_level = -1;

        elements[0] = msg.getMessageElement(ReliableMessageFactory.MESSAGETYPE);
        elements[1] = msg.getMessageElement(ReliableMessageFactory.CHANNEL);
        elements[2] = msg.getMessageElement(ReliableMessageFactory.SENDERNAME);
        elements[3] = msg.getMessageElement(ReliableMessageFactory.SENDERID);


        if( (elements[0]==null) || (elements[1]==null) || (elements[2]==null) ||(elements[3]==null) ) {
            throw new RMSException("Corrupted Message Received [Join Reqest or Respond]");
        }

        chann_id = elements[1].toString().trim();
        sender_name = elements[2].toString().trim();
        sender_id = elements[3].toString().trim();

        if((elements[0].toString().trim().compareTo(ReliableMessageFactory.MSG_JOIN_REQ) == 0)) {
            is_Request = true;
        } else {
            elements[4] = msg.getMessageElement(ReliableMessageFactory.LEVEL);
            elements[5] = msg.getMessageElement(ReliableMessageFactory.SERV_PIPE_ID);
            if( (elements[4]==null)||(elements[5]==null))
            {
                throw new RMSException("Corrupted Message Received [Join Reqest or Respond]");
            }
            the_level =  Integer.parseInt(elements[4].toString().trim());
            serv_pipe_id = elements[5].toString().trim();
        }


        if(is_Request) //the received message is a Join Request
        {
            if ( (channel != null) &&  //not impossible because although the process_message is called only if service_is_joined, it is not executed by the main thread
                                       //The main thread may in the meanwhile set the channel null (e.g. directly after the start of execution of process_message())
                 (channel.isProvider()) && /* we have the privilege to send response as a provider */
                 ((channel.getChannelId().compareTo(chann_id)) == 0) && /* we are provider of the right channel */
                 (channel.isReady()) && /* the channel is ready and initialized [we are connected to our oun provider if we are not the root] */
                 (!channel.is_full()) &&  /* we have place for a new child */
                 (!channel.isReconnecting()))
            {
                send_unicast_response_for_channel(sender_name, sender_id, chann_id, reply_addr);
            }
        } else {   //the received message is a Join Response
            if ((channel != null) &&  //not impossible because although the process_message is called only if service_is_joined, it is not executed by the main thread
                                       //The main thread may in the meanwhile set the channel null (e.g. directly after the start of execution of process_message())
                ((channel.getChannelId().compareTo(chann_id)) == 0) ) {
                   // if the response is for the right channel [even though responds are unicasted]
                   channel.add2cachedProviders(sender_name, sender_id, serv_pipe_id, channel.getChannelId(), the_level);
                   if (fast_join) {
                       synchronized (channel_responses_lock) {
                           channel_responses_lock.notify();
                       }
                   }

                   if (periodical_req)
                       stop_periodical_requests();
                }
            }
    }//process_message()

    private DatagramPacket Message2Datagram(Message msg) {
        DatagramPacket pck = null;
        String str_msg = "";

        str_msg = Message2String(msg);
        pck = new DatagramPacket(str_msg.getBytes(), str_msg.length());
        return pck;
    }

    private String Message2String (Message msg) {
        ElementIterator elements = null;
        MessageElement element = null;
        String str_msg = "";

        elements = msg.getMessageElements();
        while (elements.hasNext()) {
            element = (MessageElement) elements.next();
            str_msg += "<" + element.getElementName() + ">  " + element.toString() + "  </" + element.getElementName() + ">\n";
        }
        return str_msg;
    }

    private Message Datagram2Message(DatagramPacket pck) {
        Message msg = new Message();
        String str_pck = "";
        String []elements = null;
        String element_name = "";
        String element_value = "";
        int j=0, k=0;

        str_pck = new String(pck.getData(), 0, pck.getLength());
        elements = str_pck.split("</[^>]+>");
        for (int i=0; (i < elements.length)&&(elements[i].length() >= 2); i++) {
            j = elements[i].indexOf("<");
            k = elements[i].indexOf(">");
            j = (j == -1) ? 0 : j;
            k = (j == -1) ? 0 : k;
            element_name = elements[i].substring(j + 1, k);
            element_value = elements[i].substring(k + 1);
            msg.addMessageElement(ReliableMessageFactory.NAMESPACE,
                                  new StringMessageElement(element_name, element_value, null));
        }
        return msg;
    }

    protected void start_periodical_requests() {
        periodical_req = true;
        timer = new Timer();
        task = new TimerTask() {
                   public void run() {
                       System.err.println("Periodical iteration for join request.");
                       send_multicast_query_for_channel(channel.getChannelId());
                   }
               };
        timer.schedule(task, 1250, 7500);
        if (LOG.isEnabledFor(Level.INFO)) {
            LOG.info("Periodical iteration for join request. We send every " + 7.5 + " seconds join requests");
        }
    }

    protected void stop_periodical_requests() {
        timer.cancel();
        periodical_req = false;
        if (LOG.isEnabledFor(Level.INFO)) {
            LOG.info("We stopped the periodical join requests.");
        }
    }

}
