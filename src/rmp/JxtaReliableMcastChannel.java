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
 *  $Id: JxtaReliableMcastChannel.java,v 1.7 2006/09/07 22:12:25 dimosp Exp $
 */


package rmp;

import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.log4j.*;
import net.jxta.document.*;
import net.jxta.endpoint.*;
import net.jxta.id.*;
import net.jxta.peer.*;
import net.jxta.peergroup.*;
import net.jxta.pipe.*;
import net.jxta.protocol.*;
import net.jxta.util.*;

/**
 *  Description of the Class
 */
public class JxtaReliableMcastChannel implements PipeMsgListener, Runnable {
    private String channelID;
    private boolean isProvider;
    private boolean isRoot;
    private boolean isParent;
    private boolean channel_is_ready;
    private boolean channel_is_Active;
    private int messageSeq;
    private int localMessageSeq;
    private int tree_level;
    //only from the Root is manipulated

    private PeerID peerID;
    private String peerName;
    private ChannelProvider provider;
    private ChannelProvider provider2;
    //null if we are the root
    private PeerGroup group;
    private PipeService pipeServ;

    ChildrenManager chld_manager;
    private final static int MAX_LISTENERS_NUM = 20;

    private Vector cachedProviders;
    private JxtaBiDiPipe parent_BiDi;
    private final int PARENT_BIDI_CONN_TIMEOUT = 30000;
    private JxtaServerPipe MyServerPipe;
    private PipeAdvertisement pipeAdv;

    private final static int ROOTBUFFSIZE = 1000;
    //only the sender keeps ALL[almost all, because the size is just a big number] the copies of the messages
    private int desired_seq_num;
    private boolean is_first_message;
    private final static int BUFFSIZE = 200;
    private final static int WINDOW_SIZE = 8;
    private int undesirable_msg_arrivals;
    private Vector message_buffer;
    private Vector local_message_buffer;
    //the buffer where the incoming messages [data] from our parent arrive to and stored
    //until they reliably are transmitted to next hop

    protected Vector RMSListeners;
    // Registered JxtaReliableMcastService objects.
    private Thread chann_thread;
    private Object full_of_chld_Lock;
    private Object closure_lock;
    private Object leave_lock;

    //here are the channel's timers
    private Timer HB_timer;
    private TimerTask HB_task;
    private final static int HEARTBEAT_TIMEOUT = 10000; //set the Heartbeat's period to 10 secs
    private int parentDeadCycles; //the number of cycles (1 cycle = 1 heart-beat
    private final static int PARENT_MAX_DEAD_CYCLES = 2;
    private boolean reconnecting;
    private Thread recnn_thread;

    private JxtaReliableMcastService theRMS;

    private final static Logger LOG = Logger.getLogger(JxtaReliableMcastChannel.class.getName());


    /**
     *Constructor for the JxtaReliableMcastChannel object
     *
     * @param  gp     PeerGroup context
     * @param  ch_id  Channel ID
     */
    public JxtaReliableMcastChannel(PeerGroup gp, String ch_id, JxtaReliableMcastService svc) {
        isRoot = false;
        isProvider = false;
        isParent = false;
        channel_is_ready = false;
        channel_is_Active = false;
        is_first_message = true;
        chann_thread = null;
        provider = null;
        messageSeq = 0;
        localMessageSeq = 0;
        undesirable_msg_arrivals = 0;

        chld_manager = new ChildrenManager();
        full_of_chld_Lock = new Object();
        closure_lock = new Object();
        leave_lock = new Object();

        cachedProviders = new Vector();
        message_buffer = new Vector();
        local_message_buffer = new Vector();
        RMSListeners = new Vector(MAX_LISTENERS_NUM);

        group = gp;
        channelID = ch_id;
        peerID = group.getPeerID();
        peerName = group.getPeerName();
        pipeServ = group.getPipeService();

        Timer HB_timer = null;
        TimerTask HB_task = null;
        parentDeadCycles = 0;

        theRMS = svc;

        reconnecting = false;
        recnn_thread = null;
    }


    /*
     *  private void create_ServerPipeAdv()
     *  {
     *  try {
     *  File home = new File("Server_pipe.adv"); //in reality has to be random , not the same adv
     *  FileInputStream is = new FileInputStream(home);
     *  pipeAdv = (PipeAdvertisement) AdvertisementFactory.newAdvertisement(MimeMediaType.XMLUTF8, is);
     *  is.close();
     *  } catch (Exception e) {
     *  System.err.println("failed to bind to the JxtaServerPipe due to the following exception");
     *  e.printStackTrace();
     *  }
     *  }
     */
    /**
     *  Creates a PipeAdvertisement using the providers pipeID
     *
     * @param  provd  the ChannelProvider
     * @return        PipeAdvertisement
     */
    private PipeAdvertisement createPipeAdv(ChannelProvider provd) {
        PipeAdvertisement mypipeAdv = null;
        mypipeAdv = (PipeAdvertisement) AdvertisementFactory.newAdvertisement(PipeAdvertisement.getAdvertisementType());

        if (provd != null) { //create the pipeAdvertisement of the serverPipe of our Provider to pass it as argument to the connect() of our parent_BiDi
            try {
                mypipeAdv.setPipeID(IDFactory.fromURI(new URI(provd.getPipeId())));
            }
            catch (URISyntaxException ex) {
                ex.printStackTrace();
                if (LOG.isEnabledFor(Level.DEBUG))
                    LOG.debug("failed to create the advertisement for the ServerPipe of our provider from pipeID");
            }
        }
        else { //create the pipeAdvertisement of our serverPipe because we are a Provider
            mypipeAdv.setPipeID(IDFactory.newPipeID(group.getPeerGroupID()));
        }

        mypipeAdv.setType(PipeService.UnicastType);
        mypipeAdv.setName(ReliableMessageFactory.NAMESPACE);

        return mypipeAdv;
    }


    /*
     *  if I am a Provider [if(isProvider)] , it means that we haven't received any responses for
     *  the "Join Channel Request Message" that we sent and the vector cachedProviders is empty.
     *  Now we have to create the JxtaServerPipe so that we can accept secure connection requests from our future
     *  children after sending first to them a "Join Channel Respond Message" with our identification and the
     *  JxtaServerPipe advertisement
     *  We are now a Provider = root (local) = repair head, we have to respond to join requests !!!
     *  else
     *  get the first element-object of cachedProviders Vector , and make the peer that is related with this object
     *  our root node [fill with values the vars parent_name parent_id].
     *  After, connect to it using the pipeadvertisement of root's JxtaServerPipe [the adv also
     *  exists in the Vector] and use the parent_BiDi to connect to it. Keep the rest of the objects inside the
     *  cachedProviders Vector for future failover purposes
     */
    protected boolean prepare_channel() {
        boolean success_init = false;

        provider = null;


        //-- First we create the connection with our parent, that is the selected provider from the ones that did sent us back a response
        success_init = init_parental_pipe();
        if(!success_init)
            return false;

        //-- Second we create the JxtaServerPipe so that other peers can get attached under us
        success_init = init_chld_ServerPipe();
        if(!success_init)
            return false;

        //if the execution manages to get here, then everything went fine with the initializations
        channel_is_ready = true;
        channel_is_Active = true;
        if (chann_thread == null) {
            //prepare_channel() maybe called after an Island finds a parent so the thread has already started
            //and changing parent must not affect the procedure of accepting new children to get attached under
            chann_thread = new Thread(this);
            chann_thread.start();
        }

        prepare_timers();

        return true;
    }


    protected boolean init_parental_pipe()
    {
        if (!isRoot) {
            try { //we have received join responses for the channel
                ChannelProvider provd = select_provider(); //select the best peer from them that sent a response
                parent_BiDi = new JxtaBiDiPipe();
                PipeAdvertisement provPipeAdv = createPipeAdv(provd); //create the pipeAdv based on the pipeID of the peer that select_provider() returned
                //create the advertisement from the PipeID of the provider that we are going to connect to
                parent_BiDi.setReliable(true);
                parent_BiDi.connect(group,
                                    null,
                                    provPipeAdv,
                                    PARENT_BIDI_CONN_TIMEOUT,
                                    this);
                if (LOG.isEnabledFor(Level.DEBUG)) {
                    LOG.debug("Trying to connect to our parent through parent_BiDi so that the channel gets prepared-ready.");
                }
                provider = provd;
                tree_level = provider.getLevel() + 1;
            }
            catch (IOException ex) {
                ex.printStackTrace();
                /*try {
                    parent_BiDi.close();
                }
                catch (IOException ex2) {
                    ex2.printStackTrace();
                }*/
                parent_BiDi = null;
                provider = null;
                //the tree level has not to be rolled back to the previous value because if there is thrown an exception
                //it would be before setting the new tree_level
                return false;
            }
        }
        parentDeadCycles = 0;

        if(reconnecting)//if the cotrol reaches here, then the peer is reconnected successfuly
        {
            reconnecting = false;
            recnn_thread = null; //it has already stopped after calling the theRMS.startReconnect() , but just in case
            Message level_msg = ReliableMessageFactory.createTreeLevelMessage(peerName, peerID.toString(), channelID, tree_level);
            if(isParent)
                forward_message_to_children(level_msg);
        }
        return true;
    }


    private boolean init_chld_ServerPipe()
    {
        try {
            if ( (isProvider) && (MyServerPipe == null)) {
                if ( chld_manager.count_children() == 0) /******* MAYBE a more clever check is needed --> foreach : bidi.isBound() ******/
                    isParent = false; //if the peer has already some children attached, then creating a new MyServerPipe should not cause their detachment
                //init_chld_ServerPipe() maybe called after an Island finds a parent so the MyServerPipe has already started to accept connections and maybe already has some attached children
                //Using (MyServerPipe == null) condition we keep the connection with our children.
                //create_ServerPipeAdv();
                pipeAdv = createPipeAdv(null);
                //create the advertisement of our ServerPipe to which we listen for incoming connections
                MyServerPipe = new JxtaServerPipe(group, pipeAdv);
                MyServerPipe.setPipeTimeout(0);
                // we want to block until a connection is established
                if (LOG.isEnabledFor(Level.DEBUG)) {
                    LOG.debug("Our JxtaServerPipe is created");
                }
                if (isRoot)
                    tree_level = 0;
            }
        }
        catch (IOException ex) {
            //SOS : connect again if timeout on connection try at a different provider from cache
            ex.printStackTrace();
            pipeAdv = null;
            if (MyServerPipe != null)
                try {
                    MyServerPipe.close();
                }
                catch (IOException ex1) {
                    //ex1.printStackTrace();
                }
            MyServerPipe = null;
            return false;
        }

        return true;
    }


    private void prepare_timers()
    {
        HB_timer = new Timer();
        HB_task = new TimerTask() {
                   public void run() {
                       doCycleActions();
                   }
               };
        parentDeadCycles = 0;
        HB_timer.schedule(HB_task, 1250, HEARTBEAT_TIMEOUT);
    }


    private void doCycleActions()
    {
        send_heartbeat();
        parentDeadCycles++;
        if ( (!isRoot) && (!reconnecting) && (parentDeadCycles > PARENT_MAX_DEAD_CYCLES)) {
            //If the controls reaches this point, the, the connections with parenthas been lost. Reconnect mechanism must be triggered.and we need to reconnect to the channel
            //However, the thread of the Heartbeat mechanism has to continue and not wait for reconnection because otherwise
            //if it takes long to reconnect, all our heartbeat will be sent to children with a very big delay and they may suppose that are in turnn disconnected
            //The reconnect mechanism has to run in a different/separate thread.
            System.err.println("--##-- PARENT CONNECTION is lost.");
            start_reconnect_mechanism();
        }
        chld_manager.update_hbits();
        Message disconn_msg = ReliableMessageFactory.createDisconnectMessage(peerName, peerID.toString(), channelID);
        boolean was_full = chld_manager.full();
        int totalch = chld_manager.cutDeadChildren(disconn_msg);
        if (totalch == 0) //if after the removal of the dead children there are no more left
            isParent = false;
        if (was_full && !chld_manager.full())
            synchronized (full_of_chld_Lock) {
                full_of_chld_Lock.notify();
            }
    }


    private void send_heartbeat()
    {
        System.err.println("~~^^~~^^ SELF HEARTBEAT ^^~~^^~~");
        Message hbit = ReliableMessageFactory.createHeartbeatMessage(peerName, peerID.toString(), channelID);
        if(isParent)
            forward_message_to_children(hbit);

        if ( (!isRoot) && (!reconnecting) && (parent_BiDi != null) && (parent_BiDi.isBound())) {
            try {
                parent_BiDi.sendMessage(hbit);
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }


    private void start_reconnect_mechanism()
    {
        System.err.println("Starting the reconnect mechanism.");
        reconnecting = true;
        if (!isRoot) //just in case
        {
            try {
                parent_BiDi.close();
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        recnn_thread = new Thread(new ReconnectThread(this));

        recnn_thread.start();
    }


    /**
     *  Main processing method for the JxtaReliableMcastChannel object
     */
    public void run() {
            if (isProvider) {
                //includes the case that we are a root as a root is also a provider
                JxtaBiDiPipe bdp = null;

                while (channel_is_ready) {
                    //update_children_status(); under consideration to find a way to chack if connections are alive
                    if (!chld_manager.full()) {
                        try {
                            bdp = MyServerPipe.accept();
                        }
                        catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        //maybe we add listeners for these bidi pipes also for receiveing NAC from our children
                        if(bdp != null)
                        {
                            chld_manager.store_child(bdp, this);
                            isParent = true;
                        }
                    }
                    else //the peer is full of children
                    {
                        //maybe these else lines are useless
                        synchronized(full_of_chld_Lock)
                        {
                            try {
                                full_of_chld_Lock.wait(chld_manager.CHLD_FULL_TIMEOUT);
                            }
                            catch (InterruptedException ex1) {
                                ex1.printStackTrace();
                            }
                        }
                    }
                }
                //while
            }
            //if provider


        if (LOG.isEnabledFor(Level.INFO)) {
            LOG.info("The thread for accepting BiDi connections of provider " + peerName + " has died");
        }
        //System.err.println("!!!!!!!!!!!!!!!! ## END OF RUN() OF CHANNEL");
    }


    /**
     * Receive from our parent a message and we have to put in the buffer + forward it
     * to our children.
     *
     * @param  event  PipeMsgEvent
     */
    public synchronized void pipeMsgEvent(PipeMsgEvent event) {
        try{
            Message msg = null;
            MessageElement msgElement = null;
            msg = event.getMessage();
            // grab the message from the event
            if (msg == null) {
                return;
            }
            msgElement = msg.getMessageElement(ReliableMessageFactory.NAMESPACE, ReliableMessageFactory.MESSAGETYPE);
            int h = 0;

            h = determine_msg_type(msgElement);
            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("Message received and is of type " + h);
            }
            //System.err.println("Message received [we are the receiver] and type determined" + msg.toString());

            switch (h) {
                case 1: {
                    processDataMessage(msg);
                    break;
                }
                case 2: {
                    processRetransmissionMessage(msg);
                    break;
                }
                case 3: {
                    processClosureMessage(msg, false);
                    break;
                }
                case 4: {
                    processNACKMessage(msg);
                    break;
                }
                case 5: {
                    processHBitMessage(msg);
                    break;
                }
                case 6: {
                    processTreeLevelMessage(msg);
                    break;
                }
                case 7: {
                    processDisconnectMessage(msg);
                    break;
                }
                case 8: {
                    processDeliver2AllMessage(msg);
                    break;
                }



                default: {
                    /*
                     *  log for unreadable message or maybe connection lost etc
                     */
                    break;
                }
            }
        }catch(Exception ee){ee.printStackTrace();}
    }


    /**
     *  Description of the Method
     *
     * @param  msgElement  Description of the Parameter
     * @return             Description of the Return Value
     */
    private int determine_msg_type(MessageElement msgElement) {
        int type = 0;
        if ((msgElement.toString().compareTo(ReliableMessageFactory.MSG_BULK)) == 0) {
            type = 1;
        } else
                if ((msgElement.toString().compareTo(ReliableMessageFactory.MSG_RETR)) == 0) {
            type = 2;
        } else
                if ((msgElement.toString().compareTo(ReliableMessageFactory.MSG_CLOSE_CHANNEL)) == 0) {
            type = 3;
        } else
                if ((msgElement.toString().compareTo(ReliableMessageFactory.MSG_NACK)) == 0) {
            type = 4;
        } else
                if ((msgElement.toString().compareTo(ReliableMessageFactory.MSG_HEARTBEAT)) == 0) {
            type = 5;
        }else
                if ((msgElement.toString().compareTo(ReliableMessageFactory.MSG_TREELEVEL)) == 0) {
            type = 6;
        }else
                if ((msgElement.toString().compareTo(ReliableMessageFactory.MSG_DISCONNECT)) == 0) {
            type = 7;
        }
        else
                if ((msgElement.toString().compareTo(ReliableMessageFactory.MSG_DELIVER2ALL)) == 0) {
            type = 8;
        }

        return type;
    }


    /**
     *  Description of the Method
     *
     * @param  msg  Description of the Parameter
     */
    private void processDataMessage(Message msg) {
        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Start processing the data message");
        }

        //It is a bit funny, but you never know...maybe the parent is considered dead but is not (just BIG delay in HB), if the reconnecting process
        //has begun then sooner or later the existing parent_BiDi will close, and it wouldn't be safe to happen while processing the message
        if(reconnecting)
            return;

        //        System.err.println("Start processing the data message");
        // update the message element MULTICASTPATH
        String path = ReliableMessageFactory.getMcastPathElement(msg) + "-->" + peerName;
        StringMessageElement upd_path = new StringMessageElement(ReliableMessageFactory.MULTICASTPATH, path, null);
        msg.replaceMessageElement(ReliableMessageFactory.NAMESPACE, upd_path);

        //retrieve the message sequence number to decide if we have to generate a receive event
        //[if the message has the desirable_seq_num]
        String seq = ReliableMessageFactory.getMsgSeqNumElement(msg);
        //place it in the right position inside the buffer for the messages "message_buffer"
        store_message(msg, Integer.parseInt(seq.trim()));
        /*
         *  DO THE RELIABILITY STAFF
         */
        int pos = positionOfDesired();
        if (pos == -1) {
            //we still do not have the message with the desired_seq_num

            undesirable_msg_arrivals++;
            if (undesirable_msg_arrivals >= WINDOW_SIZE) {
                //the NACK case

                // send NACK for the msg with sequence number == this.desired_seq_num USING
                //the parent_BiDi but parent has to have added listeners to alla the bidi pipes of its children
                Message nack_msg = ReliableMessageFactory.createNACKMessage(peerName, peerID.toString(), channelID, desired_seq_num);
                if((!isRoot) && (parent_BiDi != null) && (parent_BiDi.isBound())) {
                    try {
                        parent_BiDi.sendMessage(nack_msg);
                    }
                    catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                undesirable_msg_arrivals = 0;
            }
        } else {
            while (pos != -1) {
                //the message with the desired_seq_num is found
                undesirable_msg_arrivals = 0;
                //generate the JxtaReliableEvent and notify listeners
                generateEvent((Message) message_buffer.elementAt(pos));

                //forward the received message with the updated path [added ourselves at the end of the path] to the listening children
                //we always forward messages with the right sequence, that may be a bit slower but it
                //prevents a NACK flood after 3-or more levels of the multicast tree and several retransmissions because of
                //the big mees by the dissordered messages
                if (isParent) {
                    forward_message_to_children((Message) message_buffer.elementAt(pos));
                }

                if (LOG.isEnabledFor(Level.DEBUG)) {
                    LOG.debug("Generated an Event and message dorwarded to children");
                }
                //            System.err.println("-- Forwarding to children is done");
                this.desired_seq_num++;
                //now the message with the desired_seq_num is the next one in seq_num asceding order
                pos = positionOfDesired();
            }
        }
    }


    /**
     *  Description of the Method
     *
     * @param  msg  Description of the Parameter
     */
    private void processClosureMessage(Message msg, boolean local) {
        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Start processing the closure message");
        }
        boolean do_close = false;

        String ch_id = ReliableMessageFactory.getChannelIdElement(msg);
        if (ch_id.compareTo("unknown") != 0) {
            do_close = ((ch_id.compareTo(this.channelID)) == 0) ? true : false;
        }

        // *** maybe we should check also the sender for higher security level

        if (do_close) {
            try {
                terminate_channel(msg, local);
            }
            catch (RMSException ex) {ex.printStackTrace();}
        }
    }


    /**
     *  Description of the Method
     *
     * @param  msg  Description of the Parameter
     */
    private void processRetransmissionMessage(Message msg) {
        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Start processing the retransmission message");
        }
        String destination = ReliableMessageFactory.getDestinationElement(msg);
        int retr_seq = Integer.parseInt(ReliableMessageFactory.getMsgSeqNumElement(msg));
        if ((destination.compareTo(peerID.toString()) == 0)) {
            //no problem to add it even if in the meanwhile it has received
            store_message(msg, retr_seq);
            //no problem with message type that is retransmission, the generated event will have simply MULTICAST path = "unknown"
        } else {
            //we are not the destination
            if (isParent) {
                //push it to our children if we have any
                forward_message_to_children(msg);
            }
        }
    }


    /**
     *  Description of the Method
     *
     * @param  msg  Description of the Parameter
     */
    private void processNACKMessage(Message msg) {
        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Start processing the NACK message");
        }
        String nack_seq = ReliableMessageFactory.getMsgSeqNumElement(msg);
        int nack_sequence = Integer.parseInt(nack_seq);
        int keeper = this.desired_seq_num;
        this.desired_seq_num = nack_sequence;
        int result = positionOfDesired();
        this.desired_seq_num = keeper;
        if (result == -1) {
            //send the NACK to our own parent , maybe he has the message that we havent to retransmit
            if((!isRoot) && (!reconnecting) && (parent_BiDi != null) && (parent_BiDi.isBound())) {
                try {
                    parent_BiDi.sendMessage(msg);
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        else {
            String dt = ReliableMessageFactory.getDataElement( (Message) message_buffer.elementAt(result));
            String destination = ReliableMessageFactory.getSenderIdElement(msg);
            //the initial sender of the NACK is our destination of the retransmission message
            Message retr = ReliableMessageFactory.createRetransmissionMessage(peerName, peerID.toString(), channelID, nack_sequence, dt, destination);
            if (isParent) {
                forward_message_to_children(retr);
            }
        }
    }



    private void processHBitMessage(Message msg)
    {
        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Start processing the HeartBeat message");
        }

        String sender = null;
        sender = ReliableMessageFactory.getSenderIdElement(msg);

        if ((provider != null) && ((provider.getId().compareTo(sender.trim())) == 0)) {
            parentDeadCycles = 0;
            System.err.println("HeartBit Received FROM PARENT!!!");
        }
        else
            chld_manager.reset_Hbit(sender);
    }


    private void processTreeLevelMessage(Message msg)
    {
        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Start processing the Tree Level Update message");
        }

        int parent_level = -1;
        String sender = null;
        sender = ReliableMessageFactory.getSenderIdElement(msg);

        if ( (provider != null) && ( (provider.getId().compareTo(sender.trim())) == 0)) { //check the sender of the message for authenticity

            parent_level = ReliableMessageFactory.getTreeLevelElement(msg);
            tree_level = parent_level + 1;
            Message fw_levelmsg = ReliableMessageFactory.createTreeLevelMessage(peerName, peerID.toString(), channelID, tree_level);
            if (isParent)
                forward_message_to_children(fw_levelmsg);

            System.err.println("Tree level update message received and forwarded to children.");
        }
    }



   private void processDisconnectMessage(Message msg)
   {
       if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Start processing the Disconnect message");
        }

        boolean do_disconnect = false;

        String ch_id = ReliableMessageFactory.getChannelIdElement(msg);
        if (ch_id.compareTo("unknown") != 0) {
            do_disconnect = ((ch_id.compareTo(this.channelID)) == 0) ? true : false;
        }


       String sender = ReliableMessageFactory.getSenderIdElement(msg);
       if(sender == null)
       {
           if (LOG.isEnabledFor(Level.DEBUG)) {
               LOG.debug("Unknown sender in processDisconnectMessage, processing aborted.");
           }
           System.err.println("Unknown sender in processDisconnectMessage, processing aborted.");
           return;
       }

       boolean from_parent = ((provider != null) && ( (provider.getId().compareTo(sender.trim())) == 0));

       if(do_disconnect)
       {
           if (from_parent) {
               start_reconnect_mechanism();
           }
           else {//reveived by the child
               boolean was_full = chld_manager.full();

               chld_manager.detach_child(msg, sender);

               if (chld_manager.count_children() == 0) //if after the removal of the dead children there are no more left
                   isParent = false;

               if (was_full) {
                   synchronized (full_of_chld_Lock) {
                       full_of_chld_Lock.notify();
                   }
               }
           }
       }
   }


   private void processDeliver2AllMessage(Message msg)
   {
       if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Start processing the Deliver to All message");
        }

        //It is a bit funny, but you never know...maybe the parent is considered dead but is not (just BIG delay in HB), if the reconnecting process
        //has begun then sooner or later the existing parent_BiDi will close, and it wouldn't be safe to happen while processing the message
        if(reconnecting)
            return;

        if(isRoot)
        {
            String theData = ReliableMessageFactory.getDataElement(msg);
            try {
                send(theData);
            }
            catch (RMSException ex1) {
                ex1.printStackTrace();
            }
        }
        else
        {
            if((!reconnecting) && (parent_BiDi != null) && (parent_BiDi.isBound())) {
                try {
                    parent_BiDi.sendMessage(msg);
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

        }

   }


    /**
     *  Description of the Method
     *
     * @return    Description of the Return Value
     */
    private int positionOfDesired() {
        int position = -1;

        //first received message's seq_num is the starting point for everyone
        //no late joins is sypported
        if (is_first_message) {
            is_first_message = false;
            return 0;
            //the first element of the message_buffer
        }
        Message temp;
        int i = 0;
        int curr_seq;
        do {
            temp = (Message) message_buffer.elementAt(i);
            curr_seq = Integer.parseInt(ReliableMessageFactory.getMsgSeqNumElement(temp).toString().trim());
            i++;
        } while ((curr_seq != this.desired_seq_num) && (i < message_buffer.size()));
        if (curr_seq == this.desired_seq_num) {
            position = i - 1;
        }
        return position;
    }


    /**
     *  Description of the Method
     *
     * @param  msg          Description of the Parameter
     * @param  new_seq_num  Description of the Parameter
     */
    private void store_message(Message msg, int new_seq_num) {
        int max_size = (isRoot) ? ROOTBUFFSIZE : BUFFSIZE;
        Message tmp_msg = null;
        int curr_seq_num = 0;
        int pos = -1;
        boolean already_exists = false;

        for (int i = 0; (i < message_buffer.size()) && (!already_exists); i++) {
            tmp_msg = (Message) message_buffer.elementAt(i);
            curr_seq_num = Integer.parseInt(ReliableMessageFactory.getMsgSeqNumElement(tmp_msg));
            if (curr_seq_num == new_seq_num) {
                already_exists = true;
            } else {
                //insert Message so that the elements of the vector are sorted by Message seq number
                if (new_seq_num < curr_seq_num) {
                    pos = i;
                    i = Integer.MAX_VALUE - 1;
                    //we found the right place, break the for statement
                }
            }
        }
        //for

        if (!already_exists) {
            if ((message_buffer.size() >= max_size)) {
                message_buffer.remove(0);
                //delete the message with the oldest seq_num if there is no place for the new message
                pos = (pos > 0) ? pos - 1 : pos;
            }
            if (pos == -1) {
                message_buffer.add(msg);
            }
            //add to the end , is the biggest seq_num
            else {
                message_buffer.insertElementAt(msg, pos);
            }
        }
    }


    /**
     *  Description of the Method
     *
     * @param  msg  Description of the Parameter
     */
    private void forward_message_to_children(Message msg) {
        try {
            chld_manager.send2children(msg, -1);
        }
        catch (RMSException ex) {
            ex.printStackTrace();
        }
    }


    /**
     *  Description of the Method
     *
     * @param  data              Description of the Parameter
     * @exception  RMSException  Description of the Exception
     */
    public void send(String data) throws RMSException {
        if(reconnecting)
            throw new RMSException("Channel is reconnecting, it is unable to perform your request for sending data.");

        if (isRoot) {
            Message msg = null;
            msg = ReliableMessageFactory.createBulkDataMessage(peerName, peerID.toString(), channelID, messageSeq++, data);
            message_buffer.add(msg);
            if(isParent)
                forward_message_to_children(msg);
            //System.err.println("Message sent !!!");
            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("Root peer sent a message");
            }
            generateEvent(msg);
        }
        else
        {
            //throw new RMSException("Only a root of a multicast tree can send");
            Message msg = null;
            msg = ReliableMessageFactory.createDeliverr2AllMessage(peerName, peerID.toString(), channelID, localMessageSeq++, data);
            local_message_buffer.add(msg);
            if((!isRoot) && (!reconnecting) && (parent_BiDi != null) && (parent_BiDi.isBound())) {
                try {
                    parent_BiDi.sendMessage(msg);
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }


    /**
     *  Adds a feature to the RMSEventListener attribute of the JxtaReliableMcastChannel object
     *
     * @param  listener  The feature to be added to the RMSEventListener attribute
     */
    public void addRMSEventListener(JxtaReliableEventListener listener) {

        // Add the listener to our collection, unless we already have it.
        if (!RMSListeners.contains(listener)) {
            RMSListeners.addElement(listener);
        }
    }


    /**
     *  Description of the Method
     *
     * @param  listener  Description of the Parameter
     */
    public void removeEventListener(JxtaReliableEventListener listener) {
        RMSListeners.removeElement(listener);
    }


    /**
     *  Description of the Method
     *
     * @param  e  Description of the Parameter
     */
    private void sendJxtaCastEvent(JxtaReliableEvent e) {

        JxtaReliableEventListener listener = null;

        Enumeration elements = RMSListeners.elements();
        while (elements.hasMoreElements()) {
            listener = (JxtaReliableEventListener) elements.nextElement();
            listener.processRMSEvent(e);
        }
    }


    /**
     *  Description of the Method
     *
     * @param  msg  Description of the Parameter
     */
    private void generateEvent(Message msg) {
        // Notify listeners of file progress.
        String seq = ReliableMessageFactory.getMsgSeqNumElement(msg);
        String path = ReliableMessageFactory.getMcastPathElement(msg);
        String s_id = ReliableMessageFactory.getSenderIdElement(msg);
        String s_nm = ReliableMessageFactory.getSenderNameElement(msg);
        String dt = ReliableMessageFactory.getDataElement(msg);

        MessageElement msgElement = msg.getMessageElement(ReliableMessageFactory.NAMESPACE, ReliableMessageFactory.MESSAGETYPE);
        String str_type = msgElement.toString().trim();
        if ((str_type.compareTo(ReliableMessageFactory.MSG_CLOSE_CHANNEL)) == 0) {
            JxtaReliableEvent e = new JxtaReliableEvent();
            e.MessageType = ReliableMessageFactory.MSG_CLOSE_CHANNEL;
            e.Data = "";
            sendJxtaCastEvent(e);
        } else
                if ( (str_type.compareTo(ReliableMessageFactory.MSG_BULK) == 0) || (str_type.compareTo(ReliableMessageFactory.MSG_RETR) == 0)) {
            JxtaReliableEvent e = new JxtaReliableEvent();
            e.action = JxtaReliableEvent.RECEIVE;
            e.sender = s_nm;
            e.senderId = s_id;
            e.MessageType = ReliableMessageFactory.MSG_BULK;
            //only for DATA + closure we generate events , the reliability control messages do not concern the app
            e.channelID = channelID;
            e.MsgSeqNum = seq;
            e.McastPath = path;
            e.Data = dt;
            sendJxtaCastEvent(e);
        }
    }


    protected boolean is_full()
    {
        boolean is_full = false;
        if(chld_manager != null)
            is_full = chld_manager.full();
        return is_full;
    }


    /**
     *  Description of the Method
     *
     * @param  sender_name       Description of the Parameter
     * @param  sender_id         Description of the Parameter
     * @param  provider_pipe_id  Description of the Parameter
     * @param  chann  Description of the Parameter
     */
    protected void add2cachedProviders(String sender_name, String sender_id, String provider_pipe_id, String chann, int level) {
        ChannelProvider prv = new ChannelProvider(sender_name, sender_id, provider_pipe_id, chann,level);
        boolean provider_exists = false;
        ChannelProvider temp = null;
        for(int i=0; (i<cachedProviders.size()) && (!provider_exists); i++)
        {
            temp = (ChannelProvider)cachedProviders.elementAt(i);
            if ( (temp.getId().compareTo(prv.getId()) == 0)         &&
                 (temp.getName().compareTo(prv.getName()) == 0)     &&
                 (temp.getPipeId().compareTo(prv.getPipeId()) == 0) &&
                 (temp.getChannel().compareTo(prv.getChannel()) == 0))
            {
                provider_exists = true;
            }
        }
        if (!provider_exists)
        {
            cachedProviders.add(prv);
        }
    }


    protected int countCachedProviders()
    {
        int cnt = 0;
        if(cachedProviders != null)
            cnt = cachedProviders.size();
        return cnt;
    }


    /**
     * select_provider
     *
     * @return ChannelProvider
     */
    protected ChannelProvider select_provider() {
        int selected = -1;
        int min_level = Integer.MAX_VALUE;
        ChannelProvider temp = null;
        for(int i=0; i<cachedProviders.size(); i++)
        {
            temp = (ChannelProvider) cachedProviders.elementAt(i);
            if((temp.getLevel() < min_level) && (temp.getLevel() >= 0))
            {
                selected = i;
                min_level = temp.getLevel();
            }
        }
        if(selected == -1)
            temp = null;
        else
            temp = (ChannelProvider) cachedProviders.remove(selected);
        return temp;
    }


    /*
     *  public void resetChannel()
     *  {
     *  /reset the seq_num --- start new data transmission
     *  }
     *  private void update_children_status() {
     *  / check all the bidi pipes of the children and maybe there are some dead connections
     *  / and so there are new slots for new children
     *  }
     */
    /**
     *  Description of the Method
     */
    public void closeChannel() throws RMSException {
        if(isRoot)
        {
            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("Closing the channel");
            }
            Message msg = ReliableMessageFactory.createChannelClosureMessage(this.channelID);
            processClosureMessage(msg, true);
        }
        else
            throw new RMSException("Only the root of the channel " + channelID + " has the rights to close it.");
    }

    public void leaveChannel() throws RMSException
    {
        if(isRoot)
            throw new RMSException("leave_channel(): The root node canno leave the channel....please wait for the next version that supports leader-election.");

        terminate_channel(null, true);
    }


    protected synchronized void terminate_channel(Message msg, boolean isLocal)throws RMSException
    {
        if ( (!channel_is_Active) || (!channel_is_ready))
            throw new RMSException("terminate_channel(): The channel has not been joined.");

        Message theMsg = msg;

        //determine whether the function has been called by processClosureMessage()  or by the leave_channel()
        boolean doClose; // false => leave_channel and true => close_channel
        if(theMsg != null)
            doClose = true;//called by processClosureMessage()
        else
            doClose = false;//called by the leave_channel()


        boolean was_reconnecting = reconnecting;

        //create the appropriate message in case that we had a null parameter ( called by leave_channel() )
        if(!doClose)
            theMsg = ReliableMessageFactory.createDisconnectMessage(peerName, peerID.toString(), channelID);


        //if reconnecting mechanism is enabled then stop it
        if (reconnecting) {
            reconnecting = false;
            recnn_thread = null;
            theRMS.stop_periodical_requests();
        }


        //stop our heart-beat process
        HB_timer.cancel();


        //stop accepting new children and kill the thread of the channel that is responsible for the establishment of the children-connections
        try {
            MyServerPipe.close();
        }
        catch (IOException ex3) {
            ex3.printStackTrace();
        }
        channel_is_ready = false;
        synchronized(full_of_chld_Lock){
            full_of_chld_Lock.notify();
        }
        chann_thread = null;


        if(!doClose)//only if the the terminate_chanel() has been called by leave_channel()
        {
            //inform the Parent
            if ( (!isRoot) && (!was_reconnecting) && (parent_BiDi != null) && (parent_BiDi.isBound())) {
                try {
                    parent_BiDi.sendMessage(theMsg);
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        //Inform the children about disconnection/closure (they will start their own reconnct procedure)
        if (isParent)
            forward_message_to_children(theMsg);


        //wait some time in order the closure message to reach the children before closing the connections
        synchronized (leave_lock) {
            try {
                leave_lock.wait(chld_manager.CLLD_CLOSURE_TIMEOUT);
            }
            catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }


        //close all connections
        chld_manager.detach_All();
        if ((!isRoot) && (!was_reconnecting)) {
            try {
                parent_BiDi.close();
            }
            catch (IOException ex2) {
                ex2.printStackTrace();
            }
        }


        //finalize some important fields
        isParent = false;
        message_buffer.clear();
        local_message_buffer.clear();
        provider = null;
        HB_timer = null;
        HB_task = null;
        channel_is_Active = false;
        theRMS.clear_channel(channelID);
        if((doClose) && (!isLocal))
            generateEvent(theMsg);
        RMSListeners.clear();
    }


    /**
     *  Gets the ready attribute of the JxtaReliableMcastChannel object
     *
     * @return    The ready value
     */
    public boolean isReady() {
        return channel_is_ready;
    }

    public boolean isActive() {
        return channel_is_Active;
    }



    /**
     *  Gets the provider attribute of the JxtaReliableMcastChannel object
     *
     * @return    The provider value
     */
    protected boolean isProvider() {
        return isProvider;
    }


    /**
     *  Gets the channelId attribute of the JxtaReliableMcastChannel object
     *
     * @return    The channelId value
     */
    public String getChannelId() {
        return channelID;
    }


    /**
     *  Gets the numberOfProviders attribute of the JxtaReliableMcastChannel object
     *
     * @return    The numberOfProviders value
     */
    public int getNumberOfProviders() {
        return cachedProviders.size();
    }


    /**
     *  Description of the Method
     *
     * @return    Description of the Return Value
     */
    public String get_ServerPipe_ID() {
        return pipeAdv.getPipeID().toString();
    }


    public int get_tree_level()
    {
        return tree_level;
    }

    public boolean isReconnecting()
    {
        return reconnecting;
    }

    /**
     *  Gets the isRoot attribute of the JxtaReliableMcastChannel object
     *
     * @return    The island value
     */
    public boolean getisRoot() {
        return isRoot;
    }


    public JxtaReliableMcastService getRMS()
    {
        return theRMS;
    }


    /**
     *  Sets the provider attribute of the JxtaReliableMcastChannel object
     *
     * @param  isProvd  The new provider value
     */
    protected void setProvider(boolean isProvd) {
        isProvider = isProvd;
    }


    /**
     *  Sets the root attribute of the JxtaReliableMcastChannel object
     *
     * @param  isRt  The new root value
     */
    protected void setRoot() {
        isRoot = true;
        provider = null;
        tree_level = 0;
        setProvider(true);
    }

}

