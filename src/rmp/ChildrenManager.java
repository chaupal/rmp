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
 *  $Id: ChildrenManager.java,v 1.1 2006/09/07 22:12:25 dimosp Exp $
 */




package rmp;

import java.util.*;

import net.jxta.util.*;
import net.jxta.protocol.PeerAdvertisement;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import net.jxta.pipe.PipeMsgListener;
import net.jxta.endpoint.Message;
import net.jxta.util.JxtaBiDiPipe;
import java.io.IOException;
import net.jxta.peer.PeerID;

public class ChildrenManager {
    private Vector chld_BiDiPipes;
    //vector with the bidipipes of all the connection with our children if we are a Provider
    private Vector chld_advs;
    //vector with children ID or names
    private Vector chld_PIDs;
    //holding the state of the children, "0"->have received hbit, "n"->have not received hbit for n cycles
    private Vector chld_hbits;

    private int total_children;

    private final static int CHLD_THRESHOLD = 20;
    private final static int CHLD_MAX_DEAD_CYCLES = 5;
    protected final static int CHLD_FULL_TIMEOUT = 5000;
    protected final static int CLLD_CLOSURE_TIMEOUT = 5000;

    private final static Logger LOG = Logger.getLogger(JxtaReliableMcastChannel.class.getName());


    public ChildrenManager() {
        chld_BiDiPipes = new Vector();
        chld_advs = new Vector();
        chld_PIDs = new Vector();
        chld_hbits = new Vector();
        total_children = 0;
    }


    /**
     * store_child
     *
     * @param bdp JxtaBiDiPipe
     */
    protected void store_child(JxtaBiDiPipe bdp, JxtaReliableMcastChannel listener)
    {
        PeerAdvertisement peerAd = null;
        PeerID pid = null;

        synchronized(chld_BiDiPipes)
       {
           bdp.setMessageListener(listener);
           chld_BiDiPipes.add(bdp);
           peerAd = bdp.getRemotePeerAdvertisement();
           chld_advs.add(peerAd);
           pid = peerAd.getPeerID();
           chld_PIDs.add(pid);
           chld_hbits.add(new Integer(0));
           total_children++;

           if (LOG.isEnabledFor(Level.INFO)) {
               LOG.info("Peer :\"" + peerAd.getName() + "\" connected to us");
           }
           System.err.println("Peer :\"" + peerAd.getName() + "\" connected to us [we are a parent]");
       }
    }


    protected void detach_child(Message disconn_msg, String id)
    {
        PeerID pid = null;
        String tmpid = null;

        synchronized (chld_BiDiPipes) { // this function is called by the thread of te HB_timer of the channel,
                                        //but the chld_hbits may be accessed by the reset_Hbit() (PipeMsgListener thread of the channel) at the same time as well
            for (int i = 0; i < chld_BiDiPipes.size(); i++) {

                pid = (PeerID) chld_PIDs.elementAt(i);
                tmpid = pid.toString();
                if ((id.compareTo(tmpid)) == 0)
                    try {
                        detach_child(disconn_msg, i);
                    }
                    catch (IOException ex1) {
                        ex1.printStackTrace();
                    }
                    catch (RMSException ex2) {
                        ex2.printStackTrace();
                    }
            }
        }

    }


    protected void detach_child(Message msg, int index)throws RMSException, IOException
    {
        synchronized(chld_BiDiPipes)
       {
           if (index >= total_children)
               throw new RMSException("Invalid index for child connection. Detaching aborted.");

           JxtaBiDiPipe thepipe = null;
           thepipe = (JxtaBiDiPipe) chld_BiDiPipes.remove(index);
           chld_advs.remove(index);
           chld_PIDs.remove(index);
           chld_hbits.remove(index);

           total_children--;

           if ( (thepipe != null) && (thepipe.isBound())) //in case that the connection is not dead, save time for the child and force it to reconnect elsewhere instead of waiting for the parent-Heartbeat timeout to occur
               thepipe.sendMessage(msg);

           thepipe.close();
           System.err.println("!!!!! CAUTION CHILD HAS BEEN DETACHED !!!!!");
       }
    }


    protected void detach_All()
    {
        JxtaBiDiPipe bidi = null;

        synchronized(chld_BiDiPipes)
       {

           for (int i = 0; i < total_children; i++) {
               bidi = (JxtaBiDiPipe) chld_BiDiPipes.remove(0);
               chld_advs.remove(0);
               chld_PIDs.remove(0);
               chld_hbits.remove(0);

               try {
                   bidi.close();
               }
               catch (IOException ex) {
                   ex.printStackTrace();
               }
           }
           total_children = 0;
           //just in case...
           chld_BiDiPipes.clear();
           chld_advs.clear();
           chld_PIDs.clear();
           chld_hbits.clear();
       }
    }


    protected void send2children(Message msg, int index)throws RMSException
   {
       JxtaBiDiPipe bidi = null;
       boolean send_2_all = true;

       synchronized(chld_BiDiPipes)
       {
           if (index >= total_children)
               throw new RMSException("Invalid index for child connection. Detaching aborted.");

           if (index < 0) { //send to all the attached children
               for (int i = 0; i < chld_BiDiPipes.size(); i++) {
                   bidi = (JxtaBiDiPipe) chld_BiDiPipes.elementAt(i);
                   if ( (bidi != null) && (bidi.isBound()))
                       try {
                           bidi.sendMessage(msg);
                       }
                       catch (IOException ex1) {
                           ex1.printStackTrace();
                       }
               } //for
           } //send only to the index-th child
           else {
               bidi = (JxtaBiDiPipe) chld_BiDiPipes.elementAt(index);
               if ( (bidi != null) && (bidi.isBound()))
                   try {
                       bidi.sendMessage(msg);
                   }
                   catch (IOException ex2) {
                       ex2.printStackTrace();
                   }
           }
       }
   }


   protected void update_hbits()
   {
       Integer tmp = null;
       int k = 0;

       synchronized (chld_BiDiPipes) { // this function is called by the thread of te HB_timer of the channel,
                                   //but the chld_hbits may be accessed by the reset_Hbit() (PipeMsgListener thread of the channel) at the same time as well
           for (int i = 0; i < chld_BiDiPipes.size(); i++) {
               tmp = (Integer) chld_hbits.elementAt(i);
               k = tmp.intValue();
               k++;
               chld_hbits.set(i, new Integer(k));
           }
       }
   }


   protected int cutDeadChildren(Message disconn_msg)
   {
       Integer tmp = null;
       int k = 0;
       synchronized (chld_BiDiPipes) { // this function is called by the thread of te HB_timer of the channel,
                                   //but the chld_hbits may be accessed by the reset_Hbit() (PipeMsgListener thread of the channel) at the same time as well
           for (int i = 0; i < chld_BiDiPipes.size(); i++) {

               tmp = (Integer) chld_hbits.elementAt(i);
               k = tmp.intValue();
               if (k > CHLD_MAX_DEAD_CYCLES)
                   try {
                       detach_child(disconn_msg, i);
                   }
                   catch (IOException ex1) {
                       ex1.printStackTrace();
                   }
                   catch (RMSException ex2) {
                       ex2.printStackTrace();
                   }
           }
       }
       return total_children;
   }


   protected void reset_Hbit(String theID)
   {
       PeerID tmpid = null;
       synchronized (chld_BiDiPipes) { // this function is called by the thread of te PipeMsgListener thread of the channel,
                                   //but the chld_hbits may be accessed by the thread of HB_timer ( update_hbits() ) at the same time as well
           for (int i = 0; i < chld_BiDiPipes.size(); i++) {
               tmpid = (PeerID) chld_PIDs.elementAt(i);
               if ( (tmpid != null) && ( (tmpid.toString().compareTo(theID)) ==0 ) )
                   chld_hbits.set(i, new Integer(0));
           }
       }
       System.err.println("HeartBit Reveived FROM CHILD!!!");
   }


   /**
    * totalChld
    *
    * @return boolean
    */
   protected int count_children() {
       int ch_count = 0;
       if(chld_BiDiPipes != null)
           ch_count = chld_BiDiPipes.size();
       return ch_count;
    }


    /**
     *  Description of the Method
     *
     * @return    Description of the Return Value
     */
    protected boolean full() {
        int tot = 0;
        if(chld_BiDiPipes != null)
            tot = chld_BiDiPipes.size();
        return ( (tot >= CHLD_THRESHOLD) ? true : false);
    }


}
